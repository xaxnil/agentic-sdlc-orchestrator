package dev.let.agentic.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.let.agentic.agent.SimulatedAgent;
import dev.let.agentic.api.RunViews.RunView;
import dev.let.agentic.domain.AuditType;
import dev.let.agentic.domain.RunStatus;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.engine.Orchestrator;
import dev.let.agentic.engine.PolicyGuard;
import dev.let.agentic.graph.StageGraph;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunControllerTest {

    private static final String CLEAR = "Build a URL shortener service with create, redirect and click analytics";

    private final Orchestrator orchestrator = new Orchestrator(
            StageGraph.standard(), new SimulatedAgent(), new SimulatedAgent(), new PolicyGuard());
    private final RunController controller = new RunController(orchestrator);

    private RunView startRun() {
        return controller.start(new StartRunRequest(CLEAR, RunType.GREENFIELD)).getBody();
    }

    @Test
    void startReturns201AndWaitsForDesignApproval() {
        var response = controller.start(new StartRunRequest(CLEAR, null));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().type()).isEqualTo(RunType.GREENFIELD);
        assertThat(response.getBody().status()).isEqualTo(RunStatus.WAITING_APPROVAL);
    }

    @Test
    void fullFlowThroughTheApi() {
        RunView run = startRun();
        controller.approve(run.id(), StageId.DESIGN_APPROVAL, new DecisionRequest("leticia", "ok"));
        RunView done = controller.approve(run.id(), StageId.RELEASE_APPROVAL, null);

        assertThat(done.status()).isEqualTo(RunStatus.DONE);
        assertThat(controller.audit(run.id())).anyMatch(e -> e.type() == AuditType.APPROVED);
        assertThat(controller.list()).hasSize(1);
    }

    @Test
    void stopMarksTheRunAsStopped() {
        RunView run = startRun();
        assertThat(controller.stop(run.id(), null).status()).isEqualTo(RunStatus.STOPPED);
    }

    @Test
    void unknownRunIsNotFound() {
        assertThatThrownBy(() -> controller.get(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void secretsInApprovalNotesAreRejected() {
        RunView run = startRun();
        assertThatThrownBy(() -> controller.approve(run.id(), StageId.DESIGN_APPROVAL,
                new DecisionRequest("leticia", "password = hunter2hunter2")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
