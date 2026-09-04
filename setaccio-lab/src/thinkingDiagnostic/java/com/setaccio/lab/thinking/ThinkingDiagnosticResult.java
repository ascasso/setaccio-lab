package com.setaccio.lab.thinking;

import java.util.List;
import java.util.Objects;

/** The complete recorded diagnostic suite: locked protocol identity plus every retained row. */
public record ThinkingDiagnosticResult(
        int protocolVersion,
        String provider,
        String endpointCategory,
        String executionStrategy,
        String pullModelStrategy,
        double temperature,
        int seed,
        int maxAttempts,
        long requestTimeoutMillis,
        String promptDelivery,
        String policyComparison,
        String boundaryComparison,
        List<ThinkingDiagnosticArm> arms,
        List<ThinkingDiagnosticModelIdentity> modelIdentities,
        String ollamaVersion,
        String promptId,
        String promptVersion,
        String promptSha256,
        String fixtureCatalogId,
        String fixtureCatalogVersion,
        String fixtureCatalogSha256,
        List<ThinkingDiagnosticScheduleEntry> orderedSchedule,
        List<ThinkingDiagnosticRow> rows
) {
    public ThinkingDiagnosticResult {
        provider = requireText(provider, "provider");
        endpointCategory = requireText(endpointCategory, "endpointCategory");
        executionStrategy = requireText(executionStrategy, "executionStrategy");
        pullModelStrategy = requireText(pullModelStrategy, "pullModelStrategy");
        promptId = requireText(promptId, "promptId");
        promptVersion = requireText(promptVersion, "promptVersion");
        promptSha256 = requireSha256(promptSha256, "promptSha256");
        fixtureCatalogId = requireText(fixtureCatalogId, "fixtureCatalogId");
        fixtureCatalogVersion = requireText(fixtureCatalogVersion, "fixtureCatalogVersion");
        fixtureCatalogSha256 = requireSha256(fixtureCatalogSha256, "fixtureCatalogSha256");
        ollamaVersion = requireText(ollamaVersion, "ollamaVersion");
        arms = List.copyOf(Objects.requireNonNull(arms, "arms must not be null"));
        modelIdentities = List.copyOf(
                Objects.requireNonNull(modelIdentities, "modelIdentities must not be null"));
        orderedSchedule = List.copyOf(
                Objects.requireNonNull(orderedSchedule, "orderedSchedule must not be null"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        if (!ThinkingDiagnosticProtocol.supportsVersion(protocolVersion)) {
            throw new IllegalArgumentException("protocolVersion must be a supported diagnostic version");
        }
        if (protocolVersion == ThinkingDiagnosticProtocol.VERSION) {
            promptDelivery = requireText(promptDelivery, "promptDelivery");
            policyComparison = requireText(policyComparison, "policyComparison");
            boundaryComparison = requireText(boundaryComparison, "boundaryComparison");
        } else if (promptDelivery != null || policyComparison != null || boundaryComparison != null) {
            throw new IllegalArgumentException("protocol v1 must not record v2 comparison settings");
        }
        if (maxAttempts != ThinkingDiagnosticProtocol.MAX_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1");
        }
        if (requestTimeoutMillis < 1) {
            throw new IllegalArgumentException("requestTimeoutMillis must be positive");
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    private static String requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hex digest");
        }
        return value;
    }
}
