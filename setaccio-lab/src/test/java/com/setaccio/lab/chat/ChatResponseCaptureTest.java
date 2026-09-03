package com.setaccio.lab.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ChatResponseCaptureTest {

    @Test
    void capturesPopulatedThinkingAlongsideNonEmptyContentWithoutMerging() {
        ChatResponseCapture capture = ChatResponseCapture.from(
                response("yes", "step one, step two", "stop", 11, 7),
                ChatReasoningPolicy.ENABLED,
                ChatReasoningSupport.APPLIED);

        assertThat(capture.content()).isEqualTo("yes");
        assertThat(capture.thinking()).isEqualTo("step one, step two");
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.PRESENT);
        assertThat(capture.content()).doesNotContain("step one");
        assertThat(capture.thinking()).doesNotContain("yes");
        assertThat(capture.thinkingWithoutContent()).isFalse();
    }

    @Test
    void capturesPopulatedThinkingWithEmptyContentAsItsOwnShape() {
        ChatResponseCapture capture = ChatResponseCapture.from(
                response("", "a long private reasoning trace", "length", 11, 64),
                ChatReasoningPolicy.ENABLED,
                ChatReasoningSupport.APPLIED);

        assertThat(capture.content()).isEmpty();
        assertThat(capture.thinking()).isEqualTo("a long private reasoning trace");
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.PRESENT);
        assertThat(capture.thinkingWithoutContent()).isTrue();
        assertThat(capture.evaluatedOutputTokens()).isEqualTo(64);
        assertThat(capture.finishReason()).isEqualTo("length");
    }

    @Test
    void recordsAbsentThinkingWhenTheResponseCarriesNone() {
        ChatResponseCapture capture = ChatResponseCapture.from(
                response("no", null, "stop", 11, 2),
                ChatReasoningPolicy.DISABLED,
                ChatReasoningSupport.APPLIED);

        assertThat(capture.thinking()).isNull();
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.ABSENT);
        assertThat(capture.thinkingWithoutContent()).isFalse();
    }

    @Test
    void treatsBlankThinkingAsAbsentRatherThanPresent() {
        ChatResponseCapture capture = ChatResponseCapture.from(
                response("no", "   ", "stop", 11, 2),
                ChatReasoningPolicy.DISABLED,
                ChatReasoningSupport.APPLIED);

        assertThat(capture.thinking()).isNull();
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.ABSENT);
    }

    @Test
    void capturesFinishReasonAndEvaluatedOutputTokensWhenAvailable() {
        ChatResponseCapture withMetadata = ChatResponseCapture.from(
                response("ok", null, "length", 12, 256),
                ChatReasoningPolicy.DISABLED,
                ChatReasoningSupport.APPLIED);
        assertThat(withMetadata.finishReason()).isEqualTo("length");
        assertThat(withMetadata.evaluatedOutputTokens()).isEqualTo(256);

        ChatResponse withoutMetadata = new ChatResponse(
                List.of(new Generation(new AssistantMessage("ok"))),
                ChatResponseMetadata.builder().build());
        ChatResponseCapture bare = ChatResponseCapture.from(
                withoutMetadata, ChatReasoningPolicy.DISABLED, ChatReasoningSupport.APPLIED);
        assertThat(bare.finishReason()).isNull();
        assertThat(bare.evaluatedOutputTokens()).isNull();
    }

    @Test
    void marksACaptureUnavailableWhenNoResponseWasObtained() {
        ChatResponseCapture capture = ChatResponseCapture.unavailable(
                ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED);

        assertThat(capture.content()).isNull();
        assertThat(capture.thinking()).isNull();
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.UNAVAILABLE);
        assertThat(ChatResponseCapture.from(null, ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED))
                .isEqualTo(capture);
    }

    @Test
    void rejectsInconsistentPresencePolicyAndTokenCombinations() {
        assertThatThrownBy(() -> new ChatResponseCapture(
                "text", null, ChatThinkingPresence.PRESENT, null, null,
                ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thinkingPresence PRESENT");
        assertThatThrownBy(() -> new ChatResponseCapture(
                "text", null, ChatThinkingPresence.UNAVAILABLE, null, null,
                ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unavailable capture must not record content");
        assertThatThrownBy(() -> new ChatResponseCapture(
                null, null, ChatThinkingPresence.ABSENT, null, -1,
                ChatReasoningPolicy.DISABLED, ChatReasoningSupport.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluatedOutputTokens");
        assertThatThrownBy(() -> new ChatResponseCapture(
                null, null, ChatThinkingPresence.ABSENT, null, null,
                ChatReasoningPolicy.PROVIDER_DEFAULT, ChatReasoningSupport.APPLIED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_REQUESTED");
    }

    private static ChatResponse response(
            String content,
            String thinking,
            String finishReason,
            Integer promptTokens,
            Integer completionTokens
    ) {
        AssistantMessage assistant = AssistantMessage.builder()
                .content(content)
                .properties(thinking == null
                        ? Map.of()
                        : Map.of(ChatResponseCapture.THINKING_KEY, thinking))
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        return new ChatResponse(
                List.of(new Generation(assistant, generationMetadata)),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }
}
