package com.setaccio.lab.thinking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evaluation.LocalFactCheckExpectedVerdict;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeVerdict;
import com.setaccio.lab.evaluation.LocalFactCheckPromptDefinition;
import java.util.ArrayList;
import java.util.Collections;
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

    static ThinkingDiagnosticResult legacyResult() {
        LocalFactCheckFixtureCatalog catalog = catalog();
        LocalFactCheckPromptDefinition prompt = prompt();
        var blake3 = new ApacheCommonsBlake3HashingServiceImpl();
        List<ThinkingDiagnosticScheduleEntry> schedule = ThinkingDiagnosticProtocol.schedule(
                catalog, ThinkingDiagnosticProtocol.LEGACY_VERSION);
        List<ThinkingDiagnosticRow> rows = schedule.stream().map(entry -> {
            ThinkingDiagnosticArm arm = ThinkingDiagnosticProtocol.requireArm(
                    ThinkingDiagnosticProtocol.LEGACY_VERSION, entry.armId());
            var fixture = catalog.require(entry.fixtureId());
            LocalFactCheckJudgeVerdict verdict = fixture.expectedVerdict()
                    == LocalFactCheckExpectedVerdict.SUPPORTED
                    ? LocalFactCheckJudgeVerdict.SUPPORTED
                    : LocalFactCheckJudgeVerdict.UNSUPPORTED;
            return new ThinkingDiagnosticRow(
                    entry.sequence(), arm.armId(), arm.executionBoundary(), arm.modelRole(),
                    arm.modelRole() == ThinkingDiagnosticModelRole.SUBJECT
                            ? "subject:model" : "control:model",
                    arm.reasoningPolicy(), ChatReasoningSupport.APPLIED,
                    arm.modelRole() == ThinkingDiagnosticModelRole.SUBJECT,
                    arm.maxOutputTokens(), entry.seed(), fixture.id(), fixture.pairId(),
                    fixture.expectedVerdict(), blake3.hashString(fixture.document()),
                    blake3.hashString(fixture.claim()), true, verdict == LocalFactCheckJudgeVerdict.SUPPORTED
                            ? "yes" : "no", null, com.setaccio.lab.chat.ChatThinkingPresence.ABSENT,
                    "stop", 2, 11, 13, verdict, true,
                    ThinkingDiagnosticOutcome.CONTENT_WITHOUT_THINKING, 1L, 1, null);
        }).toList();
        return new ThinkingDiagnosticResult(
                ThinkingDiagnosticProtocol.LEGACY_VERSION,
                ThinkingDiagnosticProtocol.PROVIDER,
                ThinkingDiagnosticProtocol.ENDPOINT_CATEGORY,
                ThinkingDiagnosticProtocol.EXECUTION_STRATEGY,
                ThinkingDiagnosticProtocol.PULL_MODEL_STRATEGY,
                ThinkingDiagnosticProtocol.TEMPERATURE,
                ThinkingDiagnosticProtocol.SEED,
                ThinkingDiagnosticProtocol.MAX_ATTEMPTS,
                ThinkingDiagnosticProtocol.REQUEST_TIMEOUT.toMillis(),
                null, null, null,
                ThinkingDiagnosticProtocol.arms(ThinkingDiagnosticProtocol.LEGACY_VERSION),
                List.of(subject(true), control()), "0.33.2", prompt.id(), prompt.version(),
                prompt.sha256(), catalog.id(), catalog.version(), catalog.sha256(), schedule, rows);
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
            if (think == null || ThinkOption.ThinkBoolean.ENABLED.equals(think)) {
                return response("", "reasoning trace", "length", 11, options.getNumPredict());
            }
            return response("no", null, "stop", 11, 2);
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder().model("subject:model").build();
        }

        List<ThinkOption> observedPolicies() {
            return Collections.unmodifiableList(new ArrayList<>(observedPolicies));
        }
    }

    static final class PolicyAwareChatFactory implements ThinkingDiagnosticChatFactory {

        private final List<ChatReasoningPolicy> observedPolicies = new ArrayList<>();
        private final List<String> observedPrompts = new ArrayList<>();

        @Override
        public com.setaccio.lab.chat.ChatInvocation create(
                OllamaChatModelIdentity identity,
                com.setaccio.lab.chat.ChatGenerationSettings settings,
                ChatReasoningPolicy reasoningPolicy
        ) {
            return request -> {
                observedPolicies.add(reasoningPolicy);
                observedPrompts.add(request.prompt().text());
                boolean reasons = reasoningPolicy != ChatReasoningPolicy.DISABLED;
                ChatResponse response = reasons
                        ? response("", "reasoning trace", "length", 11, settings.maxOutputTokens())
                        : response("no", null, "stop", 11, 2);
                ChatReasoningSupport support = reasoningPolicy == ChatReasoningPolicy.PROVIDER_DEFAULT
                        ? ChatReasoningSupport.NOT_REQUESTED : ChatReasoningSupport.APPLIED;
                ChatResponseCapture capture = ChatResponseCapture.from(response, reasoningPolicy, support);
                return new ChatInvocationOutcome(
                        identity,
                        com.setaccio.lab.chat.ChatProviderOptionSupport.supportsAll(),
                        request.prompt().id(),
                        true,
                        capture.content(),
                        null,
                        11,
                        reasons ? settings.maxOutputTokens() : 2,
                        reasons ? 11 + settings.maxOutputTokens() : 13,
                        1L,
                        1,
                        capture.content() == null || capture.content().isBlank()
                                ? ChatInvocationFailureCategory.EMPTY_RESPONSE
                                : ChatInvocationFailureCategory.NONE,
                        null,
                        capture);
            };
        }

        List<ChatReasoningPolicy> observedPolicies() {
            return List.copyOf(observedPolicies);
        }

        List<String> observedPrompts() {
            return List.copyOf(observedPrompts);
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
