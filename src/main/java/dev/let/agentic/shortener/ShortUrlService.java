package dev.let.agentic.shortener;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ShortUrlService {

    public record Stats(String code, String targetUrl, Instant createdAt,
                        Instant expiresAt, long clicks, boolean expired) { }

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final int MAX_URL_LENGTH = 2048;

    private final Map<String, ShortUrl> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1_000_000);
    private final Clock clock;

    public ShortUrlService(Clock clock) {
        this.clock = clock;
    }

    public ShortUrl create(String targetUrl, Instant expiresAt) {
        String normalized = validate(targetUrl);
        Instant now = clock.instant();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        String code = encode(sequence.incrementAndGet());
        ShortUrl created = new ShortUrl(code, normalized, now, expiresAt);
        store.put(code, created);
        return created;
    }

    /** Resolves a code for a redirect and counts the click. */
    public String resolve(String code) {
        ShortUrl url = find(code);
        if (url.isExpired(clock.instant())) {
            throw new ExpiredLinkException(code);
        }
        url.registerClick();
        return url.getTargetUrl();
    }

    public Stats stats(String code) {
        ShortUrl url = find(code);
        return new Stats(url.getCode(), url.getTargetUrl(), url.getCreatedAt(), url.getExpiresAt(),
                url.getClicks(), url.isExpired(clock.instant()));
    }

    private ShortUrl find(String code) {
        ShortUrl url = store.get(code);
        if (url == null) {
            throw new NoSuchElementException("Unknown short code: " + code);
        }
        return url;
    }

    private String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String value = raw.trim();
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("url is too long");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("url is not valid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("only http and https urls are allowed");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("url must include a host");
        }
        rejectInternalHost(uri.getHost());
        return value;
    }

    /** Blocks obvious internal targets. Does not resolve DNS, so DNS rebinding is out of scope. */
    private void rejectInternalHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost")
                || h.endsWith(".local") || h.endsWith(".internal")) {
            throw new IllegalArgumentException("url points to an internal host");
        }
        if (IPV4.matcher(h).matches() || h.startsWith("[")) {
            try {
                InetAddress a = InetAddress.getByName(h);
                if (a.isAnyLocalAddress() || a.isLoopbackAddress()
                        || a.isSiteLocalAddress() || a.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("url points to an internal or private network address");
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("url host is not valid");
            }
        }
    }

    static String encode(long n) {
        StringBuilder sb = new StringBuilder();
        long value = n;
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
