package dev.let.agentic.shortener;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class ShortUrl {
    private final String code;
    private final String targetUrl;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final AtomicLong clicks = new AtomicLong();

    public ShortUrl(String code, String targetUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.targetUrl = targetUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public String getCode() { return code; }
    public String getTargetUrl() { return targetUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getClicks() { return clicks.get(); }
    public long registerClick() { return clicks.incrementAndGet(); }
}
