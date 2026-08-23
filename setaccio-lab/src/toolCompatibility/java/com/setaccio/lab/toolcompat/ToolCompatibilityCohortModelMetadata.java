package com.setaccio.lab.toolcompat;

/** Public-safe identity and runtime metadata retained for one installed cohort artifact. */
record ToolCompatibilityCohortModelMetadata(
        ToolCompatibilityMetadataField sizeBytes,
        ToolCompatibilityMetadataField familyProvenance,
        ToolCompatibilityMetadataField artifactRuntimeFormat,
        ToolCompatibilityMetadataField quantizationOrPrecision,
        ToolCompatibilityMetadataField templateFingerprint,
        ToolCompatibilityMetadataField defaultSystemPromptFingerprint,
        ToolCompatibilityMetadataField toolCapability,
        ToolCompatibilityMetadataField thinkingMode
) {

    ToolCompatibilityCohortModelMetadata {
        if (sizeBytes == null
                || familyProvenance == null
                || artifactRuntimeFormat == null
                || quantizationOrPrecision == null
                || templateFingerprint == null
                || defaultSystemPromptFingerprint == null
                || toolCapability == null
                || thinkingMode == null) {
            throw new IllegalArgumentException(
                    "every cohort metadata field must be explicitly available or unavailable");
        }
    }

    static ToolCompatibilityCohortModelMetadata unavailable() {
        ToolCompatibilityMetadataField unavailable = ToolCompatibilityMetadataField.unavailable();
        return new ToolCompatibilityCohortModelMetadata(
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable,
                unavailable);
    }
}
