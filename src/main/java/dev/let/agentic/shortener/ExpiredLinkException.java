package dev.let.agentic.shortener;

public class ExpiredLinkException extends RuntimeException {
    public ExpiredLinkException(String code) {
        super("Link has expired: " + code);
    }
}
