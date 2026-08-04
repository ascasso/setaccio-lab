package com.setaccio.lab.evaluation;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;

public final class LocalFactCheckJudgeBoundary {

    private final ChatModel judgeModel;
    private final LocalFactCheckJudgeSettings settings;
    private final LocalFactCheckPromptDefinition promptDefinition;

    public LocalFactCheckJudgeBoundary(
            ChatModel judgeModel,
            LocalFactCheckJudgeSettings settings,
            LocalFactCheckPromptDefinition promptDefinition
    ) {
        this.judgeModel = Objects.requireNonNull(judgeModel, "judgeModel must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.promptDefinition = Objects.requireNonNull(promptDefinition, "promptDefinition must not be null");
    }

    public LocalFactCheckJudgeResult evaluate(LocalFactCheckFixture fixture) {
        Objects.requireNonNull(fixture, "fixture must not be null");
        RecordingChatModel recordingModel = new RecordingChatModel(judgeModel, settings);
        ChatClient.Builder chatClientBuilder = ChatClient.builder(recordingModel)
                .defaultOptions(settings.ollamaOptions().mutate());
        FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(chatClientBuilder)
                .evaluationPrompt(promptDefinition.text())
                .build();

        try {
            EvaluationResponse evaluationResponse = evaluator.evaluate(new EvaluationRequest(
                    List.of(new Document(fixture.document())),
                    fixture.claim()));
            RecordedInvocation invocation = recordingModel.observation();
            LocalFactCheckJudgeVerdict verdict = LocalFactCheckJudgeVerdict.normalize(invocation.rawResponse());
            Boolean expectedVerdictMatched = verdict == null
                    ? null
                    : expectedVerdictMatched(fixture.expectedVerdict(), verdict);
            LocalFactCheckDiagnosticCategory category = category(
                    invocation.rawResponse(),
                    verdict,
                    expectedVerdictMatched);
            return completed(fixture, evaluationResponse, invocation, verdict, expectedVerdictMatched, category);
        } catch (RuntimeException exception) {
            RecordedInvocation invocation = recordingModel.observation();
            return failed(fixture, invocation, category(exception), safeMessage(exception));
        }
    }

    private LocalFactCheckJudgeResult completed(
            LocalFactCheckFixture fixture,
            EvaluationResponse evaluationResponse,
            RecordedInvocation invocation,
            LocalFactCheckJudgeVerdict verdict,
            Boolean expectedVerdictMatched,
            LocalFactCheckDiagnosticCategory category
    ) {
        return new LocalFactCheckJudgeResult(
                fixture.id(),
                fixture.expectedVerdict(),
                settings,
                invocation.invocationSucceeded(),
                evaluationResponse.isPass(),
                verdict,
                expectedVerdictMatched,
                category,
                invocation.rawResponse(),
                invocation.responseMetadata(),
                invocation.promptTokens(),
                invocation.completionTokens(),
                invocation.totalTokens(),
                invocation.latencyMillis(),
                invocation.attemptCount(),
                null);
    }

    private LocalFactCheckJudgeResult failed(
            LocalFactCheckFixture fixture,
            RecordedInvocation invocation,
            LocalFactCheckDiagnosticCategory category,
            String error
    ) {
        return new LocalFactCheckJudgeResult(
                fixture.id(),
                fixture.expectedVerdict(),
                settings,
                false,
                null,
                null,
                null,
                category,
                invocation.rawResponse(),
                invocation.responseMetadata(),
                invocation.promptTokens(),
                invocation.completionTokens(),
                invocation.totalTokens(),
                invocation.latencyMillis(),
                invocation.attemptCount(),
                error);
    }

    private static boolean expectedVerdictMatched(
            LocalFactCheckExpectedVerdict expected,
            LocalFactCheckJudgeVerdict actual
    ) {
        return (expected == LocalFactCheckExpectedVerdict.SUPPORTED
                && actual == LocalFactCheckJudgeVerdict.SUPPORTED)
                || (expected == LocalFactCheckExpectedVerdict.UNSUPPORTED
                && actual == LocalFactCheckJudgeVerdict.UNSUPPORTED);
    }

    private static LocalFactCheckDiagnosticCategory category(
            String rawResponse,
            LocalFactCheckJudgeVerdict verdict,
            Boolean expectedVerdictMatched
    ) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE;
        }
        if (verdict == null) {
            return LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT;
        }
        if (Boolean.FALSE.equals(expectedVerdictMatched)) {
            return LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH;
        }
        return LocalFactCheckDiagnosticCategory.NONE;
    }

    private static LocalFactCheckDiagnosticCategory category(RuntimeException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof LocalFactCheckJudgeModelUnavailableException) {
                return LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE;
            }
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return LocalFactCheckDiagnosticCategory.TIMEOUT;
            }
        }
        return LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE;
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

    private static final class RecordingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final LocalFactCheckJudgeSettings settings;
        private final OllamaChatOptions options;
        private final AtomicInteger attemptCount = new AtomicInteger();
        private final AtomicReference<RecordedInvocation> observation = new AtomicReference<>(
                RecordedInvocation.notStarted());

        private RecordingChatModel(ChatModel delegate, LocalFactCheckJudgeSettings settings) {
            this.delegate = delegate;
            this.settings = settings;
            this.options = settings.ollamaOptions();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            long startedNanos = System.nanoTime();
            int currentAttempt = attemptCount.incrementAndGet();
            if (currentAttempt > settings.maxAttempts()) {
                IllegalStateException exception = new IllegalStateException(
                        "Fact-check judge exceeded the explicit one-attempt policy");
                observation.set(RecordedInvocation.failed(elapsedMillis(startedNanos), currentAttempt));
                throw exception;
            }

            Prompt explicitPrompt = prompt.mutate().chatOptions(options).build();
            try {
                ChatResponse response = delegate.call(explicitPrompt);
                observation.set(RecordedInvocation.completed(
                        response,
                        elapsedMillis(startedNanos),
                        currentAttempt));
                return response;
            } catch (RuntimeException exception) {
                observation.set(RecordedInvocation.failed(
                        elapsedMillis(startedNanos),
                        currentAttempt));
                throw exception;
            }
        }

        @Override
        public ChatOptions getOptions() {
            return options;
        }

        private RecordedInvocation observation() {
            return observation.get();
        }
    }

    private record RecordedInvocation(
            boolean invocationSucceeded,
            String rawResponse,
            LocalFactCheckJudgeResponseMetadata responseMetadata,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            long latencyMillis,
            int attemptCount
    ) {
        private static RecordedInvocation notStarted() {
            return new RecordedInvocation(false, null, null, null, null, null, 0, 0);
        }

        private static RecordedInvocation failed(long latencyMillis, int attemptCount) {
            return new RecordedInvocation(false, null, null, null, null, null, latencyMillis, attemptCount);
        }

        private static RecordedInvocation completed(ChatResponse response, long latencyMillis, int attemptCount) {
            String rawResponse = response == null
                    || response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? null
                    : response.getResult().getOutput().getText();
            ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
            Usage usage = metadata == null ? null : metadata.getUsage();
            boolean usageAvailable = usage != null && !(usage instanceof EmptyUsage);
            Map<String, Object> attributes = captureAttributes(metadata);
            LocalFactCheckJudgeResponseMetadata capturedMetadata = metadata == null
                    ? null
                    : new LocalFactCheckJudgeResponseMetadata(
                            metadata.getId(),
                            metadata.getModel(),
                            attributes);
            return new RecordedInvocation(
                    true,
                    rawResponse,
                    capturedMetadata,
                    usageAvailable ? usage.getPromptTokens() : null,
                    usageAvailable ? usage.getCompletionTokens() : null,
                    usageAvailable ? usage.getTotalTokens() : null,
                    latencyMillis,
                    attemptCount);
        }

        private static Map<String, Object> captureAttributes(ChatResponseMetadata metadata) {
            if (metadata == null) {
                return Map.of();
            }
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                attributes.put(entry.getKey(), entry.getValue());
            }
            return Collections.unmodifiableMap(attributes);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
