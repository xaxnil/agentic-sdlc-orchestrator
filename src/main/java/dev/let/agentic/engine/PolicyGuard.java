package dev.let.agentic.engine;

import dev.let.agentic.domain.StageId;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Security policy: no secrets in any stage output. The message never echoes the secret. */
public class PolicyGuard {

    private record Rule(String name, Pattern pattern) { }

    private static final List<Rule> RULES = List.of(
            new Rule("Anthropic API key", Pattern.compile("sk-ant-[A-Za-z0-9_\\-]{10,}")),
            new Rule("AWS access key", Pattern.compile("AKIA[0-9A-Z]{16}")),
            new Rule("private key block", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----")),
            new Rule("hardcoded credential", Pattern.compile(
                    "(?i)(password|passwd|secret|api[_-]?key|token)\\s*[:=]\\s*[\"']?[A-Za-z0-9/+_\\-]{8,}")));

    public Optional<String> check(StageId stage, String content) {
        if (content == null) {
            return Optional.empty();
        }
        return RULES.stream()
                .filter(r -> r.pattern().matcher(content).find())
                .map(r -> "Policy 'no secrets in output' violated at " + stage + ": " + r.name())
                .findFirst();
    }
}
