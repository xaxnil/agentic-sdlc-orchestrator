package dev.let.agentic.shortener;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Too many requests, try again later");
    }
}
