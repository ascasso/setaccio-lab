package com.setaccio.lab.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Explicitly enumerates the tracked vision prompts that can participate in a
 * controlled run. The interactive endpoint continues to inject v1 directly.
 */
@Component
public final class VisionPromptCatalog {

    public static final String VERSION_2 = "2";
    public static final String VERSION_2_RESOURCE_PATH = "prompts/vision/image-analysis-v2.md";
    private static final Set<String> SUPPORTED_VERSIONS = Set.of(VisionPromptDefinition.VERSION, VERSION_2);

    private final Map<String, VisionPromptDefinition> prompts;

    public VisionPromptCatalog(VisionPromptDefinition version1) {
        VisionPromptDefinition version2 = new VisionPromptDefinition(
                VisionPromptDefinition.ID, VERSION_2, VERSION_2_RESOURCE_PATH);
        prompts = Map.of(version1.version(), version1, version2.version(), version2);
    }

    public VisionPromptDefinition require(String version) {
        String requestedVersion = Objects.requireNonNull(version, "Vision prompt version is required");
        VisionPromptDefinition prompt = prompts.get(requestedVersion);
        if (prompt == null) {
            throw new IllegalArgumentException(
                    "Unsupported vision prompt version '" + requestedVersion + "'; supported versions: "
                            + String.join(", ", prompts.keySet().stream().sorted().toList()));
        }
        return prompt;
    }

    public static boolean supports(String version) {
        return version != null && SUPPORTED_VERSIONS.contains(version);
    }
}
