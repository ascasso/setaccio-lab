package com.setaccio.lab.thinking;

import java.util.Objects;

/** One resolved, already-installed Ollama artifact with its full immutable digest. */
public record ThinkingDiagnosticModelIdentity(
        ThinkingDiagnosticModelRole role,
        String requestedModel,
        String normalizedInstalledName,
        String digest,
        boolean advertisesThinking
) {
    public ThinkingDiagnosticModelIdentity {
        role = Objects.requireNonNull(role, "role must not be null");
        requireModelTag(requestedModel, "requestedModel");
        requireModelTag(normalizedInstalledName, "normalizedInstalledName");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase Ollama digest");
        }
    }

    private static void requireModelTag(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(field + " must be a safe Ollama model tag");
        }
    }
}
