package dev.let.agentic.agent;

/** Does the work of one stage. Implementations: SimulatedAgent now, ClaudeAgent later. */
@FunctionalInterface
public interface Agent {
    AgentResult execute(AgentContext context) throws Exception;
}
