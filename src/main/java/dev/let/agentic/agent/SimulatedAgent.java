package dev.let.agentic.agent;

import dev.let.agentic.domain.StageId;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Deterministic agent: same input, same output. Used for tests, demos and as fallback. */
public class SimulatedAgent implements Agent {

    private static final List<String> VAGUE = List.of(
            "more secure", "safer", "better", "improve", "faster",
            "mas seguro", "más seguro", "mejor");

    @Override
    public AgentResult execute(AgentContext ctx) {
        String requirement = ctx.run().getRequirement();
        boolean ambiguous = ctx.stage() == StageId.REQUIREMENT && isAmbiguous(requirement);
        String body = switch (ctx.stage()) {
            case REQUIREMENT -> "Normalized problem: " + requirement
                    + "\nType: " + ctx.run().getType()
                    + "\nAmbiguities: " + (ambiguous
                            ? "scope is vague and success criteria are undefined" : "none detected")
                    + "\nAssumptions: single node deployment, in-memory storage";
            case IMPACT -> "Impacted modules: ShortUrlService, UrlController, ClickCounter"
                    + "\nImpacted APIs: POST /urls, GET /{code}"
                    + "\nData flow: create, persist, redirect, count click";
            case PLANNING -> "Tasks: 1 define API and schema; 2 design components; 3 implement;"
                    + " 4 generate tests; 5 write docs; 6 validate"
                    + "\nDependencies: 3 after 1 and 2; 4 and 5 after 3; 6 after 4 and 5";
            case ARCHITECTURE -> "Components: ShortUrlController, ShortUrlService, ShortUrlRepository, ClickCounter"
                    + "\nStyle: layered, in-memory store, base62 codes from a sequence";
            case SECURITY -> "Controls: validate URL scheme, block private-network targets,"
                    + " rate-limit creation, never log credentials"
                    + "\nRisks: open redirect abuse, enumeration of short codes";
            case DATA_API -> "API: POST /urls (create), GET /{code} (302 redirect), GET /urls/{code}/stats"
                    + "\nSchema: ShortUrl(code, targetUrl, createdAt, expiresAt, clicks)";
            case IMPLEMENTATION -> "Implemented controller, service and repository following the approved design";
            case TESTS -> "Generated unit tests for code generation and validation,"
                    + " integration tests for create and redirect";
            case DOCS -> "README and API notes covering setup, endpoints and limitations";
            case VALIDATION -> "Checklist: build ok, tests pass, policies clean, docs present";
            case RELEASE -> "Release package prepared";
            default -> "No agent work for stage " + ctx.stage();
        };
        String name = ctx.stage().name().toLowerCase(Locale.ROOT) + "-output";
        return new AgentResult(name, body + feedback(ctx), ambiguous);
    }

    private static boolean isAmbiguous(String requirement) {
        String text = requirement.toLowerCase(Locale.ROOT);
        boolean tooShort = text.trim().split("\\s+").length < 4;
        return tooShort || VAGUE.stream().anyMatch(text::contains);
    }

    private static String feedback(AgentContext ctx) {
        return ctx.inputs().values().stream()
                .filter(a -> a.name().equals("rejection-feedback"))
                .map(a -> "\nRevised after human feedback: " + a.content())
                .collect(Collectors.joining());
    }
}
