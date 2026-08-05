package com.setaccio.lab.chat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OllamaChatModelFactoryTest {

    @Test
    void createsAnExplicitNoPullOneAttemptModelAndPropagatesTimeout() {
        OllamaApi ollamaApi = mock(OllamaApi.class);
        AtomicReference<String> capturedBaseUrl = new AtomicReference<>();
        AtomicReference<Duration> capturedTimeout = new AtomicReference<>();
        OllamaChatModelFactory factory = new OllamaChatModelFactory((baseUrl, timeout) -> {
            capturedBaseUrl.set(baseUrl);
            capturedTimeout.set(timeout);
            return ollamaApi;
        });
        ChatGenerationSettings settings = settings();

        ChatModel model = factory.create(
                "http://127.0.0.1:11434",
                ChatInvocationContractTest.identity(),
                settings);

        verifyNoInteractions(ollamaApi);
        assertThat(capturedBaseUrl).hasValue("http://127.0.0.1:11434");
        assertThat(capturedTimeout).hasValue(Duration.ofSeconds(30));
        assertThat(model.getOptions()).isInstanceOfSatisfying(OllamaChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("gemma4:e2b");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getSeed()).isEqualTo(42);
            assertThat(options.getNumPredict()).isEqualTo(128);
        });

        when(ollamaApi.chat(any(OllamaApi.ChatRequest.class)))
                .thenThrow(new IllegalStateException("provider failure"));
        assertThatThrownBy(() -> model.call(new Prompt(new UserMessage("fixture prompt"))))
                .isInstanceOf(RuntimeException.class);
        verify(ollamaApi, times(1)).chat(any(OllamaApi.ChatRequest.class));
    }

    @Test
    void rejectsNonLoopbackEndpointsAndMissingOllamaSeed() {
        OllamaChatModelFactory factory = new OllamaChatModelFactory();

        assertThatThrownBy(() -> factory.create(
                "https://example.com",
                ChatInvocationContractTest.identity(),
                settings()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must be a loopback HTTP URL");
        assertThatThrownBy(() -> factory.create(
                "http://localhost:11434",
                ChatInvocationContractTest.identity(),
                new ChatGenerationSettings(0.0, null, 128, Duration.ofSeconds(30), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seed must be explicit for the Ollama chat adapter");
    }

    private static ChatGenerationSettings settings() {
        return new ChatGenerationSettings(0.0, 42, 128, Duration.ofSeconds(30), 1);
    }
}
