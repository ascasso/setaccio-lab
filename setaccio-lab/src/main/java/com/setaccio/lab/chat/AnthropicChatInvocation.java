package com.setaccio.lab.chat;

import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

final class AnthropicChatInvocation implements ChatInvocation {

    private static final ChatProviderOptionSupport OPTION_SUPPORT = new ChatProviderOptionSupport(
            EnumSet.of(ChatGenerationOption.TEMPERATURE, ChatGenerationOption.MAX_OUTPUT_TOKENS),
            Map.of(ChatGenerationOption.SEED,
                    "Anthropic Messages API and Spring AI AnthropicChatOptions do not expose a seed option"));

    private final ChatModel chatModel;
    private final AnthropicChatModelIdentity modelIdentity;
    private final ChatGenerationSettings settings;

    AnthropicChatInvocation(
            ChatModel chatModel,
            AnthropicChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        if (settings.seed() != null) {
            throw new IllegalArgumentException("seed is unsupported for the Anthropic chat adapter");
        }
    }

    @Override
    public ChatInvocationOutcome invoke(ChatInvocationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!(request.modelIdentity() instanceof AnthropicChatModelIdentity requestIdentity)) {
            throw new IllegalArgumentException("Anthropic adapter requires an Anthropic model identity");
        }
        if (!this.modelIdentity.equals(requestIdentity)) {
            throw new IllegalArgumentException("request model identity must match the bound Anthropic model identity");
        }
        if (!settings.equals(request.settings())) {
            throw new IllegalArgumentException("request settings must match the bound Anthropic model settings");
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(modelIdentity.requestedModel())
                .maxTokens(settings.maxOutputTokens())
                .temperature(settings.temperature())
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage(request.prompt().text())), options);
        long startedNanos = System.nanoTime();
        try {
            return completed(request, chatModel.call(prompt), elapsedMillis(startedNanos));
        } catch (RuntimeException exception) {
            return failed(request, category(exception), elapsedMillis(startedNanos));
        }
    }

    private static ChatInvocationOutcome completed(ChatInvocationRequest request, ChatResponse response, long latencyMillis) {
        String rawResponse = response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? null : response.getResult().getOutput().getText();
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        boolean usageAvailable = usage != null && !(usage instanceof EmptyUsage);
        ChatInvocationFailureCategory category = rawResponse == null || rawResponse.isBlank()
                ? ChatInvocationFailureCategory.EMPTY_RESPONSE : ChatInvocationFailureCategory.NONE;
        AnthropicChatModelIdentity effectiveIdentity = ((AnthropicChatModelIdentity) request.modelIdentity())
                .withEffectiveModel(metadata == null ? null : metadata.getModel());
        return new ChatInvocationOutcome(
                effectiveIdentity, OPTION_SUPPORT, request.prompt().id(), true, rawResponse,
                safeOpaqueId(metadata == null ? null : metadata.getId()),
                usageAvailable ? usage.getPromptTokens() : null,
                usageAvailable ? usage.getCompletionTokens() : null,
                usageAvailable ? usage.getTotalTokens() : null,
                latencyMillis, 1, category, null);
    }

    private static ChatInvocationOutcome failed(
            ChatInvocationRequest request, ChatInvocationFailureCategory category, long latencyMillis
    ) {
        return new ChatInvocationOutcome(
                request.modelIdentity(), OPTION_SUPPORT, request.prompt().id(), false, null, null,
                null, null, null, latencyMillis, 1, category, safeError(category));
    }

    private static ChatInvocationFailureCategory category(RuntimeException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof UnauthorizedException || current instanceof NoCredentialsException) {
                return ChatInvocationFailureCategory.AUTHENTICATION;
            }
            if (current instanceof RateLimitException) {
                return ChatInvocationFailureCategory.RATE_LIMIT;
            }
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return ChatInvocationFailureCategory.TIMEOUT;
            }
        }
        return ChatInvocationFailureCategory.PROVIDER_FAILURE;
    }

    private static String safeOpaqueId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,128}") ? id : null;
    }

    private static String safeError(ChatInvocationFailureCategory category) {
        return switch (category) {
            case AUTHENTICATION -> "Anthropic authentication failed";
            case RATE_LIMIT -> "Anthropic rate limit exceeded";
            case TIMEOUT -> "Anthropic request timed out";
            default -> "Anthropic provider request failed";
        };
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
