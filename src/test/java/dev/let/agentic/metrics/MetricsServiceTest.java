package dev.let.agentic.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.let.agentic.agent.Agent;
import dev.let.agentic.agent.SimulatedAgent;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.engine.Orchestrator;
import dev.let.agentic.engine.PolicyGuard;
import dev.let.agentic.graph.StageGraph;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

    private static final String CLEAR = "Build a URL shortener service with create, redirect and click analytics";

    @Test
    void computesReliabilityMetricsFromTheAuditLog() {
        SimulatedAgent sim = new SimulatedAgent();
        AtomicInteger calls = new AtomicInteger();
        Agent flaky = ctx -> {
            if (ctx.stage() == StageId.ARCHITECTURE && calls.incrementAndGet() == 1) {
                throw new IllegalStateException("model timeout");
            }
            return sim.execute(ctx);
        };
        Orchestrator orchestrator = new Orchestrator(StageGraph.standard(), flaky, sim, new PolicyGuard());

        var first = orchestrator.start(CLEAR, RunType.GREENFIELD);
        orchestrator.approve(first.getId(), StageId.DESIGN_APPROVAL, "leticia", "ok");
        orchestrator.approve(first.getId(), StageId.RELEASE_APPROVAL, "leticia", "ok");
        orchestrator.start(CLEAR, RunType.GREENFIELD);

        var metrics = new MetricsService(orchestrator).snapshot();

        assertThat(metrics.totalRuns()).isEqualTo(2);
        assertThat(metrics.done()).isEqualTo(1);
        assertThat(metrics.inProgress()).isEqualTo(1);
        assertThat(metrics.successRate()).isEqualTo(1.0);
        assertThat(metrics.stageRetries()).isEqualTo(1);
        assertThat(metrics.rollbacks()).isZero();
        assertThat(metrics.approvals()).isEqualTo(2);
        assertThat(metrics.mttrMillis()).isNotNull();
        assertThat(metrics.avgEndToEndMillis()).isNotNull();
    }

    @Test
    void emptySystemHasNoLatencyData() {
        Orchestrator orchestrator = new Orchestrator(
                StageGraph.standard(), new SimulatedAgent(), new SimulatedAgent(), new PolicyGuard());
        var metrics = new MetricsService(orchestrator).snapshot();

        assertThat(metrics.totalRuns()).isZero();
        assertThat(metrics.mttrMillis()).isNull();
        assertThat(metrics.avgEndToEndMillis()).isNull();
    }
}
