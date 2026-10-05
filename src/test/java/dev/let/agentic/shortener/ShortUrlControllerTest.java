package dev.let.agentic.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.let.agentic.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ShortUrlControllerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T12:00:00Z"));
    private final ShortUrlService service = new ShortUrlService(clock);
    private final ShortUrlController controller =
            new ShortUrlController(service, new RateLimiter(2, Duration.ofMinutes(1), clock));

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/urls");
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    @Test
    void createReturns201WithShortUrlAndRedirectReturns302() {
        var response = controller.create(
                new ShortUrlController.CreateUrlRequest("https://example.com/page", null), request());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String code = response.getBody().code();
        assertThat(response.getBody().shortUrl()).endsWith("/" + code);

        var redirect = controller.redirect(code);
        assertThat(redirect.getStatusCode().value()).isEqualTo(302);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/page");
    }

    @Test
    void thirdCreationInTheSameWindowIsRateLimited() {
        var body = new ShortUrlController.CreateUrlRequest("https://example.com/", null);
        controller.create(body, request());
        controller.create(body, request());

        assertThatThrownBy(() -> controller.create(body, request()))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
