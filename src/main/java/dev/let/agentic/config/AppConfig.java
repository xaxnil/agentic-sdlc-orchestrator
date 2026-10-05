package dev.let.agentic.config;

import dev.let.agentic.agent.Agent;
import dev.let.agentic.agent.AnthropicHttpClient;
import dev.let.agentic.agent.ClaudeAgent;
import dev.let.agentic.agent.SimulatedAgent;
import dev.let.agentic.engine.Orchestrator;
import dev.let.agentic.engine.PolicyGuard;
import dev.let.agentic.graph.StageGraph;
import dev.let.agentic.shortener.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimiter rateLimiter(Clock clock) {
        return new RateLimiter(20, Duration.ofMinutes(1), clock);
    }

    @Bean
    StageGraph stageGraph() {
        return StageGraph.standard();
    }

    @Bean
    PolicyGuard policyGuard() {
        return new PolicyGuard();
    }

    @Bean(destroyMethod = "close")
    Orchestrator orchestrator(StageGraph graph, PolicyGuard policy,
                              @Value("${agent.mode:simulated}") String mode,
                              @Value("${anthropic.api-key:}") String apiKey,
                              @Value("${anthropic.model:claude-haiku-4-5-20251001}") String model) {
        Agent fallback = new SimulatedAgent();
        Agent primary = switch (mode.toLowerCase(Locale.ROOT)) {
            case "simulated" -> fallback;
            case "claude" -> {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "AGENT_MODE=claude requires the ANTHROPIC_API_KEY environment variable");
                }
                yield new ClaudeAgent(new AnthropicHttpClient(apiKey, model), fallback);
            }
            default -> throw new IllegalStateException("Unknown AGENT_MODE: " + mode);
        };
        return new Orchestrator(graph, primary, fallback, policy);
    }
}
