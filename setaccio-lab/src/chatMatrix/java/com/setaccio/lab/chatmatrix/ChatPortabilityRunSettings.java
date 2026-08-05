package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationOption;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import java.util.List;
import java.util.Objects;

/** The protocol fields that must be visible before a provider run can be compared. */
record ChatPortabilityRunSettings(
        String promptCatalogId,
        String promptCatalogVersion,
        String promptCatalogSha256,
        List<ChatPromptIdentity> orderedPromptIdentities,
        int repetitions,
        int plannedCallCount,
        double temperature,
        int maxOutputTokens,
        long timeoutMillis,
        int maxAttempts,
        List<Integer> seeds,
        ChatProviderOptionSupport optionSupport
) {

    ChatPortabilityRunSettings {
        promptCatalogId = requireText(promptCatalogId, "promptCatalogId");
        promptCatalogVersion = requireText(promptCatalogVersion, "promptCatalogVersion");
        if (promptCatalogSha256 == null || !promptCatalogSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("promptCatalogSha256 must be a full lowercase SHA-256 digest");
        }
        orderedPromptIdentities = orderedPromptIdentities == null ? List.of() : List.copyOf(orderedPromptIdentities);
        if (orderedPromptIdentities.isEmpty()) {
            throw new IllegalArgumentException("orderedPromptIdentities must not be empty");
        }
        if (repetitions < 1 || plannedCallCount != repetitions * orderedPromptIdentities.size()) {
            throw new IllegalArgumentException("plannedCallCount must exactly cover every prompt repetition");
        }
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (maxOutputTokens < 1 || timeoutMillis < 1 || maxAttempts != 1) {
            throw new IllegalArgumentException("token, timeout, and one-attempt settings must be valid");
        }
        optionSupport = Objects.requireNonNull(optionSupport, "optionSupport must not be null");
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        boolean seedSupported = optionSupport.supports(ChatGenerationOption.SEED);
        if (seedSupported && (seeds.size() != repetitions || seeds.stream().anyMatch(seed -> seed == null || seed < 0))) {
            throw new IllegalArgumentException("supported seed settings must include one non-negative value per repetition");
        }
        if (!seedSupported && !seeds.isEmpty()) {
            throw new IllegalArgumentException("unsupported seed settings must not record simulated seed values");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
