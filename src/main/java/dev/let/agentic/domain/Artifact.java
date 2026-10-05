package dev.let.agentic.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Versioned output of a stage. The hash drives re-planning. */
public record Artifact(
        UUID id,
        StageId producedBy,
        String name,
        int version,
        String content,
        String hash,
        Instant createdAt) {

    public static Artifact of(StageId producedBy, String name, int version, String content) {
        return new Artifact(UUID.randomUUID(), producedBy, name, version,
                content, sha256(content), Instant.now());
    }

    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
