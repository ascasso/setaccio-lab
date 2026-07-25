package com.setaccio.lab.evidence;

import java.nio.file.Path;
import java.util.Locale;

public record EvidenceArtifact(
        String path,
        String role,
        long sizeBytes,
        String sha256
) {

    public EvidenceArtifact {
        path = requireRelativePath(path);
        role = requireText(role, "role");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        sha256 = requireText(sha256, "sha256").toLowerCase(Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
        }
    }

    private static String requireRelativePath(String value) {
        String pathValue = requireText(value, "path");
        if (pathValue.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("path must use forward slashes");
        }

        Path parsed = Path.of(pathValue);
        if (parsed.isAbsolute()
                || pathValue.matches("^[A-Za-z]:/.*")
                || parsed.getNameCount() == 0
                || containsParentTraversal(parsed)
                || !parsed.normalize().toString().replace('\\', '/').equals(pathValue)
                || ".".equals(pathValue)) {
            throw new IllegalArgumentException("path must be a normalized relative artifact path");
        }
        return pathValue;
    }

    private static boolean containsParentTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
