package dev.let.agentic.api;

import dev.let.agentic.domain.AuditEvent;
import dev.let.agentic.domain.AuditType;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.RunStatus;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.domain.StageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API response shapes. Domain objects are never serialized directly. */
public final class RunViews {

    private RunViews() { }

    public record StageView(StageId stage, StageStatus status, int attempts, String lastError) { }

    public record ArtifactView(StageId stage, String name, int version, String hash, String content) { }

    public record DecisionView(StageId stage, String actor, String choice, String rationale, Instant at) { }

    public record AuditView(Instant at, StageId stage, AuditType type, String actor, String detail) {
        public static AuditView from(AuditEvent e) {
            return new AuditView(e.at(), e.stage(), e.type(), e.actor(), e.detail());
        }
    }

    public record RunSummary(UUID id, String requirement, RunType type, RunStatus status) {
        public static RunSummary from(Run run) {
            return new RunSummary(run.getId(), run.getRequirement(), run.getType(), run.getStatus());
        }
    }

    public record RunView(UUID id, String requirement, RunType type, RunStatus status, boolean ambiguous,
                          List<StageView> stages, List<ArtifactView> artifacts, List<DecisionView> decisions) {

        public static RunView from(Run run) {
            return new RunView(run.getId(), run.getRequirement(), run.getType(), run.getStatus(),
                    run.isAmbiguous(),
                    run.getStages().values().stream()
                            .map(e -> new StageView(e.getStageId(), e.getStatus(), e.getAttempts(), e.getLastError()))
                            .toList(),
                    run.getArtifacts().stream()
                            .map(a -> new ArtifactView(a.producedBy(), a.name(), a.version(), a.hash(), a.content()))
                            .toList(),
                    run.getDecisions().stream()
                            .map(d -> new DecisionView(d.stage(), d.actor(), d.choice(), d.rationale(), d.at()))
                            .toList());
        }
    }
}
