package com.setaccio.lab.evaluation;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;

public final class LocalFactCheckJudgeModelFactory {

    private final OllamaApiFactory ollamaApiFactory;

    public LocalFactCheckJudgeModelFactory() {
        this(LocalFactCheckJudgeModelFactory::createOllamaApi);
    }

    LocalFactCheckJudgeModelFactory(OllamaApiFactory ollamaApiFactory) {
        this.ollamaApiFactory = Objects.requireNonNull(ollamaApiFactory, "ollamaApiFactory must not be null");
    }

    public ChatModel create(String baseUrl, LocalFactCheckJudgeSettings settings) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(settings, "settings must not be null");
        return create(ollamaApiFactory.create(baseUrl, settings.timeout()), settings);
    }

    public OllamaApi createApi(String baseUrl, Duration timeout) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return ollamaApiFactory.create(baseUrl, timeout);
    }

    ChatModel create(OllamaApi ollamaApi, LocalFactCheckJudgeSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        return OllamaChatModelFactory.createNoPullModel(
                ollamaApi,
                settings.ollamaOptions(),
                settings.timeout(),
                settings.maxAttempts());
    }

    private static OllamaApi createOllamaApi(String baseUrl, Duration timeout) {
        return new OllamaChatModelFactory().createApi(baseUrl, timeout);
    }

    public static void requireLoopbackBaseUrl(String baseUrl) {
        OllamaChatModelFactory.requireLoopbackBaseUrl(baseUrl);
    }

    @FunctionalInterface
    interface OllamaApiFactory {
        OllamaApi create(String baseUrl, Duration timeout);
    }
}
