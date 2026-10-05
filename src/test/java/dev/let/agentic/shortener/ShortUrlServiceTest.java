package dev.let.agentic.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.let.agentic.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortUrlServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T12:00:00Z"));
    private final ShortUrlService service = new ShortUrlService(clock);

    @Test
    void createsResolvesAndCountsClicks() {
        ShortUrl created = service.create("https://example.com/docs?q=1", null);

        assertThat(created.getCode()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(service.resolve(created.getCode())).isEqualTo("https://example.com/docs?q=1");
        service.resolve(created.getCode());
        assertThat(service.stats(created.getCode()).clicks()).isEqualTo(2);
    }

    @Test
    void codesAreUnique() {
        String a = service.create("https://example.com/a", null).getCode();
        String b = service.create("https://example.com/b", null).getCode();
        assertThat(a).isNotEqualTo(b);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "javascript:alert(1)", "not a url", "https://", ""})
    void rejectsInvalidOrDangerousSchemes(String url) {
        assertThatThrownBy(() -> service.create(url, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/admin", "http://127.0.0.1/", "http://192.168.1.5/",
            "http://10.0.0.1/", "http://169.254.169.254/latest/meta-data", "http://[::1]/x",
            "http://printer.local/"})
    void rejectsInternalTargets(String url) {
        assertThatThrownBy(() -> service.create(url, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal");
    }

    @Test
    void expiredLinkIsRejectedAndNotCounted() {
        ShortUrl created = service.create("https://example.com/", clock.instant().plus(Duration.ofHours(1)));
        service.resolve(created.getCode());

        clock.advance(Duration.ofHours(2));

        assertThatThrownBy(() -> service.resolve(created.getCode())).isInstanceOf(ExpiredLinkException.class);
        assertThat(service.stats(created.getCode()).expired()).isTrue();
        assertThat(service.stats(created.getCode()).clicks()).isEqualTo(1);
    }

    @Test
    void expirationInThePastIsRejected() {
        assertThatThrownBy(() -> service.create("https://example.com/", clock.instant().minusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownCodeIsNotFound() {
        assertThatThrownBy(() -> service.resolve("zzzzzz")).isInstanceOf(NoSuchElementException.class);
    }
}
