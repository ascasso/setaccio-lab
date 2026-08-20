package com.setaccio.lab.toolcompat;

record ToolCompatibilityModelIdentity(
        String requestedModel,
        String effectiveModel,
        String digest
) {

    ToolCompatibilityModelIdentity {
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(requestedModel)) {
            throw new IllegalArgumentException("requestedModel must equal the locked initial model");
        }
        effectiveModel = requireText(effectiveModel, "effectiveModel");
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase SHA-256 digest");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
