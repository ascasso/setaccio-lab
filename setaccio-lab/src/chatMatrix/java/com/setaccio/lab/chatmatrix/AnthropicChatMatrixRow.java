package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import java.util.Objects;

/** Ignored raw-evidence row. Its response text is never copied to a public report. */
record AnthropicChatMatrixRow(
        int sequence,
        int repetition,
        String promptId,
        String promptSha256,
        ChatGenerationSettings generationSettings,
        AnthropicChatModelIdentity modelIdentity,
        ChatProviderOptionSupport optionSupport,
        boolean invocationSucceeded,
        String rawResponse,
        String providerResponseId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        ChatInvocationFailureCategory failureCategory,
        String error
) {

    static AnthropicChatMatrixRow from(
            int sequence,
            int repetition,
            ChatPromptCase prompt,
            ChatGenerationSettings settings,
            AnthropicChatModelIdentity requestedIdentity,
            ChatInvocationOutcome outcome
    ) {
        if (sequence < 1 || repetition < 1 || prompt == null || settings == null || requestedIdentity == null || outcome == null) {
            throw new IllegalArgumentException("Anthropic chat matrix row inputs must be complete");
        }
        if (!prompt.id().equals(outcome.promptId()) || !(outcome.modelIdentity() instanceof AnthropicChatModelIdentity effectiveIdentity)
                || !requestedIdentity.providerId().equals(effectiveIdentity.providerId())
                || !requestedIdentity.requestedModel().equals(effectiveIdentity.requestedModel())) {
            throw new IllegalArgumentException("Anthropic invocation outcome does not match the locked prompt/model identity");
        }
        return new AnthropicChatMatrixRow(
                sequence, repetition, prompt.id(), prompt.sha256(), settings, effectiveIdentity, outcome.optionSupport(),
                outcome.invocationSucceeded(), outcome.rawResponse(), outcome.providerResponseId(),
                outcome.promptTokens(), outcome.completionTokens(), outcome.totalTokens(), outcome.latencyMillis(),
                outcome.attemptCount(), outcome.failureCategory(), outcome.error());
    }

    AnthropicChatMatrixRow {
        if (sequence < 1 || repetition < 1 || promptId == null || promptId.isBlank()
                || promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Anthropic row schedule identity must be complete");
        }
        generationSettings = Objects.requireNonNull(generationSettings, "generationSettings must not be null");
        modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        optionSupport = Objects.requireNonNull(optionSupport, "optionSupport must not be null");
        failureCategory = Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        if (generationSettings.seed() != null || optionSupport.supports(com.setaccio.lab.chat.ChatGenerationOption.SEED)) {
            throw new IllegalArgumentException("Anthropic row must record unsupported seed semantics");
        }
        if (attemptCount != 1 || latencyMillis < 0) {
            throw new IllegalArgumentException("Anthropic row must record one non-negative-latency attempt");
        }
        if (providerResponseId != null && !providerResponseId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Anthropic response ID must be a safe opaque identifier");
        }
        boolean usageAbsent = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean usagePresent = promptTokens != null && completionTokens != null && totalTokens != null;
        if (!usageAbsent && !usagePresent) {
            throw new IllegalArgumentException("Anthropic row usage must be complete or absent");
        }
        if (usagePresent && (promptTokens < 0 || completionTokens < 0 || totalTokens < 0)) {
            throw new IllegalArgumentException("Anthropic row usage must not be negative");
        }
        if (invocationSucceeded) {
            if (failureCategory == ChatInvocationFailureCategory.NONE && (rawResponse == null || rawResponse.isBlank())) {
                throw new IllegalArgumentException("successful Anthropic row must contain a response");
            }
            if (failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE && rawResponse != null && !rawResponse.isBlank()) {
                throw new IllegalArgumentException("empty Anthropic row must not contain a response");
            }
            if (failureCategory != ChatInvocationFailureCategory.NONE
                    && failureCategory != ChatInvocationFailureCategory.EMPTY_RESPONSE || error != null) {
                throw new IllegalArgumentException("completed Anthropic row outcome is inconsistent");
            }
        } else if (failureCategory == ChatInvocationFailureCategory.NONE
                || failureCategory == ChatInvocationFailureCategory.EMPTY_RESPONSE
                || rawResponse != null || error == null || error.isBlank()) {
            throw new IllegalArgumentException("failed Anthropic row outcome is inconsistent");
        }
    }
}
