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

    static final String UNTREATED_ID = "tool-system-none";
    static final int UNTREATED_VERSION = 1;
    static final String UNTREATED_TEXT = "";
    static final boolean UNTREATED_PRESENT = false;
    static final String UNTREATED_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    static final String DISCIPLINE_ID = "tool-system-discipline";
    static final int DISCIPLINE_VERSION = 1;
    static final boolean DISCIPLINE_PRESENT = true;
    static final String DISCIPLINE_SHA256 = "fcc115e73a44ed2fcd76ba11ee0937a54d308465e16a929d781d8de27e04cd71";

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

    static ToolCompatibilitySystemPromptIdentity untreated() {
        ToolCompatibilitySystemPromptIdentity identity = new ToolCompatibilitySystemPromptIdentity(
                UNTREATED_ID,
                UNTREATED_VERSION,
                UNTREATED_SHA256,
                UNTREATED_TEXT,
                UNTREATED_PRESENT);
        identity.requireUntreated();
        return identity;
    }

    void requireUntreated() {
        if (!UNTREATED_ID.equals(id)
                || UNTREATED_VERSION != version
                || !UNTREATED_SHA256.equals(sha256)
                || !UNTREATED_TEXT.equals(text)
                || UNTREATED_PRESENT != present) {
            throw new IllegalArgumentException("System prompt identity must equal the locked untreated baseline");
        }
    }

    void requireToolDiscipline() {
        if (!DISCIPLINE_ID.equals(id)
                || DISCIPLINE_VERSION != version
                || !DISCIPLINE_SHA256.equals(sha256)
                || DISCIPLINE_PRESENT != present) {
            throw new IllegalArgumentException("System prompt identity must equal the locked tool-discipline prompt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
