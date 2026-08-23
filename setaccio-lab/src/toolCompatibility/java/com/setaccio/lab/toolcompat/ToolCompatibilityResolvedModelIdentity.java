package com.setaccio.lab.toolcompat;

/** Common immutable model identity consumed by single-model and cohort row analysis. */
interface ToolCompatibilityResolvedModelIdentity {

    String requestedModel();

    String effectiveModel();

    String digest();

    static void requireFields(String requestedModel, String effectiveModel, String digest) {
        requireText(requestedModel, "requestedModel");
        requireText(effectiveModel, "effectiveModel");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "digest must be a full lowercase SHA-256 digest");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
