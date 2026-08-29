package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatModelUnavailableException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Records Spring AI's {@link RelevancyEvaluator} without allowing it to receive synthetic or
 * unpreserved context.
 */
final class RetrievalRelevancyEvaluatorBoundary implements RetrievalRelevancyEvaluator {

    private final ChatModel evaluatorModel;
    private final RetrievalRelevancyModelIdentity modelIdentity;
    private final RetrievalRelevancyRunSettings settings;
    private final RetrievalRelevancyPromptDefinition promptDefinition;

    RetrievalRelevancyEvaluatorBoundary(
            ChatModel evaluatorModel,
            RetrievalRelevancyModelIdentity modelIdentity,
            RetrievalRelevancyRunSettings settings,
            RetrievalRelevancyPromptDefinition promptDefinition
    ) {
        this.evaluatorModel = Objects.requireNonNull(evaluatorModel, "evaluatorModel must not be null");
        this.modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.promptDefinition = Objects.requireNonNull(promptDefinition, "promptDefinition must not be null");
    }

    @Override
    public RetrievalRelevancyEvaluatorOutcome evaluate(
            String query,
            List<RetrievalEvaluationRetrievedDocument> retrievedDocuments,
            String answerText
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (answerText == null || answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
        List<Document> documents = documentsFor(retrievedDocuments);
        RecordingChatModel recordingModel = new RecordingChatModel(evaluatorModel, modelIdentity, settings);
        ChatClient.Builder chatClientBuilder = ChatClient.builder(recordingModel)
                .defaultOptions(recordingModel.options().mutate());
        RelevancyEvaluator evaluator = RelevancyEvaluator.builder()
                .chatClientBuilder(chatClientBuilder)
                .promptTemplate(new PromptTemplate(promptDefinition.text()))
                .build();
        try {
            EvaluationResponse response = evaluator.evaluate(new EvaluationRequest(query, documents, answerText));
            RecordedInvocation invocation = recordingModel.observation();
            RetrievalRelevancyVerdict verdict = RetrievalRelevancyVerdict.normalize(invocation.rawResponse());
            RetrievalRelevancyDiagnosticCategory category = RetrievalRelevancyDiagnosticCategory.fromRawResponse(
                    invocation.rawResponse(), verdict);
            return new RetrievalRelevancyEvaluatorOutcome(
                    modelIdentity,
                    promptDefinition.contract().promptId(),
                    promptDefinition.contract().promptSha256(),
                    true,
                    invocation.invocationSucceeded(),
                    response.isPass(),
                    response.getScore(),
                    verdict,
                    category,
                    invocation.rawResponse(),
                    invocation.responseMetadata(),
                    invocation.promptTokens(),
                    invocation.completionTokens(),
                    invocation.totalTokens(),
                    invocation.latencyMillis(),
                    invocation.attemptCount());
        } catch (RuntimeException exception) {
            RecordedInvocation invocation = recordingModel.observation();
            return new RetrievalRelevancyEvaluatorOutcome(
                    modelIdentity,
                    promptDefinition.contract().promptId(),
                    promptDefinition.contract().promptSha256(),
                    true,
                    false,
                    null,
                    null,
                    null,
                    category(exception),
                    invocation.rawResponse(),
                    invocation.responseMetadata(),
                    invocation.promptTokens(),
                    invocation.completionTokens(),
                    invocation.totalTokens(),
                    invocation.latencyMillis(),
                    invocation.attemptCount());
        }
    }

    static List<Document> documentsFor(List<RetrievalEvaluationRetrievedDocument> retrievedDocuments) {
        if (retrievedDocuments == null || retrievedDocuments.isEmpty()) {
            throw new IllegalArgumentException("RelevancyEvaluator requires at least one actual retrieved document.");
        }
        return retrievedDocuments.stream().map(document -> new Document(
                document.documentId(),
                document.content(),
                Map.of(
                        "documentId", document.documentId(),
                        "rank", document.rank(),
                        "contentSha256", document.contentSha256())))
                .toList();
    }

    static RetrievalRelevancyEvaluatorOutcome notAttempted(
            RetrievalRelevancyModelIdentity modelIdentity,
            RetrievalRelevancyPromptContract prompt,
            RetrievalRelevancyDiagnosticCategory category
    ) {
        if (category != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT
                && category != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_NO_ANSWER) {
            throw new IllegalArgumentException("not-attempted evaluator outcome requires a not-attempted category");
        }
        return new RetrievalRelevancyEvaluatorOutcome(
                modelIdentity, prompt.promptId(), prompt.promptSha256(), false, false,
                null, null, null, category, null, null, null, null, null, 0, 0);
    }

    private static RetrievalRelevancyDiagnosticCategory category(RuntimeException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ChatModelUnavailableException) {
                return RetrievalRelevancyDiagnosticCategory.EVALUATOR_MODEL_UNAVAILABLE;
            }
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return RetrievalRelevancyDiagnosticCategory.TIMEOUT;
            }
        }
        return RetrievalRelevancyDiagnosticCategory.PROVIDER_FAILURE;
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ChatModel delegate;
        private final OllamaChatOptions options;
        private final AtomicInteger attemptCount = new AtomicInteger();
        private final AtomicReference<RecordedInvocation> observation = new AtomicReference<>(
                RecordedInvocation.notStarted());

        private RecordingChatModel(
                ChatModel delegate,
                RetrievalRelevancyModelIdentity modelIdentity,
                RetrievalRelevancyRunSettings settings
        ) {
            this.delegate = delegate;
            options = OllamaChatOptions.builder()
                    .model(modelIdentity.requestedModel())
                    .temperature(settings.temperature())
                    .seed(settings.seed())
                    .numPredict(settings.maxOutputTokens())
                    .build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            long startedNanos = System.nanoTime();
            int attempt = attemptCount.incrementAndGet();
            if (attempt != 1) {
                observation.set(RecordedInvocation.failed(elapsedMillis(startedNanos), attempt));
                throw new IllegalStateException("Relevancy evaluator exceeded the explicit one-attempt policy");
            }
            try {
                ChatResponse response = delegate.call(prompt.mutate().chatOptions(options).build());
                observation.set(RecordedInvocation.completed(response, elapsedMillis(startedNanos), attempt));
                return response;
            } catch (RuntimeException exception) {
                observation.set(RecordedInvocation.failed(elapsedMillis(startedNanos), attempt));
                throw exception;
            }
        }

        @Override
        public ChatOptions getOptions() {
            return options;
        }

        private OllamaChatOptions options() {
            return options;
        }

        private RecordedInvocation observation() {
            return observation.get();
        }
    }

    private record RecordedInvocation(
            boolean invocationSucceeded,
            String rawResponse,
            RetrievalRelevancyResponseMetadata responseMetadata,
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
            String rawResponse = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? null : response.getResult().getOutput().getText();
            ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
            Usage usage = metadata == null ? null : metadata.getUsage();
            boolean usageAvailable = usage != null && !(usage instanceof EmptyUsage);
            RetrievalRelevancyResponseMetadata safeMetadata = metadata == null ? null
                    : new RetrievalRelevancyResponseMetadata(metadata.getId(), metadata.getModel());
            return new RecordedInvocation(
                    true,
                    rawResponse,
                    safeMetadata,
                    usageAvailable ? usage.getPromptTokens() : null,
                    usageAvailable ? usage.getCompletionTokens() : null,
                    usageAvailable ? usage.getTotalTokens() : null,
                    latencyMillis,
                    attemptCount);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
