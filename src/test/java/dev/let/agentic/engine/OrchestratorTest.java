package dev.let.agentic.engine;

import static dev.let.agentic.domain.AuditType.*;
import static dev.let.agentic.domain.StageId.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.let.agentic.agent.Agent;
import dev.let.agentic.agent.AgentResult;
import dev.let.agentic.agent.SimulatedAgent;
import dev.let.agentic.domain.AuditType;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.RunStatus;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.domain.StageStatus;
import dev.let.agentic.graph.StageGraph;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrchestratorTest {

    private static final String CLEAR = "Build a URL shortener service with create, redirect and click analytics";
    private final SimulatedAgent sim = new SimulatedAgent();

    private Orchestrator orchestrator(Agent primary, Agent fallback) {
        return new Orchestrator(StageGraph.standard(), primary, fallback, new PolicyGuard());
    }

    private StageStatus status(Run run, StageId id) {
        return run.getStages().get(id).getStatus();
    }

    private long count(Run run, AuditType type) {
        return run.getAuditLog().stream().filter(e -> e.type() == type).count();
    }

    @Test
    void greenfieldNeedsTwoHumanApprovals() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);

        assertThat(run.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL);
        assertThat(status(run, DESIGN_APPROVAL)).isEqualTo(StageStatus.WAITING_APPROVAL);
        assertThat(status(run, CLARIFY)).isEqualTo(StageStatus.SKIPPED);
        assertThat(status(run, IMPACT)).isEqualTo(StageStatus.SKIPPED);
        assertThat(status(run, IMPLEMENTATION)).isEqualTo(StageStatus.PENDING);

        o.approve(run.getId(), DESIGN_APPROVAL, "leticia", "looks good");
        assertThat(run.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL);
        assertThat(status(run, RELEASE_APPROVAL)).isEqualTo(StageStatus.WAITING_APPROVAL);
        assertThat(status(run, RELEASE)).isEqualTo(StageStatus.PENDING);

        o.approve(run.getId(), RELEASE_APPROVAL, "leticia", "ship it");
        assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
        assertThat(status(run, RELEASE)).isEqualTo(StageStatus.SUCCEEDED);
    }

    @Test
    void brownfieldRunsImpactAnalysis() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start("Add expiration to short links so they stop working after a date", RunType.BROWNFIELD);

        assertThat(status(run, IMPACT)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(status(run, CLARIFY)).isEqualTo(StageStatus.SKIPPED);
        assertThat(status(run, DESIGN_APPROVAL)).isEqualTo(StageStatus.WAITING_APPROVAL);
    }

    @Test
    void ambiguousRequirementStopsForHumanClarification() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start("Make it more secure", RunType.GREENFIELD);

        assertThat(run.isAmbiguous()).isTrue();
        assertThat(status(run, CLARIFY)).isEqualTo(StageStatus.WAITING_APPROVAL);
        assertThat(status(run, PLANNING)).isEqualTo(StageStatus.PENDING);

        o.approve(run.getId(), CLARIFY, "leticia", "Focus on input validation and rate limiting");
        assertThat(status(run, CLARIFY)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(status(run, DESIGN_APPROVAL)).isEqualTo(StageStatus.WAITING_APPROVAL);
        assertThat(run.getArtifacts()).anyMatch(a -> a.name().equals("approval-note")
                && a.content().contains("rate limiting"));
    }

    @Test
    void failingStageIsRetriedThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        Agent flaky = ctx -> {
            if (ctx.stage() == ARCHITECTURE && calls.incrementAndGet() <= 2) {
                throw new IllegalStateException("model timeout");
            }
            return sim.execute(ctx);
        };
        Run run = orchestrator(flaky, sim).start(CLEAR, RunType.GREENFIELD);

        assertThat(status(run, ARCHITECTURE)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.getStages().get(ARCHITECTURE).getAttempts()).isEqualTo(3);
        assertThat(count(run, STAGE_RETRY)).isEqualTo(2);
        assertThat(count(run, ROLLBACK)).isZero();
    }

    @Test
    void fallbackAgentTakesOverWhenPrimaryKeepsFailing() {
        Agent brokenTests = ctx -> {
            if (ctx.stage() == TESTS) {
                throw new IllegalStateException("model unavailable");
            }
            return sim.execute(ctx);
        };
        Orchestrator o = orchestrator(brokenTests, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);
        o.approve(run.getId(), DESIGN_APPROVAL, "leticia", "ok");

        assertThat(status(run, TESTS)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.getAuditLog()).anyMatch(e -> e.stage() == TESTS
                && e.type() == STAGE_SUCCEEDED && e.actor().equals("fallback-agent"));
    }

    @Test
    void rollsBackToLastApprovedCheckpointWhenEverythingFails() {
        Agent broken = ctx -> {
            if (ctx.stage() == IMPLEMENTATION) {
                throw new IllegalStateException("cannot generate code");
            }
            return sim.execute(ctx);
        };
        Orchestrator o = orchestrator(broken, broken);
        Run run = o.start(CLEAR, RunType.GREENFIELD);
        o.approve(run.getId(), DESIGN_APPROVAL, "leticia", "ok");

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(status(run, IMPLEMENTATION)).isEqualTo(StageStatus.ROLLED_BACK);
        assertThat(status(run, DESIGN_APPROVAL)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(status(run, TESTS)).isEqualTo(StageStatus.PENDING);
        assertThat(count(run, ROLLBACK)).isEqualTo(1);
    }

    @Test
    void policyGuardBlocksSecretsAndFallbackProducesCleanOutput() {
        Agent leaky = ctx -> ctx.stage() == IMPLEMENTATION
                ? AgentResult.of("impl", "config api_key=ABCDEFGH12345678")
                : sim.execute(ctx);
        Orchestrator o = orchestrator(leaky, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);
        o.approve(run.getId(), DESIGN_APPROVAL, "leticia", "ok");

        assertThat(count(run, POLICY_VIOLATION)).isGreaterThanOrEqualTo(1);
        assertThat(status(run, IMPLEMENTATION)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(run.getArtifacts()).noneMatch(a -> a.content().contains("ABCDEFGH12345678"));
    }

    @Test
    void safeStopPreventsNewStagesFromStarting() {
        Agent stopper = ctx -> {
            if (ctx.stage() == PLANNING) {
                ctx.run().requestStop();
            }
            return sim.execute(ctx);
        };
        Run run = orchestrator(stopper, sim).start(CLEAR, RunType.GREENFIELD);

        assertThat(run.getStatus()).isEqualTo(RunStatus.STOPPED);
        assertThat(status(run, PLANNING)).isEqualTo(StageStatus.SUCCEEDED);
        assertThat(status(run, ARCHITECTURE)).isEqualTo(StageStatus.STOPPED);
        assertThat(count(run, SAFE_STOP)).isEqualTo(1);
    }

    @Test
    void rejectingTheDesignReplansAndReRunsDependentStages() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);
        o.reject(run.getId(), DESIGN_APPROVAL, "leticia", "Add rate limiting to the design");

        assertThat(run.getStatus()).isEqualTo(RunStatus.WAITING_APPROVAL);
        assertThat(status(run, DESIGN_APPROVAL)).isEqualTo(StageStatus.WAITING_APPROVAL);
        assertThat(run.getStages().get(PLANNING).getAttempts()).isEqualTo(2);
        assertThat(run.getStages().get(ARCHITECTURE).getAttempts()).isEqualTo(2);
        assertThat(count(run, STAGE_INVALIDATED)).isGreaterThanOrEqualTo(4);
    }

    @Test
    void tooManyRejectionsFailTheRun() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);
        o.reject(run.getId(), DESIGN_APPROVAL, "leticia", "first");
        o.reject(run.getId(), DESIGN_APPROVAL, "leticia", "second");
        o.reject(run.getId(), DESIGN_APPROVAL, "leticia", "third");

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void approvalOutOfOrderIsRejected() {
        Orchestrator o = orchestrator(sim, sim);
        Run run = o.start(CLEAR, RunType.GREENFIELD);

        assertThatThrownBy(() -> o.approve(run.getId(), RELEASE_APPROVAL, "leticia", "skip ahead"))
                .isInstanceOf(IllegalStateException.class);
    }
}
