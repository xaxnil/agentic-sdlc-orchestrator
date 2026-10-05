package dev.let.agentic.agent;

public record AgentResult(String name, String content, boolean ambiguous) {

    public static AgentResult of(String name, String content) {
        return new AgentResult(name, content, false);
    }
}
