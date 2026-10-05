package dev.let.agentic.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.RunType;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.graph.StageGraph;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClaudeAgentTest {

    private final AtomicInteger calls = new AtomicInteger();

    private Run run(String requirement) {
        return new Run(requirement, RunType.GREENFIELD, StageGraph.standard().all());
    }

    @Test
    void requirementStageUsesTheModelAndParsesAmbiguity() throws Exception {
        ClaudeClient fake = (system, user) -> {
            calls.incrementAndGet();
            return "AMBIGUOUS: YES\nThe scope is unclear.";
        };
        ClaudeAgent agent = new ClaudeAgent(fake, new SimulatedAgent());

        AgentResult result = agent.execute(
                new AgentContext(run("Make it more secure"), StageId.REQUIREMENT, Map.of(), 1));

        assertThat(result.ambiguous()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void notAmbiguousWhenTheModelSaysNo() throws Exception {
        ClaudeAgent agent = new ClaudeAgent((system, user) -> "AMBIGUOUS: NO\nClear enough.", new SimulatedAgent());

        AgentResult result = agent.execute(
                new AgentContext(run("Build a URL shortener"), StageId.REQUIREMENT, Map.of(), 1));

        assertThat(result.ambiguous()).isFalse();
    }

    @Test
    void stagesOutsideTheModelScopeAreDelegated() throws Exception {
        ClaudeClient fake = (system, user) -> {
            calls.incrementAndGet();
            return "should not be called";
        };
        ClaudeAgent agent = new ClaudeAgent(fake, new SimulatedAgent());

        AgentResult result = agent.execute(
                new AgentContext(run("Build a URL shortener"), StageId.VALIDATION, Map.of(), 1));

        assertThat(calls.get()).isZero();
        assertThat(result.content()).contains("Checklist");
    }

    @Test
    void parsesAmbiguityTolerantlyOfFormatting() {
        assertThat(ClaudeAgent.parseAmbiguous("**AMBIGUOUS: YES**\nrest")).isTrue();
        assertThat(ClaudeAgent.parseAmbiguous("ambiguous : yes")).isTrue();
        assertThat(ClaudeAgent.parseAmbiguous("AMBIGUOUS: NO")).isFalse();
        assertThat(ClaudeAgent.parseAmbiguous("")).isFalse();
    }
}
