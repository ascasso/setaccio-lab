package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;

record ChatMatrixRow(
        int sequence,
        int repetition,
        int seed,
        String promptId,
        String promptSha256,
        ChatGenerationSettings generationSettings,
        ChatProviderOptionSupport optionSupport,
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

    static ChatMatrixRow from(
            ChatMatrixScheduleEntry schedule,
            ChatGenerationSettings settings,
            OllamaChatModelIdentity expectedModelIdentity,
            ChatInvocationOutcome outcome
    ) {
        if (schedule == null || settings == null || expectedModelIdentity == null || outcome == null) {
            throw new IllegalArgumentException("Chat matrix row inputs must be complete");
        }
        if (!schedule.promptId().equals(outcome.promptId())) {
            throw new IllegalArgumentException("Chat invocation outcome does not match its scheduled prompt");
        }
        if (!expectedModelIdentity.equals(outcome.modelIdentity())) {
            throw new IllegalArgumentException("Chat invocation outcome does not match the installed model identity");
        }
        return new ChatMatrixRow(
                schedule.sequence(),
                schedule.repetition(),
                schedule.seed(),
                schedule.promptId(),
                schedule.promptSha256(),
                settings,
                outcome.optionSupport(),
                outcome.invocationSucceeded(),
                outcome.rawResponse(),
                outcome.promptTokens(),
                outcome.completionTokens(),
                outcome.totalTokens(),
                outcome.latencyMillis(),
                outcome.attemptCount(),
                outcome.failureCategory(),
                safeError(outcome.failureCategory()));
    }

    boolean successful() {
        return invocationSucceeded && failureCategory == ChatInvocationFailureCategory.NONE;
    }

    private static String safeError(ChatInvocationFailureCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case MODEL_UNAVAILABLE -> "Ollama chat model was unavailable";
            case TIMEOUT -> "Ollama chat invocation timed out";
            case AUTHENTICATION, RATE_LIMIT, PROVIDER_FAILURE -> "Ollama chat provider invocation failed";
            case NONE, EMPTY_RESPONSE -> null;
        };
    }
}
