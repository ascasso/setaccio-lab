package com.setaccio.lab.thinking;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import java.util.Objects;

/** One pre-registered arm: a model, an explicit reasoning policy, and one output budget. */
public record ThinkingDiagnosticArm(
        String armId,
        ThinkingDiagnosticModelRole modelRole,
        ChatReasoningPolicy reasoningPolicy,
        int maxOutputTokens
) {
    public ThinkingDiagnosticArm {
        if (armId == null || !armId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("armId must be a lowercase kebab-case identifier");
        }
        modelRole = Objects.requireNonNull(modelRole, "modelRole must not be null");
        reasoningPolicy = Objects.requireNonNull(reasoningPolicy, "reasoningPolicy must not be null");
        if (reasoningPolicy == ChatReasoningPolicy.PROVIDER_DEFAULT) {
            throw new IllegalArgumentException(
                    "every diagnostic arm must state an explicit reasoning policy");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
