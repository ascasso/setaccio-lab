package com.setaccio.lab.thinking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evaluation.LocalFactCheckPromptDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

/**
 * Provider-free fixtures. Every test here runs against an in-memory chat model, so the suite
 * needs no Ollama service, no network, and no credentials.
 */
final class ThinkingDiagnosticTestSupport {

    private ThinkingDiagnosticTestSupport() {}

    static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    static LocalFactCheckFixtureCatalog catalog() {
        return new LocalFactCheckFixtureCatalog(objectMapper());
    }

    static LocalFactCheckPromptDefinition prompt() {
        return new LocalFactCheckPromptDefinition();
    }

    static ThinkingDiagnosticModelIdentity subject(boolean advertisesThinking) {
        return new ThinkingDiagnosticModelIdentity(
                ThinkingDiagnosticModelRole.SUBJECT, "subject:model", "subject:model",
                "a".repeat(64), advertisesThinking);
    }

    static ThinkingDiagnosticModelIdentity control() {
        return new ThinkingDiagnosticModelIdentity(
                ThinkingDiagnosticModelRole.CONTROL, "control:model", "control:model",
                "b".repeat(64), false);
    }

    static Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities() {
        return Map.of(
                ThinkingDiagnosticModelRole.SUBJECT, subject(true),
                ThinkingDiagnosticModelRole.CONTROL, control());
    }

    /**
     * A chat model that answers according to the reasoning policy actually present on the
     * request options, so tests assert propagation and capture in one place.
     */
    static final class PolicyAwareChatModel implements ChatModel {

        private final List<ThinkOption> observedPolicies = new ArrayList<>();

        @Override
        public ChatResponse call(Prompt prompt) {
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            ThinkOption think = options.getThinkOption();
            observedPolicies.add(think);
            if (ThinkOption.ThinkBoolean.ENABLED.equals(think)) {
                return response("", "reasoning trace", "length", 11, options.getNumPredict());
            }
            return response("no", null, "stop", 11, 2);
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder().model("subject:model").build();
        }

        List<ThinkOption> observedPolicies() {
            return List.copyOf(observedPolicies);
        }
    }

    static ChatResponse response(
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
        return new ChatResponse(
                List.of(new Generation(
                        assistant,
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder()
                        .id("response-1")
                        .model("subject:model")
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }
}
