package com.setaccio.lab.chat;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

final class OllamaChatInvocation implements ChatInvocation {

    private static final ChatProviderOptionSupport OPTION_SUPPORT = ChatProviderOptionSupport.supportsAll();

    private final ChatModel chatModel;
    private final OllamaChatModelIdentity modelIdentity;
    private final ChatGenerationSettings settings;

    OllamaChatInvocation(
            ChatModel chatModel,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        if (settings.seed() == null) {
            throw new IllegalArgumentException("seed must be explicit for the Ollama chat adapter");
        }
    }

    @Override
    public ChatInvocationOutcome invoke(ChatInvocationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!(request.modelIdentity() instanceof OllamaChatModelIdentity modelIdentity)) {
            throw new IllegalArgumentException("Ollama adapter requires an Ollama model identity");
        }
        if (!this.modelIdentity.equals(modelIdentity)) {
            throw new IllegalArgumentException("request model identity must match the bound Ollama model identity");
        }
        if (!settings.equals(request.settings())) {
            throw new IllegalArgumentException("request settings must match the bound Ollama model settings");
        }

        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelIdentity.requestedModel())
                .temperature(settings.temperature())
                .seed(settings.seed())
                .numPredict(settings.maxOutputTokens())
                .build();
        Prompt prompt = new Prompt(List.of(new UserMessage(request.prompt().text())), options);
        long startedNanos = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(prompt);
            return completed(request, response, elapsedMillis(startedNanos));
        } catch (RuntimeException exception) {
            return failed(request, category(exception), safeMessage(exception), elapsedMillis(startedNanos));
        }
    }

    private static ChatInvocationOutcome completed(
            ChatInvocationRequest request,
            ChatResponse response,
            long latencyMillis
    ) {
        String rawResponse = response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                ? null
                : response.getResult().getOutput().getText();
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        boolean usageAvailable = usage != null && !(usage instanceof EmptyUsage);
        ChatInvocationFailureCategory category = rawResponse == null || rawResponse.isBlank()
                ? ChatInvocationFailureCategory.EMPTY_RESPONSE
                : ChatInvocationFailureCategory.NONE;
        return new ChatInvocationOutcome(
                request.modelIdentity(),
                OPTION_SUPPORT,
                request.prompt().id(),
                true,
                rawResponse,
                null,
                usageAvailable ? usage.getPromptTokens() : null,
                usageAvailable ? usage.getCompletionTokens() : null,
                usageAvailable ? usage.getTotalTokens() : null,
                latencyMillis,
                1,
                category,
                null);
    }

    private static ChatInvocationOutcome failed(
            ChatInvocationRequest request,
            ChatInvocationFailureCategory category,
            String error,
            long latencyMillis
    ) {
        return new ChatInvocationOutcome(
                request.modelIdentity(),
                OPTION_SUPPORT,
                request.prompt().id(),
                false,
                null,
                null,
                null,
                null,
                null,
                latencyMillis,
                1,
                category,
                error);
    }

    private static ChatInvocationFailureCategory category(RuntimeException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ChatModelUnavailableException) {
                return ChatInvocationFailureCategory.MODEL_UNAVAILABLE;
            }
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return ChatInvocationFailureCategory.TIMEOUT;
            }
        }
        return ChatInvocationFailureCategory.PROVIDER_FAILURE;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = null;
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
