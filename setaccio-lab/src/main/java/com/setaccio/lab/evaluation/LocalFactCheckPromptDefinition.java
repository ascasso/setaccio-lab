package com.setaccio.lab.evaluation;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class LocalFactCheckPromptDefinition {

    public static final String ID = "local-fact-check";
    public static final String VERSION = "1";
    public static final String SHA256 =
            "e75e0ddd9bef80eecf27e1b668cef954a5eddb5a74b5e4c19db97710c3d39470";
    public static final String RESOURCE_PATH = "prompts/evaluation/local-fact-check-v1.md";
    public static final String DOCUMENT_PLACEHOLDER = "{document}";
    public static final String CLAIM_PLACEHOLDER = "{claim}";

    private final String text;
    private final String sha256;

    public LocalFactCheckPromptDefinition() {
        byte[] bytes = loadBytes();
        text = new String(bytes, StandardCharsets.UTF_8);
        sha256 = EvidenceIntegrity.sha256(bytes);
        requireExactlyOne(text, DOCUMENT_PLACEHOLDER);
        requireExactlyOne(text, CLAIM_PLACEHOLDER);
        String withoutKnownPlaceholders = text
                .replace(DOCUMENT_PLACEHOLDER, "")
                .replace(CLAIM_PLACEHOLDER, "");
        if (withoutKnownPlaceholders.contains("{") || withoutKnownPlaceholders.contains("}")) {
            throw new IllegalStateException("Fact-check prompt contains an unsupported placeholder");
        }
    }

    public String id() {
        return ID;
    }

    public String version() {
        return VERSION;
    }

    public String text() {
        return text;
    }

    public String sha256() {
        return sha256;
    }

    private static byte[] loadBytes() {
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load tracked fact-check prompt " + RESOURCE_PATH, exception);
        }
    }

    private static void requireExactlyOne(String prompt, String placeholder) {
        int first = prompt.indexOf(placeholder);
        if (first < 0 || first != prompt.lastIndexOf(placeholder)) {
            throw new IllegalStateException("Fact-check prompt must contain exactly one " + placeholder + " placeholder");
        }
    }
}
