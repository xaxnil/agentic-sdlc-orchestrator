package dev.let.agentic.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.let.agentic.domain.StageId;
import org.junit.jupiter.api.Test;

class PolicyGuardTest {

    private final PolicyGuard guard = new PolicyGuard();

    @Test
    void flagsHardcodedSecrets() {
        assertThat(guard.check(StageId.IMPLEMENTATION, "password = hunter2hunter2")).isPresent();
        assertThat(guard.check(StageId.IMPLEMENTATION, "key AKIAABCDEFGHIJKLMNOP")).isPresent();
        assertThat(guard.check(StageId.IMPLEMENTATION, "sk-ant-abcdefghij12345")).isPresent();
    }

    @Test
    void allowsNormalText() {
        assertThat(guard.check(StageId.SECURITY, "Controls: validate URLs and rate-limit creation")).isEmpty();
    }

    @Test
    void messageNeverEchoesTheSecret() {
        String message = guard.check(StageId.IMPLEMENTATION, "api_key=ABCDEFGH12345678").orElseThrow();
        assertThat(message).doesNotContain("ABCDEFGH12345678");
    }
}
