package dev.let.agentic.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Decision lineage: who decided what, why, and based on which artifacts. */
public record Decision(
        UUID id,
        StageId stage,
        String actor,
        String choice,
        String rationale,
        List<UUID> basedOnArtifacts,
        Instant at) {
}
