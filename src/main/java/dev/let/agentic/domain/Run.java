package dev.let.agentic.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** One end-to-end execution of a requirement through the lifecycle graph. */
public class Run {
    private final UUID id = UUID.randomUUID();
    private final String requirement;
    private final RunType type;
    private volatile RunStatus status = RunStatus.CREATED;
    private volatile boolean stopRequested;
    private volatile boolean ambiguous;
    private final Map<StageId, StageExecution> stages;
    private final List<Artifact> artifacts = new CopyOnWriteArrayList<>();
    private final List<Decision> decisions = new CopyOnWriteArrayList<>();
    private final List<AuditEvent> auditLog = new CopyOnWriteArrayList<>();

    public Run(String requirement, RunType type, Collection<StageDefinition> definitions) {
        this.requirement = requirement;
        this.type = type;
        Map<StageId, StageExecution> map = new EnumMap<>(StageId.class);
        for (StageDefinition d : definitions) {
            map.put(d.id(), new StageExecution(d.id()));
        }
        this.stages = Collections.unmodifiableMap(map);
    }

    public void audit(AuditType type, StageId stage, String actor, String detail) {
        auditLog.add(new AuditEvent(Instant.now(), id, stage, type, actor, detail));
    }

    public void addArtifact(Artifact artifact) { artifacts.add(artifact); }
    public void addDecision(Decision decision) { decisions.add(decision); }

    public UUID getId() { return id; }
    public String getRequirement() { return requirement; }
    public RunType getType() { return type; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public boolean isStopRequested() { return stopRequested; }
    public void requestStop() { this.stopRequested = true; }
    public boolean isAmbiguous() { return ambiguous; }
    public void setAmbiguous(boolean ambiguous) { this.ambiguous = ambiguous; }
    public Map<StageId, StageExecution> getStages() { return stages; }
    public List<Artifact> getArtifacts() { return Collections.unmodifiableList(artifacts); }
    public List<Decision> getDecisions() { return Collections.unmodifiableList(decisions); }
    public List<AuditEvent> getAuditLog() { return Collections.unmodifiableList(auditLog); }
}
