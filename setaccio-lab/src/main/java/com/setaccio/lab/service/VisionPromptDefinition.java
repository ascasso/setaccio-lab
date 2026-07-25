package com.setaccio.lab.service;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class VisionPromptDefinition {

    public static final String ID = "vision-image-analysis";
    public static final String VERSION = "1";
    public static final String RESOURCE_PATH = "prompts/vision/image-analysis-v1.md";

    private final String text;
    private final String sha256;
    private final List<String> requiredSections;

    public VisionPromptDefinition() {
        byte[] bytes = loadPromptBytes();
        text = new String(bytes, StandardCharsets.UTF_8);
        sha256 = EvidenceIntegrity.sha256(bytes);
        requiredSections = text.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3).strip())
                .toList();
        if (requiredSections.isEmpty() || requiredSections.stream().distinct().count() != requiredSections.size()) {
            throw new IllegalStateException("Vision prompt must declare unique required sections");
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

    public List<String> requiredSections() {
        return requiredSections;
    }

    private static byte[] loadPromptBytes() {
        try (var input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            return input.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load tracked vision prompt " + RESOURCE_PATH, e);
        }
    }
}
