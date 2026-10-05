package dev.let.agentic.domain;

public enum StageStatus {
    PENDING, READY, RUNNING, WAITING_APPROVAL, SUCCEEDED,
    FAILED, SKIPPED, INVALIDATED, ROLLED_BACK, STOPPED;

    /** A stage that no longer blocks its dependents. */
    public boolean isDone() {
        return this == SUCCEEDED || this == SKIPPED;
    }
}
