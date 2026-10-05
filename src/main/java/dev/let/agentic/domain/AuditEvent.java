package dev.let.agentic.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit log entry. */
public record AuditEvent(
        Instant at,
        UUID runId,
        StageId stage,
        AuditType type,
        String actor,
        String detail) {
}
