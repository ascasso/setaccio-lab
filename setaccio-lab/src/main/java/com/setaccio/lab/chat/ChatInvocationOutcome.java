package com.setaccio.lab.chat;

import java.util.Objects;

public record ChatInvocationOutcome(
        ChatProviderModelIdentity modelIdentity,
        ChatProviderOptionSupport optionSupport,
        String promptId,
        boolean invocationSucceeded,
        String rawResponse,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        ChatInvocationFailureCategory failureCategory,
        String error
) {
    public ChatInvocationOutcome {
        modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        optionSupport = Objects.requireNonNull(optionSupport, "optionSupport must not be null");
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("promptId must not be blank");
        }
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis must not be negative");
        }
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        validateUsage(promptTokens, completionTokens, totalTokens);

        if (invocationSucceeded) {
            if (error != null) {
                throw new IllegalArgumentException("completed invocation must not record an error");
            }
            if (failureCategory != ChatInvocationFailureCategory.NONE
                    && failureCategory != ChatInvocationFailureCategory.EMPTY_RESPONSE) {
                throw new IllegalArgumentException("completed invocation has an invalid failure category");
            }
            if (failureCategory == ChatInvocationFailureCategory.NONE
                    && (rawResponse == null || rawResponse.isBlank())) {
                throw new IllegalArgumentException("successful response must not be blank");
            }
            if (failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE
                    && rawResponse != null && !rawResponse.isBlank()) {
                throw new IllegalArgumentException("empty response category must not contain response text");
            }
        } else {
            if (failureCategory == ChatInvocationFailureCategory.NONE
                    || failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE) {
                throw new IllegalArgumentException("failed invocation has an invalid failure category");
            }
            if (error == null || error.isBlank()) {
                throw new IllegalArgumentException("failed invocation must record an error");
            }
            if (rawResponse != null) {
                throw new IllegalArgumentException("failed invocation must not record a response");
            }
        }
    }

    public boolean successful() {
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
