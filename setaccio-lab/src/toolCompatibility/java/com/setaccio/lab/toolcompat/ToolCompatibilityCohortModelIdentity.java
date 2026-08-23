package com.setaccio.lab.toolcompat;

/** Resolved immutable identity for one peer or the separately labelled reference. */
record ToolCompatibilityCohortModelIdentity(
        int cohortPosition,
        Role role,
        String requestedTag,
        String effectiveInstalledTag,
        String digest,
        ToolCompatibilityCohortModelMetadata metadata
) {

    ToolCompatibilityCohortModelIdentity {
        if (cohortPosition < 1) {
            throw new IllegalArgumentException("cohortPosition must be positive");
        }
        if (role == null || metadata == null) {
            throw new IllegalArgumentException("cohort model role and metadata are required");
        }
        requestedTag = requireText(requestedTag, "requestedTag");
        effectiveInstalledTag = requireText(effectiveInstalledTag, "effectiveInstalledTag");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "digest must be one full lowercase SHA-256 digest");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    enum Role {
        PEER,
        REFERENCE
    }
}
