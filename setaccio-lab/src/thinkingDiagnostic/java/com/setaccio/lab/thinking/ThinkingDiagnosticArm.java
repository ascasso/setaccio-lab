package com.setaccio.lab.thinking;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import java.util.Objects;

/** One pre-registered arm: a model, a named reasoning policy, a boundary, and one output budget. */
public record ThinkingDiagnosticArm(
        String armId,
        ThinkingDiagnosticModelRole modelRole,
        ChatReasoningPolicy reasoningPolicy,
        ThinkingDiagnosticExecutionBoundary executionBoundary,
        int maxOutputTokens,
        boolean measuredProviderDefault
) {
    public ThinkingDiagnosticArm {
        if (armId == null || !armId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("armId must be a lowercase kebab-case identifier");
        }
        modelRole = Objects.requireNonNull(modelRole, "modelRole must not be null");
        reasoningPolicy = Objects.requireNonNull(reasoningPolicy, "reasoningPolicy must not be null");
        executionBoundary = Objects.requireNonNull(
                executionBoundary, "executionBoundary must not be null");
        if ((reasoningPolicy == ChatReasoningPolicy.PROVIDER_DEFAULT) != measuredProviderDefault) {
            throw new IllegalArgumentException(
                    "PROVIDER_DEFAULT is allowed only as an explicitly measured pre-registered condition");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
