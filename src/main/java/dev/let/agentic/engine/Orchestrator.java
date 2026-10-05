package dev.let.agentic.engine;

import static dev.let.agentic.domain.AuditType.*;
import static dev.let.agentic.domain.StageStatus.*;

import dev.let.agentic.agent.Agent;
import dev.let.agentic.agent.AgentContext;
import dev.let.agentic.agent.AgentResult;
import dev.let.agentic.domain.Artifact;
import dev.let.agentic.domain.Decision;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.RunStatus;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageDefinition;
import dev.let.agentic.domain.StageExecution;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.graph.StageGraph;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Deterministic workflow engine. It is plain code, not an AI agent: it decides what runs,
 * when and under which rules. Agents only do the work inside a stage.
 */
public class Orchestrator implements AutoCloseable {

    private static final int MAX_REJECTIONS = 2;
    /** Where the lifecycle restarts when a human rejects an approval gate. */
    private static final Map<StageId, StageId> REJECT_RESTART = Map.of(
            StageId.CLARIFY, StageId.REQUIREMENT,
            StageId.DESIGN_APPROVAL, StageId.PLANNING,
            StageId.RELEASE_APPROVAL, StageId.IMPLEMENTATION);

    private final StageGraph graph;
    private final Agent primary;
    private final Agent fallback;
    private final PolicyGuard policy;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    public Orchestrator(StageGraph graph, Agent primary, Agent fallback, PolicyGuard policy) {
        this.graph = graph;
        this.primary = primary;
        this.fallback = fallback;
        this.policy = policy;
    }

    // ---------------------------------------------------------------- public API

    public Run start(String requirement, RunType type) {
        Run run = new Run(requirement, type, graph.all());
        runs.put(run.getId(), run);
        run.audit(RUN_STARTED, null, "system", type + ": " + requirement);
        advance(run);
        return run;
    }

    public Optional<Run> find(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    public Collection<Run> allRuns() {
        return Collections.unmodifiableCollection(runs.values());
    }

    public Run approve(UUID runId, StageId stage, String actor, String note) {
        Run run = require(runId);
        synchronized (run) {
            StageExecution ex = requireWaiting(run, stage);
            String text = (note == null || note.isBlank()) ? "approved" : note.trim();
            enforcePolicy(stage, text);
            storeArtifact(run, stage, "approval-note", text);
            run.addDecision(new Decision(UUID.randomUUID(), stage, actor, "APPROVED", text,
                    basis(run, graph.get(stage)), Instant.now()));
            ex.setStatus(SUCCEEDED);
            ex.setFinishedAt(Instant.now());
            run.audit(APPROVED, stage, actor, text);
            advance(run);
        }
        return run;
    }

    public Run reject(UUID runId, StageId stage, String actor, String reason) {
        Run run = require(runId);
        synchronized (run) {
            StageExecution ex = requireWaiting(run, stage);
            String text = (reason == null || reason.isBlank()) ? "rejected without comment" : reason.trim();
            enforcePolicy(stage, text);
            long previous = run.getAuditLog().stream()
                    .filter(e -> e.type() == REJECTED && e.stage() == stage).count();
            run.audit(REJECTED, stage, actor, text);
            run.addDecision(new Decision(UUID.randomUUID(), stage, actor, "REJECTED", text,
                    basis(run, graph.get(stage)), Instant.now()));
            if (previous >= MAX_REJECTIONS) {
                ex.setStatus(FAILED);
                run.setStatus(RunStatus.FAILED);
                run.audit(RUN_FAILED, stage, "engine", "rejected more than " + MAX_REJECTIONS + " times");
                return run;
            }
            storeArtifact(run, stage, "rejection-feedback", text);
            ex.setStatus(INVALIDATED);
            run.audit(STAGE_INVALIDATED, stage, "engine", "rejected by " + actor);
            StageId restart = REJECT_RESTART.get(stage);
            run.getStages().get(restart).setStatus(INVALIDATED);
            run.audit(STAGE_INVALIDATED, restart, "engine", "re-plan after rejection of " + stage);
            advance(run);
        }
        return run;
    }

    /** Safe-stop: nothing new starts. Work already running finishes cleanly. */
    public Run stop(UUID runId, String actor) {
        Run run = require(runId);
        run.requestStop();
        synchronized (run) {
            if (!isTerminal(run)) {
                finalizeStop(run, actor);
            }
        }
        return run;
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    // ---------------------------------------------------------------- main loop

    private void advance(Run run) {
        synchronized (run) {
            if (isTerminal(run)) {
                return;
            }
            run.setStatus(RunStatus.RUNNING);
            while (true) {
                if (run.isStopRequested()) {
                    finalizeStop(run, "engine");
                    return;
                }
                boolean progressed = false;
                List<StageDefinition> batch = new ArrayList<>();
                for (StageDefinition def : graph.all()) {
                    StageExecution ex = run.getStages().get(def.id());
                    if (!isRunnable(ex.getStatus()) || !dependenciesDone(run, def)) {
                        continue;
                    }
                    if (!applies(def, run)) {
                        ex.setStatus(SKIPPED);
                        run.audit(STAGE_SKIPPED, def.id(), "engine", "condition " + def.condition() + " not met");
                        progressed = true;
                    } else if (def.requiresApproval()) {
                        ex.setStatus(WAITING_APPROVAL);
                        run.audit(APPROVAL_REQUESTED, def.id(), "engine", "human approval required");
                    } else {
                        batch.add(def);
                    }
                }
                if (!batch.isEmpty()) {
                    runBatch(run, batch);
                    progressed = true;
                }
                if (run.getStatus() == RunStatus.FAILED) {
                    return;
                }
                if (!progressed) {
                    break;
                }
            }
            settle(run);
        }
    }

    private void runBatch(Run run, List<StageDefinition> batch) {
        List<Callable<String>> tasks = new ArrayList<>();
        for (StageDefinition def : batch) {
            tasks.add(() -> executeStage(run, def));
        }
        try {
            List<Future<String>> futures = executor.invokeAll(tasks);
            // Rollback only after the whole parallel batch finished, to avoid races.
            for (int i = 0; i < batch.size(); i++) {
                String error = futures.get(i).get();
                if (error != null) {
                    rollback(run, batch.get(i), error);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Returns null on success (or stop), or the last error message when every option failed. */
    private String executeStage(Run run, StageDefinition def) {
        StageExecution ex = run.getStages().get(def.id());
        ex.setStatus(RUNNING);
        ex.setStartedAt(Instant.now());
        ex.setFinishedAt(null);
        ex.setLastError(null);
        recordInputHashes(run, def, ex);
        run.audit(STAGE_STARTED, def.id(), "engine", "attempt " + (ex.getAttempts() + 1));

        int maxAttempts = def.maxRetries() + 1;
        String lastError = "unknown error";
        for (int i = 1; i <= maxAttempts; i++) {
            if (run.isStopRequested()) {
                ex.setStatus(STOPPED);
                return null;
            }
            int attempt = ex.incrementAttempts();
            try {
                succeed(run, def, ex, primary, "agent", attempt);
                return null;
            } catch (Exception e) {
                lastError = describe(e);
                ex.setLastError(lastError);
                run.audit(i < maxAttempts ? STAGE_RETRY : STAGE_FAILED, def.id(), "engine",
                        "attempt " + attempt + " failed: " + lastError);
            }
        }
        if (run.isStopRequested()) {
            ex.setStatus(STOPPED);
            return null;
        }
        run.audit(STAGE_RETRY, def.id(), "engine", "switching to fallback agent");
        try {
            succeed(run, def, ex, fallback, "fallback-agent", ex.incrementAttempts());
            return null;
        } catch (Exception e) {
            lastError = describe(e);
            ex.setLastError(lastError);
            run.audit(STAGE_FAILED, def.id(), "engine", "fallback failed: " + lastError);
            return lastError;
        }
    }

    private void succeed(Run run, StageDefinition def, StageExecution ex,
                         Agent agent, String actor, int attempt) throws Exception {
        AgentContext ctx = new AgentContext(run, def.id(), latestByStage(run, def.id()), attempt);
        AgentResult result = agent.execute(ctx);
        if (result == null || result.content() == null) {
            throw new IllegalStateException("agent returned no output");
        }
        policy.check(def.id(), result.content()).ifPresent(violation -> {
            run.audit(POLICY_VIOLATION, def.id(), "policy-guard", violation);
            throw new IllegalStateException(violation);
        });

        Optional<Artifact> previous = latestOf(run, def.id());
        Artifact artifact = storeArtifact(run, def.id(), result.name(), result.content());
        if (def.id() == StageId.REQUIREMENT) {
            run.setAmbiguous(result.ambiguous());
        }
        run.addDecision(new Decision(UUID.randomUUID(), def.id(), actor,
                "produced " + artifact.name() + " v" + artifact.version(),
                "attempt " + attempt, basis(run, def), Instant.now()));
        ex.setStatus(SUCCEEDED);
        ex.setFinishedAt(Instant.now());
        run.audit(STAGE_SUCCEEDED, def.id(), actor,
                artifact.name() + " v" + artifact.version() + " hash " + artifact.hash().substring(0, 12));

        if (previous.isPresent() && !previous.get().hash().equals(artifact.hash())) {
            invalidateDownstream(run, def.id());
        }
    }

    // ---------------------------------------------------------------- control flow helpers

    /** Re-planning: upstream output changed, so everything that depended on it must be redone. */
    private void invalidateDownstream(Run run, StageId changed) {
        for (StageId id : graph.downstreamOf(changed)) {
            StageExecution e = run.getStages().get(id);
            switch (e.getStatus()) {
                case SUCCEEDED, WAITING_APPROVAL, FAILED, ROLLED_BACK -> {
                    e.setStatus(INVALIDATED);
                    run.audit(STAGE_INVALIDATED, id, "engine", "output of " + changed + " changed");
                }
                case SKIPPED -> e.setStatus(PENDING);
                default -> { }
            }
        }
    }

    /** Rollback to the last approved checkpoint. The checkpoint itself stays approved. */
    private void rollback(Run run, StageDefinition failed, String error) {
        StageExecution failedExec = run.getStages().get(failed.id());
        failedExec.setStatus(FAILED);
        failedExec.setFinishedAt(Instant.now());

        Optional<StageId> checkpoint = lastCheckpoint(run, failed.id());
        Set<StageId> scope = new LinkedHashSet<>();
        scope.add(failed.id());
        checkpoint.ifPresent(cp -> scope.addAll(graph.downstreamOf(cp)));

        List<StageId> rolledBack = new ArrayList<>();
        for (StageId id : scope) {
            StageExecution e = run.getStages().get(id);
            switch (e.getStatus()) {
                case SUCCEEDED, FAILED, RUNNING, WAITING_APPROVAL -> {
                    e.setStatus(ROLLED_BACK);
                    rolledBack.add(id);
                }
                default -> { }
            }
        }
        run.audit(ROLLBACK, failed.id(), "engine", "rolled back " + rolledBack + " to checkpoint "
                + checkpoint.map(Enum::name).orElse("start") + " after: " + error);
        run.setStatus(RunStatus.FAILED);
        run.audit(RUN_FAILED, failed.id(), "engine", error);
    }

    private Optional<StageId> lastCheckpoint(Run run, StageId failed) {
        List<StageId> order = new ArrayList<>(graph.topologicalOrder());
        Collections.reverse(order);
        for (StageId id : order) {
            if (graph.get(id).requiresApproval()
                    && run.getStages().get(id).getStatus() == SUCCEEDED
                    && graph.downstreamOf(id).contains(failed)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private void finalizeStop(Run run, String actor) {
        for (StageExecution e : run.getStages().values()) {
            if (!e.getStatus().isDone() && e.getStatus() != FAILED && e.getStatus() != ROLLED_BACK) {
                e.setStatus(STOPPED);
            }
        }
        run.setStatus(RunStatus.STOPPED);
        run.audit(SAFE_STOP, null, actor, "run stopped, no new stage will start");
    }

    private void settle(Run run) {
        boolean allDone = run.getStages().values().stream().allMatch(e -> e.getStatus().isDone());
        boolean waiting = run.getStages().values().stream().anyMatch(e -> e.getStatus() == WAITING_APPROVAL);
        if (allDone) {
            run.setStatus(RunStatus.DONE);
            run.audit(RUN_DONE, null, "engine", "all stages completed");
        } else if (waiting) {
            run.setStatus(RunStatus.WAITING_APPROVAL);
        } else {
            run.setStatus(RunStatus.FAILED);
            run.audit(RUN_FAILED, null, "engine", "run stalled: no runnable stage");
        }
    }

    // ---------------------------------------------------------------- small helpers

    private boolean applies(StageDefinition def, Run run) {
        return switch (def.condition()) {
            case ALWAYS -> true;
            case IF_AMBIGUOUS -> run.isAmbiguous();
            case IF_BROWNFIELD -> run.getType() == RunType.BROWNFIELD;
        };
    }

    private boolean isRunnable(dev.let.agentic.domain.StageStatus status) {
        return status == PENDING || status == READY || status == INVALIDATED;
    }

    private boolean dependenciesDone(Run run, StageDefinition def) {
        return def.dependsOn().stream().allMatch(d -> run.getStages().get(d).getStatus().isDone());
    }

    private boolean isTerminal(Run run) {
        RunStatus s = run.getStatus();
        return s == RunStatus.DONE || s == RunStatus.FAILED || s == RunStatus.STOPPED;
    }

    private Run require(UUID runId) {
        Run run = runs.get(runId);
        if (run == null) {
            throw new NoSuchElementException("Run not found: " + runId);
        }
        return run;
    }

    private StageExecution requireWaiting(Run run, StageId stage) {
        if (isTerminal(run)) {
            throw new IllegalStateException("Run is already " + run.getStatus());
        }
        StageExecution ex = run.getStages().get(stage);
        if (ex == null || ex.getStatus() != WAITING_APPROVAL) {
            throw new IllegalStateException("Stage " + stage + " is not waiting for approval");
        }
        return ex;
    }

    private void enforcePolicy(StageId stage, String text) {
        policy.check(stage, text).ifPresent(v -> {
            throw new IllegalArgumentException(v);
        });
    }

    private Artifact storeArtifact(Run run, StageId stage, String name, String content) {
        int version = latestOf(run, stage).map(a -> a.version() + 1).orElse(1);
        Artifact artifact = Artifact.of(stage, name, version, content);
        run.addArtifact(artifact);
        return artifact;
    }

    private Optional<Artifact> latestOf(Run run, StageId stage) {
        List<Artifact> list = run.getArtifacts();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).producedBy() == stage) {
                return Optional.of(list.get(i));
            }
        }
        return Optional.empty();
    }

    private Map<StageId, Artifact> latestByStage(Run run, StageId exclude) {
        Map<StageId, Artifact> map = new EnumMap<>(StageId.class);
        for (Artifact a : run.getArtifacts()) {
            if (a.producedBy() != exclude) {
                map.put(a.producedBy(), a);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private List<UUID> basis(Run run, StageDefinition def) {
        List<UUID> ids = new ArrayList<>();
        for (StageId dep : def.dependsOn()) {
            latestOf(run, dep).ifPresent(a -> ids.add(a.id()));
        }
        return ids;
    }

    private void recordInputHashes(Run run, StageDefinition def, StageExecution ex) {
        ex.getInputHashes().clear();
        for (StageId dep : def.dependsOn()) {
            latestOf(run, dep).ifPresent(a -> ex.getInputHashes().put(dep, a.hash()));
        }
    }

    private String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
