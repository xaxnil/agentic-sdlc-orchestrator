package dev.let.agentic.metrics;

import dev.let.agentic.domain.AuditEvent;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.RunStatus;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.engine.Orchestrator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Reliability metrics computed from the audit log.
 * MTTR = time from a stage's first failure to its next success (unrecovered failures are excluded).
 * End-to-end latency covers DONE runs only and includes time spent waiting for human approvals.
 */
@Service
public class MetricsService {

    public record Metrics(int totalRuns, int done, int failed, int stopped, int inProgress,
                          double successRate, long stageRetries, long rollbacks, long policyViolations,
                          long approvals, long rejections, Double mttrMillis, Double avgEndToEndMillis) { }

    private final Orchestrator orchestrator;

    public MetricsService(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public Metrics snapshot() {
        int done = 0;
        int failed = 0;
        int stopped = 0;
        int inProgress = 0;
        long retries = 0;
        long rollbacks = 0;
        long violations = 0;
        long approvals = 0;
        long rejections = 0;
        List<Long> recovery = new ArrayList<>();
        List<Long> latency = new ArrayList<>();
        List<Run> runs = new ArrayList<>(orchestrator.allRuns());

        for (Run run : runs) {
            switch (run.getStatus()) {
                case DONE -> done++;
                case FAILED -> failed++;
                case STOPPED -> stopped++;
                default -> inProgress++;
            }
            Map<StageId, Instant> open = new EnumMap<>(StageId.class);
            Instant started = null;
            Instant finished = null;
            for (AuditEvent e : run.getAuditLog()) {
                switch (e.type()) {
                    case STAGE_RETRY -> {
                        retries++;
                        open.putIfAbsent(e.stage(), e.at());
                    }
                    case STAGE_FAILED -> open.putIfAbsent(e.stage(), e.at());
                    case STAGE_SUCCEEDED -> {
                        Instant since = open.remove(e.stage());
                        if (since != null) {
                            recovery.add(Duration.between(since, e.at()).toMillis());
                        }
                    }
                    case ROLLBACK -> rollbacks++;
                    case POLICY_VIOLATION -> violations++;
                    case APPROVED -> approvals++;
                    case REJECTED -> rejections++;
                    case RUN_STARTED -> started = e.at();
                    case RUN_DONE -> finished = e.at();
                    default -> { }
                }
            }
            if (run.getStatus() == RunStatus.DONE && started != null && finished != null) {
                latency.add(Duration.between(started, finished).toMillis());
            }
        }
        int finishedRuns = done + failed + stopped;
        double successRate = finishedRuns == 0 ? 0.0 : (double) done / finishedRuns;
        return new Metrics(runs.size(), done, failed, stopped, inProgress, successRate,
                retries, rollbacks, violations, approvals, rejections,
                average(recovery), average(latency));
    }

    private static Double average(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
}
