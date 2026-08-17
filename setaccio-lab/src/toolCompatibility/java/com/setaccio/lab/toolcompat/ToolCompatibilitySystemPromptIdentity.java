package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;

record ToolCompatibilitySystemPromptIdentity(
        String id,
        int version,
        String sha256,
        String text,
        boolean present
) {

    ToolCompatibilitySystemPromptIdentity {
        id = requireText(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (present != !text.isEmpty()) {
            throw new IllegalArgumentException("present must match whether text is non-empty");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a full lowercase SHA-256 digest");
        }
        if (!sha256.equals(EvidenceIntegrity.sha256(text.getBytes(StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException("sha256 must match the UTF-8 prompt text");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
