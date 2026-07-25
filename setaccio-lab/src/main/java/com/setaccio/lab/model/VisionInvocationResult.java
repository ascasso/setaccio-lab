package com.setaccio.lab.model;

import java.util.List;

public record VisionInvocationResult(
        VisionInvocationSettings settings,
        String mimeType,
        String promptId,
        String promptVersion,
        String promptSha256,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        List<VisionStructuralCheck> structuralChecks,
        boolean structureComplete,
        boolean success,
        VisionErrorCategory errorCategory,
        String error
) {

    public VisionInvocationResult {
        structuralChecks = structuralChecks == null ? List.of() : List.copyOf(structuralChecks);
    }
}
