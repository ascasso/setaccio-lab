package com.setaccio.lab.model;

import java.util.List;

public record RunRow(
        String model,
        String input,
        String inputHash,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        boolean success,
        String error,
        String mimeType,
        String promptId,
        String promptVersion,
        String promptSha256,
        Double temperature,
        Integer seed,
        Integer maxTokens,
        List<VisionStructuralCheck> structuralChecks,
        boolean structureComplete,
        VisionErrorCategory errorCategory
) {
    public RunRow {
        structuralChecks = structuralChecks == null ? List.of() : List.copyOf(structuralChecks);
    }

    public static RunRow ok(String model, String input, String hash, long latencyMs,
                            Integer tokensIn, Integer tokensOut, String outputText) {
        return new RunRow(model, input, hash, latencyMs, tokensIn, tokensOut,
                outputText, true, null, null, null, null, null,
                null, null, null, List.of(), false, null);
    }

    public static RunRow fail(String model, String input, String hash, long latencyMs, String error) {
        return new RunRow(model, input, hash, latencyMs, null, null, null, false, error,
                null, null, null, null, null, null, null, List.of(), false, null);
    }

    public static RunRow from(String input, String hash, VisionInvocationResult invocation) {
        VisionInvocationSettings settings = invocation.settings();
        return new RunRow(
                settings.model(),
                input,
                hash,
                invocation.latencyMs(),
                invocation.tokensIn(),
                invocation.tokensOut(),
                invocation.outputText(),
                invocation.success(),
                invocation.error(),
                invocation.mimeType(),
                invocation.promptId(),
                invocation.promptVersion(),
                invocation.promptSha256(),
                settings.temperature(),
                settings.seed(),
                settings.maxTokens(),
                invocation.structuralChecks(),
                invocation.structureComplete(),
                invocation.errorCategory());
    }
}
