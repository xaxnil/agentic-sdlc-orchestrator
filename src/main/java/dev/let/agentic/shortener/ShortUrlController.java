package dev.let.agentic.shortener;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class ShortUrlController {

    public record CreateUrlRequest(@NotBlank @Size(max = 2048) String url, Instant expiresAt) { }

    public record CreatedUrl(String code, String shortUrl, String targetUrl, Instant expiresAt) { }

    private final ShortUrlService service;
    private final RateLimiter rateLimiter;

    public ShortUrlController(ShortUrlService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/urls")
    public ResponseEntity<CreatedUrl> create(@Valid @RequestBody CreateUrlRequest request,
                                             HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            throw new RateLimitExceededException();
        }
        ShortUrl created = service.create(request.url(), request.expiresAt());
        String shortUrl = ServletUriComponentsBuilder.fromContextPath(http)
                .path("/" + created.getCode()).toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CreatedUrl(created.getCode(), shortUrl, created.getTargetUrl(), created.getExpiresAt()));
    }

    @GetMapping("/{code:[0-9A-Za-z]{4,12}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @GetMapping("/urls/{code}/stats")
    public ShortUrlService.Stats stats(@PathVariable String code) {
        return service.stats(code);
    }
}
