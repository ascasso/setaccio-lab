package com.setaccio.lab.evidence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EvidenceManifest(
        int manifestVersion,
        String suite,
        String runId,
        Instant generatedAt,
        EvidenceCodeBaseline codeBaseline,
        EvidenceFrameworkVersions frameworkVersions,
        String executionEngine,
        Map<String, Object> settings,
        List<EvidenceArtifact> artifacts
) {

    public static final int CURRENT_VERSION = 1;

    public EvidenceManifest {
        if (manifestVersion < 1) {
            throw new IllegalArgumentException("manifestVersion must be positive");
        }
        suite = requireText(suite, "suite");
        runId = requireText(runId, "runId");
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        if (frameworkVersions == null) {
            throw new IllegalArgumentException("frameworkVersions must not be null");
        }
        executionEngine = requireText(executionEngine, "executionEngine");
        settings = copySettings(settings);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    private static Map<String, Object> copySettings(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("settings keys must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("settings values must not be null");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
