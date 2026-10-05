package dev.let.agentic.domain;

/** When a stage applies to a run. If it does not apply, it is SKIPPED. */
public enum StageCondition { ALWAYS, IF_AMBIGUOUS, IF_BROWNFIELD }
