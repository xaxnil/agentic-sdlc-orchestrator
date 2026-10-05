package dev.let.agentic.domain;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Runtime state of one stage inside one run. */
public class StageExecution {
    private final StageId stageId;
    private volatile StageStatus status = StageStatus.PENDING;
    private final AtomicInteger attempts = new AtomicInteger();
    private final Map<StageId, String> inputHashes = new ConcurrentHashMap<>();
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String lastError;

    public StageExecution(StageId stageId) { this.stageId = stageId; }

    public StageId getStageId() { return stageId; }
    public StageStatus getStatus() { return status; }
    public void setStatus(StageStatus status) { this.status = status; }
    public int getAttempts() { return attempts.get(); }
    public int incrementAttempts() { return attempts.incrementAndGet(); }
    public Map<StageId, String> getInputHashes() { return inputHashes; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
