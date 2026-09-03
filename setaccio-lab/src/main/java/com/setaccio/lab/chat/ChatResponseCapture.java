package com.setaccio.lab.chat;

import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * One provider response, recorded as separate dimensions.
 *
 * <p>Assistant content and reasoning are captured as two independent fields. They are never
 * concatenated, substituted for one another, or merged: a response whose reasoning is populated
 * and whose content is empty is recorded as exactly that, not as a non-empty response.
 *
 * <p>Both chat boundaries build this through the same factory so the two paths cannot drift.
 */
public record ChatResponseCapture(
        String content,
        String thinking,
        ChatThinkingPresence thinkingPresence,
        String finishReason,
        Integer evaluatedOutputTokens,
        ChatReasoningPolicy requestedReasoningPolicy,
        ChatReasoningSupport reasoningPolicySupport
) {

    /**
     * The message-property key Spring AI's {@code OllamaChatModel} uses for the Ollama
     * {@code message.thinking} field. Spring AI keeps its own constant private.
     */
    public static final String THINKING_KEY = "thinking";

    public ChatResponseCapture {
        thinkingPresence = Objects.requireNonNull(thinkingPresence, "thinkingPresence must not be null");
        requestedReasoningPolicy = Objects.requireNonNull(
                requestedReasoningPolicy, "requestedReasoningPolicy must not be null");
        reasoningPolicySupport = Objects.requireNonNull(
                reasoningPolicySupport, "reasoningPolicySupport must not be null");
        thinking = blankToNull(thinking);
        finishReason = blankToNull(finishReason);
        if (evaluatedOutputTokens != null && evaluatedOutputTokens < 0) {
            throw new IllegalArgumentException("evaluatedOutputTokens must not be negative");
        }
        if ((thinkingPresence == ChatThinkingPresence.PRESENT) != (thinking != null)) {
            throw new IllegalArgumentException(
                    "thinkingPresence PRESENT must accompany non-blank thinking text, and only that");
        }
        if (thinkingPresence == ChatThinkingPresence.UNAVAILABLE && content != null) {
            throw new IllegalArgumentException("unavailable capture must not record content");
        }
        if (requestedReasoningPolicy == ChatReasoningPolicy.PROVIDER_DEFAULT
                != (reasoningPolicySupport == ChatReasoningSupport.NOT_REQUESTED)) {
            throw new IllegalArgumentException(
                    "NOT_REQUESTED support must accompany PROVIDER_DEFAULT policy, and only that");
        }
    }

    /** True when the provider returned reasoning but no visible assistant content. */
    public boolean thinkingWithoutContent() {
        return thinkingPresence == ChatThinkingPresence.PRESENT && (content == null || content.isBlank());
    }

    public static ChatResponseCapture from(
            ChatResponse response,
            ChatReasoningPolicy requestedReasoningPolicy,
            ChatReasoningSupport reasoningPolicySupport
    ) {
        Generation generation = response == null ? null : response.getResult();
        AssistantMessage assistant = generation == null ? null : generation.getOutput();
        if (assistant == null) {
            return unavailable(requestedReasoningPolicy, reasoningPolicySupport);
        }
        String thinking = blankToNull(thinkingProperty(assistant.getMetadata()));
        ChatGenerationMetadata generationMetadata = generation.getMetadata();
        return new ChatResponseCapture(
                assistant.getText(),
                thinking,
                thinking == null ? ChatThinkingPresence.ABSENT : ChatThinkingPresence.PRESENT,
                generationMetadata == null ? null : generationMetadata.getFinishReason(),
                evaluatedOutputTokens(response),
                requestedReasoningPolicy,
                reasoningPolicySupport);
    }

    public static ChatResponseCapture unavailable(
            ChatReasoningPolicy requestedReasoningPolicy,
            ChatReasoningSupport reasoningPolicySupport
    ) {
        return new ChatResponseCapture(
                null,
                null,
                ChatThinkingPresence.UNAVAILABLE,
                null,
                null,
                requestedReasoningPolicy,
                reasoningPolicySupport);
    }

    private static String thinkingProperty(Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }
        Object value = properties.get(THINKING_KEY);
        return value instanceof String text ? text : null;
    }

    private static Integer evaluatedOutputTokens(ChatResponse response) {
        ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        if (usage == null || usage instanceof EmptyUsage) {
            return null;
        }
        return usage.getCompletionTokens();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
