package com.setaccio.lab.toolcompat;

/** One read-only model entry supplied to the provider-free cohort preflight. */
record ToolCompatibilityCohortInventoryModel(
        String installedTag,
        String digest,
        ExecutionLocation executionLocation,
        ToolCompatibilityCohortSeedSemantics seedSemantics,
        ToolCompatibilityCohortModelMetadata metadata
) {

    ToolCompatibilityCohortInventoryModel {
        installedTag = requireText(installedTag, "installedTag");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "digest must be one full lowercase SHA-256 digest");
        }
        if (executionLocation == null) {
            throw new IllegalArgumentException("executionLocation is required");
        }
        if (seedSemantics == null) {
            throw new IllegalArgumentException("seedSemantics is required");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    enum ExecutionLocation {
        LOCAL,
        REMOTE
    }
}
