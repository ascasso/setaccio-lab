package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import java.util.Objects;

/** Provider-neutral chat-invocation projection retained for one retrieval answer. */
public record RetrievalAnswerInvocationOutcome(
        RetrievalAnswerModelIdentity modelIdentity,
        String promptId,
        String promptSha256,
        boolean invocationSucceeded,
        String answerText,
        String providerResponseId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        ChatInvocationFailureCategory failureCategory
) {

    public RetrievalAnswerInvocationOutcome {
        modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("promptId must not be blank");
        }
        if (promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("promptSha256 must be a full lowercase SHA-256 digest");
        }
        if (providerResponseId != null && !providerResponseId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("providerResponseId must be a safe opaque identifier");
        }
        if (latencyMillis < 0 || attemptCount != 1) {
            throw new IllegalArgumentException("latencyMillis must be non-negative and attemptCount must be exactly one");
        }
        failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        validateUsage(promptTokens, completionTokens, totalTokens);
        if (invocationSucceeded) {
            if (failureCategory != ChatInvocationFailureCategory.NONE
                    && failureCategory != ChatInvocationFailureCategory.EMPTY_RESPONSE) {
                throw new IllegalArgumentException("completed answer invocation has an invalid failure category");
            }
            if (failureCategory == ChatInvocationFailureCategory.NONE
                    && (answerText == null || answerText.isBlank())) {
                throw new IllegalArgumentException("successful answer text must not be blank");
            }
            if (failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE
                    && answerText != null && !answerText.isBlank()) {
                throw new IllegalArgumentException("empty answer outcome must not contain answer text");
            }
        } else if (failureCategory == ChatInvocationFailureCategory.NONE
                || failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE
                || answerText != null) {
            throw new IllegalArgumentException("failed answer invocation has invalid response state");
        }
    }

    boolean successful() {
        return invocationSucceeded && failureCategory == ChatInvocationFailureCategory.NONE;
    }

    private static void validateUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        boolean allMissing = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean allPresent = promptTokens != null && completionTokens != null && totalTokens != null;
        if (!allMissing && !allPresent) {
            throw new IllegalArgumentException("usage token counts must be all present or all absent");
        }
        if (allPresent && (promptTokens < 0 || completionTokens < 0 || totalTokens < 0)) {
            throw new IllegalArgumentException("usage token counts must not be negative");
        }
    }
}
