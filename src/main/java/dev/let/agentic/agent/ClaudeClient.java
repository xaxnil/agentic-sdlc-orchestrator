package dev.let.agentic.agent;

/** Transport to the model. Kept as an interface so the agent can be tested without network. */
@FunctionalInterface
public interface ClaudeClient {
    String complete(String system, String user);
}
