package com.setaccio.lab.chat;

import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnthropicChatInvocationTest {

    @Test
    void mapsOnlySupportedOptionsAndRecordsSafeResponseMetadata() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response("A compact answer.", "msg_01ABC", "claude-haiku-4-5-20251001", 12, 4));

        ChatInvocationOutcome outcome = invocation(chatModel).invoke(request());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getOptions()).isInstanceOfSatisfying(AnthropicChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("claude-haiku-4-5-20251001");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getMaxTokens()).isEqualTo(128);
        });
        assertThat(outcome.modelIdentity()).isEqualTo(identity());
        assertThat(outcome.providerResponseId()).isEqualTo("msg_01ABC");
        assertThat(outcome.rawResponse()).isEqualTo("A compact answer.");
        assertThat(outcome.promptTokens()).isEqualTo(12);
        assertThat(outcome.completionTokens()).isEqualTo(4);
        assertThat(outcome.totalTokens()).isEqualTo(16);
        assertThat(outcome.optionSupport().supported()).containsExactlyInAnyOrder(
                ChatGenerationOption.TEMPERATURE, ChatGenerationOption.MAX_OUTPUT_TOKENS);
        assertThat(outcome.optionSupport().unsupportedReasons()).containsKey(ChatGenerationOption.SEED);
        assertThat(outcome.successful()).isTrue();
    }

    @Test
    void leavesUsageAndUnsafeResponseMetadataAbsent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response("Answer", "id with spaces", "", null, null));

        ChatInvocationOutcome outcome = invocation(chatModel).invoke(request());

        assertThat(outcome.providerResponseId()).isNull();
        assertThat(outcome.promptTokens()).isNull();
        assertThat(outcome.completionTokens()).isNull();
        assertThat(outcome.totalTokens()).isNull();
        assertThat(outcome.modelIdentity()).isEqualTo(identity());
    }

    @Test
    void classifiesAuthenticationRateLimitTimeoutAndProviderFailuresWithoutLeakingErrorDetails() {
        assertFailure(mock(UnauthorizedException.class), ChatInvocationFailureCategory.AUTHENTICATION,
                "Anthropic authentication failed");
        assertFailure(mock(RateLimitException.class), ChatInvocationFailureCategory.RATE_LIMIT,
                "Anthropic rate limit exceeded");
        assertFailure(new IllegalStateException(new HttpTimeoutException("https://private.example/token")), ChatInvocationFailureCategory.TIMEOUT,
                "Anthropic request timed out");
        assertFailure(new IllegalStateException("Authorization: secret"), ChatInvocationFailureCategory.PROVIDER_FAILURE,
                "Anthropic provider request failed");
    }

    @Test
    void rejectsSeedAndAnotherProviderBeforeCallingAnthropic() {
        ChatModel chatModel = mock(ChatModel.class);
        assertThatThrownBy(() -> new AnthropicChatInvocation(chatModel, identity(),
                new ChatGenerationSettings(0.0, 42, 128, Duration.ofSeconds(30), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("seed is unsupported for the Anthropic chat adapter");

        ChatInvocationRequest ollamaRequest = new ChatInvocationRequest(
                ChatInvocationContractTest.identity(), new ChatInvocationPrompt("prompt", "Text"), settings());
        assertThatThrownBy(() -> invocation(chatModel).invoke(ollamaRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic adapter requires an Anthropic model identity");
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.any(Prompt.class));
    }

    @Test
    void createsOneAttemptModelWithoutReadingCredentialsOrMakingANetworkRequest() {
        ChatModel chatModel = new AnthropicChatModelFactory().create("test-local-key", identity(), settings());

        assertThat(chatModel).isInstanceOfSatisfying(AnthropicChatModel.class, model -> {
            assertThat(model.getOptions().getBaseUrl()).isEqualTo(AnthropicChatModelFactory.ANTHROPIC_API_BASE_URL);
            assertThat(model.getOptions().getTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(model.getOptions().getMaxRetries()).isZero();
        });
        assertThatThrownBy(() -> new AnthropicChatModelFactory().create("", identity(), settings()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic API key must be supplied through local configuration");
    }

    private static void assertFailure(RuntimeException exception, ChatInvocationFailureCategory category, String error) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenThrow(exception);

        ChatInvocationOutcome outcome = invocation(chatModel).invoke(request());

        assertThat(outcome.invocationSucceeded()).isFalse();
        assertThat(outcome.failureCategory()).isEqualTo(category);
        assertThat(outcome.error()).isEqualTo(error);
        assertThat(outcome.rawResponse()).isNull();
        assertThat(outcome.providerResponseId()).isNull();
        assertThat(outcome.attemptCount()).isEqualTo(1);
    }

    private static AnthropicChatInvocation invocation(ChatModel chatModel) {
        return new AnthropicChatInvocation(chatModel, identity(), settings());
    }

    private static ChatInvocationRequest request() {
        return new ChatInvocationRequest(identity(), new ChatInvocationPrompt("concise-answer", "Answer in one sentence."), settings());
    }

    private static AnthropicChatModelIdentity identity() {
        return new AnthropicChatModelIdentity("anthropic", "claude-haiku-4-5-20251001",
                "claude-haiku-4-5-20251001", true);
    }

    private static ChatGenerationSettings settings() {
        return new ChatGenerationSettings(0.0, null, 128, Duration.ofSeconds(30), 1);
    }

    private static ChatResponse response(String text, String responseId, String model, Integer inputTokens, Integer outputTokens) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().id(responseId).model(model);
        if (inputTokens != null && outputTokens != null) {
            metadata.usage(new DefaultUsage(inputTokens, outputTokens));
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
    }
}
