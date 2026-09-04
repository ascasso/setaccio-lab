package com.setaccio.lab.evaluation;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.ChatThinkingPresence;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalFactCheckJudgeBoundaryTest {

    private final LocalFactCheckPromptDefinition promptDefinition = new LocalFactCheckPromptDefinition();

    @Test
    void propagatesTheTrackedPromptAndAllOptionsForBothRepetitions() {
        ChatModel judgeModel = mock(ChatModel.class);
        List<Prompt> capturedPrompts = new ArrayList<>();
        when(judgeModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            capturedPrompts.add(prompt);
            return response(capturedPrompts.size() == 1 ? "yes" : "no", 11, 1);
        });

        LocalFactCheckFixture supported = fixture(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED);
        LocalFactCheckFixture unsupported = fixture(
                "unsupported-fixture",
                LocalFactCheckExpectedVerdict.UNSUPPORTED);

        LocalFactCheckJudgeResult first = boundary(judgeModel, 42).evaluate(supported);
        LocalFactCheckJudgeResult second = boundary(judgeModel, 43).evaluate(unsupported);

        assertThat(capturedPrompts).hasSize(2);
        assertExplicitPrompt(capturedPrompts.get(0), supported, 42);
        assertExplicitPrompt(capturedPrompts.get(1), unsupported, 43);
        assertThat(first.normalizedJudgeVerdict()).isEqualTo(LocalFactCheckJudgeVerdict.SUPPORTED);
        assertThat(first.springEvaluatorPassed()).isTrue();
        assertThat(first.expectedVerdictMatched()).isTrue();
        assertThat(second.normalizedJudgeVerdict()).isEqualTo(LocalFactCheckJudgeVerdict.UNSUPPORTED);
        assertThat(second.springEvaluatorPassed()).isFalse();
        assertThat(second.expectedVerdictMatched()).isTrue();
    }

    @Test
    void recordsRawVerdictResponseMetadataUsageLatencyAndAttempts() {
        ChatModel judgeModel = mock(ChatModel.class);
        when(judgeModel.call(any(Prompt.class))).thenReturn(response(" \nYeS\t", 13, 2));

        LocalFactCheckJudgeResult result = boundary(judgeModel, 42).evaluate(fixture(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED));

        assertThat(result.invocationSucceeded()).isTrue();
        assertThat(result.springEvaluatorPassed()).isTrue();
        assertThat(result.normalizedJudgeVerdict()).isEqualTo(LocalFactCheckJudgeVerdict.SUPPORTED);
        assertThat(result.expectedVerdictMatched()).isTrue();
        assertThat(result.diagnosticCategory()).isEqualTo(LocalFactCheckDiagnosticCategory.NONE);
        assertThat(result.rawResponse()).isEqualTo(" \nYeS\t");
        assertThat(result.responseMetadata().responseId()).isEqualTo("response-1");
        assertThat(result.responseMetadata().responseModel()).isEqualTo("judge:model");
        assertThat(result.responseMetadata().attributes()).containsEntry("done", true);
        assertThat(result.promptTokens()).isEqualTo(13);
        assertThat(result.completionTokens()).isEqualTo(2);
        assertThat(result.totalTokens()).isEqualTo(15);
        assertThat(result.latencyMillis()).isNotNegative();
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.error()).isNull();
    }

    @Test
    void distinguishesExpectationMismatchFromTheSpringEvaluatorBoolean() {
        ChatModel judgeModel = modelReturning("no");

        LocalFactCheckJudgeResult result = boundary(judgeModel, 42).evaluate(fixture(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED));

        assertThat(result.invocationSucceeded()).isTrue();
        assertThat(result.springEvaluatorPassed()).isFalse();
        assertThat(result.normalizedJudgeVerdict()).isEqualTo(LocalFactCheckJudgeVerdict.UNSUPPORTED);
        assertThat(result.expectedVerdictMatched()).isFalse();
        assertThat(result.diagnosticCategory())
                .isEqualTo(LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH);
    }

    @Test
    void classifiesEmptyAndMalformedResponsesWithoutCoercingThemToNo() {
        LocalFactCheckFixture fixture = fixture(
                "unsupported-fixture",
                LocalFactCheckExpectedVerdict.UNSUPPORTED);

        LocalFactCheckJudgeResult empty = boundary(modelReturning(" \n\t"), 42).evaluate(fixture);
        LocalFactCheckJudgeResult malformed = boundary(modelReturning("probably"), 43).evaluate(fixture);

        assertThat(empty.invocationSucceeded()).isTrue();
        assertThat(empty.springEvaluatorPassed()).isFalse();
        assertThat(empty.normalizedJudgeVerdict()).isNull();
        assertThat(empty.expectedVerdictMatched()).isNull();
        assertThat(empty.diagnosticCategory()).isEqualTo(LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE);
        assertThat(malformed.invocationSucceeded()).isTrue();
        assertThat(malformed.springEvaluatorPassed()).isFalse();
        assertThat(malformed.normalizedJudgeVerdict()).isNull();
        assertThat(malformed.expectedVerdictMatched()).isNull();
        assertThat(malformed.diagnosticCategory())
                .isEqualTo(LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT);
    }

    @Test
    void preservesAbsentUsageAsNullInsteadOfInventingZeroTokenCounts() {
        ChatModel judgeModel = mock(ChatModel.class);
        when(judgeModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("yes")))));

        LocalFactCheckJudgeResult result = boundary(judgeModel, 42).evaluate(fixture(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED));

        assertThat(result.invocationSucceeded()).isTrue();
        assertThat(result.responseMetadata()).isNotNull();
        assertThat(result.promptTokens()).isNull();
        assertThat(result.completionTokens()).isNull();
        assertThat(result.totalTokens()).isNull();
    }

    @Test
    void classifiesUnavailableTimeoutAndProviderFailures() {
        LocalFactCheckFixture fixture = fixture(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED);

        LocalFactCheckJudgeResult unavailable = boundary(failingModel(
                new LocalFactCheckJudgeModelUnavailableException("judge is not installed")), 42).evaluate(fixture);
        LocalFactCheckJudgeResult timeout = boundary(failingModel(
                new IllegalStateException("judge timed out", new SocketTimeoutException("read timed out"))), 42)
                .evaluate(fixture);
        LocalFactCheckJudgeResult providerFailure = boundary(failingModel(
                new IllegalStateException("fixture provider failure")), 42).evaluate(fixture);

        assertFailure(unavailable, LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE, "judge is not installed");
        assertFailure(timeout, LocalFactCheckDiagnosticCategory.TIMEOUT, "read timed out");
        assertFailure(providerFailure, LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE, "fixture provider failure");
    }

    @Test
    void settingsRequireEveryJudgeOptionAndForbidHiddenRetryPolicy() {
        assertThatThrownBy(() -> settings(" ", 42, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("model must not be blank");
        assertThatThrownBy(() -> settings("judge:model", 42, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts must be exactly 1 for the local fact-check judge");
        assertThatThrownBy(() -> new LocalFactCheckJudgeSettings(
                "judge:model", 0.0, 42, 64, Duration.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
    }

    private void assertExplicitPrompt(Prompt prompt, LocalFactCheckFixture fixture, int seed) {
        assertThat(prompt.getOptions()).isInstanceOfSatisfying(OllamaChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("judge:model");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getSeed()).isEqualTo(seed);
            assertThat(options.getNumPredict()).isEqualTo(64);
        });
        assertThat(prompt.getInstructions())
                .singleElement()
                .isInstanceOfSatisfying(UserMessage.class, message -> assertThat(message.getText()).isEqualTo(
                        promptDefinition.text()
                                .replace(LocalFactCheckPromptDefinition.DOCUMENT_PLACEHOLDER, fixture.document())
                                .replace(LocalFactCheckPromptDefinition.CLAIM_PLACEHOLDER, fixture.claim())));
    }

    private static void assertFailure(
            LocalFactCheckJudgeResult result,
            LocalFactCheckDiagnosticCategory expectedCategory,
            String expectedError
    ) {
        assertThat(result.invocationSucceeded()).isFalse();
        assertThat(result.springEvaluatorPassed()).isNull();
        assertThat(result.normalizedJudgeVerdict()).isNull();
        assertThat(result.expectedVerdictMatched()).isNull();
        assertThat(result.diagnosticCategory()).isEqualTo(expectedCategory);
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.error()).isEqualTo(expectedError);
    }

    @Test
    void capturesThinkingFinishReasonAndEvaluatedTokensThroughTheRecordingBoundary() {
        ChatModel judgeModel = mock(ChatModel.class);
        when(judgeModel.call(any(Prompt.class)))
                .thenReturn(thinkingResponse("", "a private reasoning trace", "length", 11, 64));

        LocalFactCheckJudgeResult result = new LocalFactCheckJudgeBoundary(
                judgeModel, settings("judge:model", 42, 1), promptDefinition,
                ChatReasoningPolicy.ENABLED)
                .evaluate(fixture("supported-fixture", LocalFactCheckExpectedVerdict.SUPPORTED));

        ChatResponseCapture capture = result.capture();
        assertThat(capture).isNotNull();
        assertThat(capture.content()).isEmpty();
        assertThat(capture.thinking()).isEqualTo("a private reasoning trace");
        assertThat(capture.thinkingPresence()).isEqualTo(ChatThinkingPresence.PRESENT);
        assertThat(capture.thinkingWithoutContent()).isTrue();
        assertThat(capture.finishReason()).isEqualTo("length");
        assertThat(capture.evaluatedOutputTokens()).isEqualTo(64);
        assertThat(capture.requestedReasoningPolicy()).isEqualTo(ChatReasoningPolicy.ENABLED);
        assertThat(capture.reasoningPolicySupport()).isEqualTo(ChatReasoningSupport.APPLIED);

        assertThat(result.rawResponse()).isEmpty();
        assertThat(result.rawResponse()).doesNotContain("reasoning");
        assertThat(result.diagnosticCategory()).isEqualTo(LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE);
    }

    @Test
    void sendsTheExplicitReasoningPolicyOnEveryJudgeRequest() {
        ChatModel judgeModel = mock(ChatModel.class);
        List<Prompt> prompts = new ArrayList<>();
        when(judgeModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response("no", 11, 1);
        });

        new LocalFactCheckJudgeBoundary(
                judgeModel, settings("judge:model", 42, 1), promptDefinition, ChatReasoningPolicy.DISABLED)
                .evaluate(fixture("unsupported-fixture", LocalFactCheckExpectedVerdict.UNSUPPORTED));

        assertThat(prompts).hasSize(1);
        OllamaChatOptions options = (OllamaChatOptions) prompts.getFirst().getOptions();
        assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
        assertThat(options.getNumPredict()).isEqualTo(64);
        assertThat(options.getSeed()).isEqualTo(42);
    }

    @Test
    void leavesTheReasoningPolicyUnsentUnlessACallerRequestsOne() {
        ChatModel judgeModel = mock(ChatModel.class);
        List<Prompt> prompts = new ArrayList<>();
        when(judgeModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response("no", 11, 1);
        });

        LocalFactCheckJudgeResult result = boundary(judgeModel, 42)
                .evaluate(fixture("unsupported-fixture", LocalFactCheckExpectedVerdict.UNSUPPORTED));

        OllamaChatOptions options = (OllamaChatOptions) prompts.getFirst().getOptions();
        assertThat(options.getThinkOption()).isNull();
        assertThat(result.capture().requestedReasoningPolicy())
                .isEqualTo(ChatReasoningPolicy.PROVIDER_DEFAULT);
        assertThat(result.capture().reasoningPolicySupport())
                .isEqualTo(ChatReasoningSupport.NOT_REQUESTED);
    }

    @Test
    void recordsAnUnavailableCaptureWhenTheProviderFails() {
        LocalFactCheckJudgeResult result = new LocalFactCheckJudgeBoundary(
                failingModel(new IllegalStateException("provider exploded")),
                settings("judge:model", 42, 1),
                promptDefinition,
                ChatReasoningPolicy.ENABLED)
                .evaluate(fixture("supported-fixture", LocalFactCheckExpectedVerdict.SUPPORTED));

        assertThat(result.invocationSucceeded()).isFalse();
        assertThat(result.capture().thinkingPresence()).isEqualTo(ChatThinkingPresence.UNAVAILABLE);
        assertThat(result.capture().content()).isNull();
        assertThat(result.capture().thinking()).isNull();
    }

    @Test
    void keepsThePhase4ResultShapeConstructibleWithoutACapture() {
        LocalFactCheckJudgeResult legacy = new LocalFactCheckJudgeResult(
                "supported-fixture",
                LocalFactCheckExpectedVerdict.SUPPORTED,
                settings("judge:model", 42, 1),
                true,
                true,
                LocalFactCheckJudgeVerdict.SUPPORTED,
                true,
                LocalFactCheckDiagnosticCategory.NONE,
                "yes",
                null,
                11,
                1,
                12,
                5L,
                1,
                null);

        assertThat(legacy.capture()).isNull();
        assertThat(legacy.rawResponse()).isEqualTo("yes");
    }

    private static ChatResponse thinkingResponse(
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
                        .model("judge:model")
                        .keyValue("done", true)
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }

    private LocalFactCheckJudgeBoundary boundary(ChatModel judgeModel, int seed) {
        return new LocalFactCheckJudgeBoundary(judgeModel, settings("judge:model", seed, 1), promptDefinition);
    }

    private static LocalFactCheckJudgeSettings settings(String model, int seed, int maxAttempts) {
        return new LocalFactCheckJudgeSettings(
                model,
                0.0,
                seed,
                64,
                Duration.ofSeconds(30),
                maxAttempts);
    }

    private static LocalFactCheckFixture fixture(String id, LocalFactCheckExpectedVerdict expectedVerdict) {
        return new LocalFactCheckFixture(
                id,
                "fixture-pair",
                "The repair shop opens at 08:00 on weekdays.",
                expectedVerdict == LocalFactCheckExpectedVerdict.SUPPORTED
                        ? "The repair shop opens at 08:00 on weekdays."
                        : "The repair shop opens at 10:00 on weekdays.",
                expectedVerdict);
    }

    private static ChatModel modelReturning(String rawResponse) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response(rawResponse, null, null));
        return model;
    }

    private static ChatModel failingModel(RuntimeException exception) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(exception);
        return model;
    }

    private static ChatResponse response(String text, Integer promptTokens, Integer completionTokens) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder()
                .id("response-1")
                .model("judge:model")
                .keyValue("done", true);
        if (promptTokens != null || completionTokens != null) {
            metadata.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                metadata.build());
    }
}
