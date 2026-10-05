package dev.let.agentic.domain;

import java.util.Set;

/** Declarative description of one stage of the lifecycle graph. */
public record StageDefinition(
        StageId id,
        Set<StageId> dependsOn,
        StageCondition condition,
        boolean requiresApproval,
        int maxRetries) {

    public StageDefinition {
        dependsOn = Set.copyOf(dependsOn);
    }

    public static StageDefinition of(StageId id, StageCondition condition,
                                     boolean requiresApproval, int maxRetries,
                                     StageId... deps) {
        return new StageDefinition(id, Set.of(deps), condition, requiresApproval, maxRetries);
    }
}
