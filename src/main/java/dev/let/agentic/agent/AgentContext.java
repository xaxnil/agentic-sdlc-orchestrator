package dev.let.agentic.agent;

import dev.let.agentic.domain.Artifact;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.StageId;
import java.util.Map;

/** Everything an agent may see: the run and the latest artifact of every other stage. */
public record AgentContext(Run run, StageId stage, Map<StageId, Artifact> inputs, int attempt) {
}
