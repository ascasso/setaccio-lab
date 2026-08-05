package com.setaccio.lab.chat;

import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Creates the narrowly configured, one-attempt Anthropic model used by the portability matrix.
 * It never reads credentials from the environment itself and never makes a request while building.
 */
public final class AnthropicChatModelFactory {

    static final String ANTHROPIC_API_BASE_URL = "https://api.anthropic.com";

    public ChatModel create(
            String apiKey,
            AnthropicChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        requireApiKey(apiKey);
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (settings.seed() != null) {
            throw new IllegalArgumentException("seed is unsupported for the Anthropic chat adapter");
        }
        if (settings.maxAttempts() != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1 for controlled Anthropic chat");
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .apiKey(apiKey)
                .baseUrl(ANTHROPIC_API_BASE_URL)
                .model(modelIdentity.requestedModel())
                .maxTokens(settings.maxOutputTokens())
                .temperature(settings.temperature())
                .timeout(settings.requestTimeout())
                .maxRetries(0)
                .build();
        return AnthropicChatModel.builder()
                .options(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    public ChatInvocation createInvocation(
            String apiKey,
            AnthropicChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        return new AnthropicChatInvocation(create(apiKey, modelIdentity, settings), modelIdentity, settings);
    }

    private static void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key must be supplied through local configuration");
        }
        if (!apiKey.equals(apiKey.strip())) {
            throw new IllegalArgumentException("Anthropic API key must not have surrounding whitespace");
        }
    }
}
