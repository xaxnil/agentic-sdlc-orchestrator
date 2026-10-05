package dev.let.agentic.graph;

import static dev.let.agentic.domain.StageCondition.ALWAYS;
import static dev.let.agentic.domain.StageId.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.let.agentic.domain.StageDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageGraphTest {

    private final StageGraph graph = StageGraph.standard();

    @Test
    void standardGraphHasAllStagesAndIsValid() {
        assertThat(graph.all()).hasSize(14);
        List<?> order = graph.topologicalOrder();
        assertThat(order.get(0)).isEqualTo(REQUIREMENT);
        assertThat(order.get(order.size() - 1)).isEqualTo(RELEASE);
    }

    @Test
    void designBranchesRunInParallelAfterPlanning() {
        assertThat(graph.get(ARCHITECTURE).dependsOn()).containsExactly(PLANNING);
        assertThat(graph.get(SECURITY).dependsOn()).containsExactly(PLANNING);
        assertThat(graph.get(DATA_API).dependsOn()).containsExactly(PLANNING);
        assertThat(graph.get(DESIGN_APPROVAL).dependsOn())
                .containsExactlyInAnyOrder(ARCHITECTURE, SECURITY, DATA_API);
    }

    @Test
    void highImpactStagesRequireHumanApproval() {
        assertThat(graph.get(DESIGN_APPROVAL).requiresApproval()).isTrue();
        assertThat(graph.get(RELEASE_APPROVAL).requiresApproval()).isTrue();
        assertThat(graph.get(IMPLEMENTATION).requiresApproval()).isFalse();
    }

    @Test
    void downstreamOfPlanningInvalidatesEverythingAfterIt() {
        assertThat(graph.downstreamOf(PLANNING))
                .contains(ARCHITECTURE, DESIGN_APPROVAL, IMPLEMENTATION, TESTS, DOCS, VALIDATION, RELEASE)
                .doesNotContain(REQUIREMENT, CLARIFY, IMPACT);
    }

    @Test
    void cycleIsRejected() {
        StageDefinition a = StageDefinition.of(REQUIREMENT, ALWAYS, false, 0, CLARIFY);
        StageDefinition b = StageDefinition.of(CLARIFY, ALWAYS, false, 0, REQUIREMENT);
        assertThatThrownBy(() -> new StageGraph(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle");
    }

    @Test
    void unknownDependencyIsRejected() {
        StageDefinition lonely = StageDefinition.of(PLANNING, ALWAYS, false, 0, REQUIREMENT);
        assertThatThrownBy(() -> new StageGraph(List.of(lonely)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown");
    }
}
