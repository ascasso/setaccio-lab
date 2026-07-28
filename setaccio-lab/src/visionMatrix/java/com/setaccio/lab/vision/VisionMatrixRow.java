package com.setaccio.lab.vision;

import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.model.VisionStructuralCheck;
import java.util.List;

public record VisionMatrixRow(
        int sequence,
        String model,
        String caseId,
        int repetition,
        VisionInvocationSettings invocationSettings,
        String mimeType,
        String inputBlake3,
        String promptId,
        String promptVersion,
        String promptSha256,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        List<VisionStructuralCheck> structuralChecks,
        boolean structureComplete,
        boolean invocationSuccess,
        VisionErrorCategory errorCategory,
        String error
) {

    public VisionMatrixRow {
        structuralChecks = structuralChecks == null ? List.of() : List.copyOf(structuralChecks);
    }

    static VisionMatrixRow from(
            int sequence,
            String caseId,
            int repetition,
            String inputBlake3,
            VisionInvocationResult invocation) {
        return new VisionMatrixRow(
                sequence,
                invocation.settings().model(),
                caseId,
                repetition,
                invocation.settings(),
                invocation.mimeType(),
                inputBlake3,
                invocation.promptId(),
                invocation.promptVersion(),
                invocation.promptSha256(),
                invocation.latencyMs(),
                invocation.tokensIn(),
                invocation.tokensOut(),
                invocation.outputText(),
                invocation.structuralChecks(),
                invocation.structureComplete(),
                invocation.success(),
                invocation.errorCategory(),
                safeError(invocation.errorCategory(), invocation.error()));
    }

    private static String safeError(
            VisionErrorCategory category,
            String error) {
        if (category == null) {
            return error == null ? null : "Vision invocation failed";
        }
        return switch (category) {
            case INVALID_INPUT -> "Vision input was invalid";
            case MODEL_UNAVAILABLE -> "Vision model was unavailable";
            case EMPTY_RESPONSE -> "Vision model returned an empty response";
            case PROVIDER_FAILURE -> "Vision provider invocation failed";
        };
    }
}
