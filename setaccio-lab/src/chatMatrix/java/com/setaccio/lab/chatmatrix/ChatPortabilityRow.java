package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatEvidenceModelIdentity;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import java.util.Objects;

/** A raw-output-free row projection used only by the offline portability report. */
record ChatPortabilityRow(
        int sequence,
        int repetition,
        Integer seed,
        String promptId,
        String promptSha256,
        ChatEvidenceModelIdentity modelIdentity,
        boolean invocationSucceeded,
        boolean structuralOutputPresent,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        ChatInvocationFailureCategory failureCategory
) {

    ChatPortabilityRow {
        if (sequence < 1 || repetition < 1 || (seed != null && seed < 0)) {
            throw new IllegalArgumentException("row sequence, repetition, and seed must be valid");
        }
        if (promptId == null || promptId.isBlank()
                || promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("row prompt identity must be complete");
        }
        modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        if (latencyMillis < 0 || attemptCount != 1) {
            throw new IllegalArgumentException("row latency and attempt count must be valid");
        }
        failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        boolean usageAbsent = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean usagePresent = promptTokens != null && completionTokens != null && totalTokens != null;
        if (!usageAbsent && !usagePresent) {
            throw new IllegalArgumentException("row usage must be complete or absent");
        }
        if (usagePresent && (promptTokens < 0 || completionTokens < 0 || totalTokens < 0)) {
            throw new IllegalArgumentException("row usage must not be negative");
        }
        if (invocationSucceeded != (failureCategory == ChatInvocationFailureCategory.NONE
                || failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE)) {
            throw new IllegalArgumentException("row invocation outcome is inconsistent");
        }
        if (structuralOutputPresent != (failureCategory == ChatInvocationFailureCategory.NONE)) {
            throw new IllegalArgumentException("row structural output state is inconsistent");
        }
    }
}
