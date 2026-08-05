package com.setaccio.lab.chat;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaChatInvocationTest {

    @Test
    void appliesExplicitOllamaOptionsAndRecordsRawResponseUsageAndIdentity() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response("A compact answer.", 12, 4));
        OllamaChatInvocation invocation = invocation(chatModel);

        ChatInvocationOutcome outcome = invocation.invoke(request());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt effectivePrompt = promptCaptor.getValue();
        assertThat(effectivePrompt.getInstructions()).singleElement().satisfies(message ->
                assertThat(message.getText()).isEqualTo("Answer in one sentence."));
        assertThat(effectivePrompt.getOptions()).isInstanceOfSatisfying(OllamaChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("gemma4:e2b");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getSeed()).isEqualTo(42);
            assertThat(options.getNumPredict()).isEqualTo(128);
        });
        assertThat(outcome.modelIdentity()).isEqualTo(ChatInvocationContractTest.identity());
        assertThat(outcome.promptId()).isEqualTo("concise-answer");
        assertThat(outcome.rawResponse()).isEqualTo("A compact answer.");
        assertThat(outcome.promptTokens()).isEqualTo(12);
        assertThat(outcome.completionTokens()).isEqualTo(4);
        assertThat(outcome.totalTokens()).isEqualTo(16);
        assertThat(outcome.attemptCount()).isEqualTo(1);
        assertThat(outcome.failureCategory()).isEqualTo(ChatInvocationFailureCategory.NONE);
        assertThat(outcome.optionSupport().supported())
                .containsExactlyInAnyOrder(ChatGenerationOption.values());
        assertThat(outcome.optionSupport().unsupportedReasons()).isEmpty();
        assertThat(outcome.invocationSucceeded()).isTrue();
        assertThat(outcome.successful()).isTrue();
        assertThat(outcome.error()).isNull();
    }

    @Test
    void classifiesAProviderCompletedEmptyResponseWithoutInventingUsage() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of()));

        ChatInvocationOutcome outcome = invocation(chatModel).invoke(request());

        assertThat(outcome.invocationSucceeded()).isTrue();
        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.rawResponse()).isNull();
        assertThat(outcome.promptTokens()).isNull();
        assertThat(outcome.completionTokens()).isNull();
        assertThat(outcome.totalTokens()).isNull();
        assertThat(outcome.failureCategory()).isEqualTo(ChatInvocationFailureCategory.EMPTY_RESPONSE);
        assertThat(outcome.error()).isNull();
    }

    @Test
    void classifiesUnavailableTimeoutAndProviderFailures() {
        assertFailure(
                new ChatModelUnavailableException("model digest is not installed"),
                ChatInvocationFailureCategory.MODEL_UNAVAILABLE,
                "model digest is not installed");
        assertFailure(
                new IllegalStateException("outer", new HttpTimeoutException("request timed out")),
                ChatInvocationFailureCategory.TIMEOUT,
                "request timed out");
        assertFailure(
                new IllegalStateException("provider failed"),
                ChatInvocationFailureCategory.PROVIDER_FAILURE,
                "provider failed");
    }

    @Test
    void rejectsAnotherProviderIdentityBeforeCallingOllama() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatInvocationRequest request = new ChatInvocationRequest(
                new AlternateProviderIdentity("anthropic", "requested", "effective"),
                new ChatInvocationPrompt("prompt", "Text"),
                settings());

        assertThatThrownBy(() -> invocation(chatModel).invoke(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ollama adapter requires an Ollama model identity");
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void rejectsSettingsThatDifferFromTheBoundNoPullModel() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatInvocationRequest request = new ChatInvocationRequest(
                ChatInvocationContractTest.identity(),
                new ChatInvocationPrompt("prompt", "Text"),
                new ChatGenerationSettings(0.0, 43, 128, Duration.ofSeconds(30), 1));

        assertThatThrownBy(() -> invocation(chatModel).invoke(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request settings must match the bound Ollama model settings");
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    private static void assertFailure(
            RuntimeException exception,
            ChatInvocationFailureCategory expectedCategory,
            String expectedMessage
    ) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenThrow(exception);

        ChatInvocationOutcome outcome = invocation(chatModel).invoke(request());

        verify(chatModel).call(org.mockito.ArgumentMatchers.any(Prompt.class));
        assertThat(outcome.invocationSucceeded()).isFalse();
        assertThat(outcome.successful()).isFalse();
        assertThat(outcome.attemptCount()).isEqualTo(1);
        assertThat(outcome.failureCategory()).isEqualTo(expectedCategory);
        assertThat(outcome.error()).isEqualTo(expectedMessage);
    }

    private static ChatInvocationRequest request() {
        return new ChatInvocationRequest(
                ChatInvocationContractTest.identity(),
                new ChatInvocationPrompt("concise-answer", "Answer in one sentence."),
                settings());
    }

    private static ChatGenerationSettings settings() {
        return new ChatGenerationSettings(0.0, 42, 128, Duration.ofSeconds(30), 1);
    }

    private static OllamaChatInvocation invocation(ChatModel chatModel) {
        return new OllamaChatInvocation(chatModel, ChatInvocationContractTest.identity(), settings());
    }

    private static ChatResponse response(String text, int promptTokens, int completionTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }

    private record AlternateProviderIdentity(
            String providerId,
            String requestedModel,
            String effectiveModel
    ) implements ChatProviderModelIdentity {
    }
}
