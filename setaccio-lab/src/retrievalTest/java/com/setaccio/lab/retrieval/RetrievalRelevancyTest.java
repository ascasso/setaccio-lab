package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatInvocationRequest;
import com.setaccio.lab.chat.ChatModelUnavailableException;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

class RetrievalRelevancyTest {

    private static final String ANSWER_DIGEST = "a".repeat(64);
    private static final String EVALUATOR_DIGEST = "b".repeat(64);
    private static final OllamaChatModelIdentity ANSWER_CHAT_IDENTITY = new OllamaChatModelIdentity(
            "ollama", "answer-test", "answer-test", ANSWER_DIGEST);
    private static final RetrievalAnswerModelIdentity ANSWER_MODEL_IDENTITY = new RetrievalAnswerModelIdentity(
            "ollama", "answer-test", "answer-test", ANSWER_DIGEST);
    private static final RetrievalRelevancyModelIdentity EVALUATOR_MODEL_IDENTITY = new RetrievalRelevancyModelIdentity(
            "ollama", "evaluator-test", "evaluator-test", EVALUATOR_DIGEST);

    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesSpringEvaluatorOnlyWithPreservedR5DocumentsAndKeepsObservationsSeparate() {
        ChatModel evaluatorModel = mock(ChatModel.class);
        List<Prompt> prompts = new ArrayList<>();
        when(evaluatorModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return response("YES", 11, 2);
        });

        RetrievalRelevancyResult result = execute(
                answers((index, request) -> successfulAnswer(index)),
                new RetrievalRelevancyEvaluatorBoundary(
                        evaluatorModel, EVALUATOR_MODEL_IDENTITY, settings(), RetrievalRelevancyPromptDefinition.load()),
                EVALUATOR_MODEL_IDENTITY);

        assertThat(prompts).hasSize(12);
        RetrievalRelevancyRow first = result.rows().getFirst();
        String rendered = prompts.getFirst().getInstructions().stream().map(Message::getText)
                .reduce("", (left, right) -> left + "\n" + right);
        RetrievalEvaluationRetrievedDocument retrieved = first.answer().retrieval().retrievedDocuments().getFirst();
        assertThat(rendered).contains(first.answer().retrieval().query(), first.answer().invocation().answerText());
        assertThat(rendered).contains(retrieved.documentId(), retrieved.content());
        assertThat(prompts.getFirst().getOptions()).isInstanceOfSatisfying(OllamaChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("evaluator-test");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getSeed()).isEqualTo(42);
            assertThat(options.getNumPredict()).isEqualTo(64);
        });
        assertThat(first.deterministicExpectation())
                .isEqualTo(RetrievalRelevancyDeterministicExpectation.from(first.answer().retrieval()));
        assertThat(first.evaluatorOutcome().normalizedVerdict()).isEqualTo(RetrievalRelevancyVerdict.YES);
        assertThat(first.evaluatorOutcome().springEvaluatorPassed()).isTrue();
        assertThat(first.modelRelationship()).isEqualTo(RetrievalRelevancyModelRelationship.SEPARATE_EVALUATOR);
        assertThat(first.humanSupportJudgment()).isEqualTo(RetrievalRelevancyHumanSupportJudgment.NOT_REVIEWED);
        assertThat(first.answerCorrectness()).isEqualTo(RetrievalRelevancyAnswerCorrectness.NOT_ASSESSED);
        assertThat(result.rows().get(12).evaluatorOutcome().diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT);
        assertThat(result.rows().get(12).evaluatorOutcome().invocationAttempted()).isFalse();
    }

    @Test
    void flagsSelfEvaluationAndDoesNotCallEvaluatorForMissingContextOrAnswer() {
        AtomicInteger calls = new AtomicInteger();
        RetrievalRelevancyModelIdentity sameAsAnswer = new RetrievalRelevancyModelIdentity(
                "ollama", "answer-test", "answer-test", ANSWER_DIGEST);
        RetrievalRelevancyResult selfEvaluated = execute(
                answers((index, request) -> index == 0
                        ? failedAnswer(ChatInvocationFailureCategory.PROVIDER_FAILURE, index)
                        : successfulAnswer(index)),
                (query, documents, answer) -> {
                    calls.incrementAndGet();
                    return successfulEvaluatorOutcome(sameAsAnswer);
                },
                sameAsAnswer);

        assertThat(calls).hasValue(11);
        assertThat(selfEvaluated.rows()).allSatisfy(row ->
                assertThat(row.modelRelationship()).isEqualTo(RetrievalRelevancyModelRelationship.SELF_EVALUATION));
        assertThat(selfEvaluated.rows().getFirst().evaluatorOutcome().diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_NO_ANSWER);
        assertThat(selfEvaluated.rows().get(12).evaluatorOutcome().diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT);
    }

    @Test
    void rejectsSyntheticContextAndClassifiesRecordedBoundaryFailures() {
        assertThatThrownBy(() -> RetrievalRelevancyEvaluatorBoundary.documentsFor(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actual retrieved document");

        RetrievalEvaluationRetrievedDocument document = retrieval().rows().getFirst().retrievedDocuments().getFirst();
        assertThat(boundary(modelReturning("NO")).evaluate("query", List.of(document), "answer"))
                .satisfies(result -> {
                    assertThat(result.invocationSucceeded()).isTrue();
                    assertThat(result.normalizedVerdict()).isEqualTo(RetrievalRelevancyVerdict.NO);
                    assertThat(result.springEvaluatorPassed()).isFalse();
                    assertThat(result.springEvaluatorScore()).isEqualTo(0.0f);
                    assertThat(result.diagnosticCategory()).isEqualTo(RetrievalRelevancyDiagnosticCategory.NONE);
                });
        assertThat(boundary(modelReturning(" \n\t")).evaluate("query", List.of(document), "answer").diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.EMPTY_RESPONSE);
        assertThat(boundary(modelReturning("maybe")).evaluate("query", List.of(document), "answer").diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.MALFORMED_VERDICT);
        assertThat(boundary(failingModel(new ChatModelUnavailableException("not installed")))
                .evaluate("query", List.of(document), "answer").diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.EVALUATOR_MODEL_UNAVAILABLE);
        assertThat(boundary(failingModel(new IllegalStateException("timed out", new SocketTimeoutException("read"))))
                .evaluate("query", List.of(document), "answer").diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.TIMEOUT);
        assertThat(boundary(failingModel(new IllegalStateException("provider failure")))
                .evaluate("query", List.of(document), "answer").diagnosticCategory())
                .isEqualTo(RetrievalRelevancyDiagnosticCategory.PROVIDER_FAILURE);
    }

    @Test
    void writesVerifiesAndRepairsOnlyTheDeterministicSummary() throws Exception {
        RetrievalRelevancyEvidence evidence = evidence();
        Path run = Files.createDirectory(temporaryDirectory.resolve("relevancy-run"));
        evidence.write(run, execute(
                answers((index, request) -> successfulAnswer(index)),
                (query, documents, answer) -> successfulEvaluatorOutcome(EVALUATOR_MODEL_IDENTITY),
                EVALUATOR_MODEL_IDENTITY), new EvidenceCodeBaseline("c".repeat(40), false));

        assertThat(evidence.verify(run).failures()).isEmpty();
        Path summary = run.resolve(RetrievalRelevancyEvidence.SUMMARY_FILENAME);
        Files.writeString(summary, "changed summary\n");
        assertThat(evidence.verify(run).failures()).isNotEmpty();
        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(Files.readString(summary)).contains("# Retrieval Relevancy Evaluation");

        Files.writeString(run.resolve(RetrievalRelevancyProtocol.RAW_FILENAME), "{}\n");
        assertThat(evidence.reanalyze(run).failures()).isNotEmpty();
    }

    @Test
    void restrictsNewAndSavedEvidenceToDedicatedRoots() {
        assertThat(RetrievalRelevancyRunner.resolveNewOutputDirectory(
                "build/retrieval-relevancy/2026-08-28-r6").getFileName().toString()).isEqualTo("2026-08-28-r6");
        assertThatThrownBy(() -> RetrievalRelevancyRunner.resolveNewOutputDirectory("build/elsewhere/2026-08-28-r6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("build/retrieval-relevancy");
        assertThatThrownBy(() -> RetrievalRelevancyRunner.resolveNewOutputDirectory("build/retrieval-relevancy/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> RetrievalRelevancyOfflineRunner.resolveRunDirectory("build/retrieval-answer/2026-08-28-r5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("build/retrieval-relevancy");
    }

    private static RetrievalRelevancyResult execute(
            RetrievalAnswerResult answers,
            RetrievalRelevancyEvaluator evaluator,
            RetrievalRelevancyModelIdentity evaluatorIdentity
    ) {
        return new RetrievalRelevancyExecutor(evaluator).execute(
                sourceEvidence(), answers, RetrievalRelevancyPromptDefinition.load(), evaluatorIdentity, settings());
    }

    private static RetrievalAnswerResult answers(
            BiFunction<Integer, ChatInvocationRequest, ChatInvocationOutcome> outcomes
    ) {
        AtomicInteger index = new AtomicInteger();
        ChatInvocation invocation = request -> outcomes.apply(index.getAndIncrement(), request);
        return new RetrievalAnswerExecutor(invocation).execute(
                new RetrievalAnswerSourceEvidence("2026-08-28-r3", "c".repeat(64), "d".repeat(64), "e".repeat(40)),
                retrieval(), RetrievalAnswerPromptDefinition.load(), ANSWER_MODEL_IDENTITY,
                RetrievalAnswerProtocol.settings(42, 128, Duration.ofMinutes(2)), ANSWER_CHAT_IDENTITY);
    }

    private static RetrievalEvaluationResult retrieval() {
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        return new RetrievalEvaluationExecutor(new DeterministicLexicalRetriever())
                .execute(inputs.corpus(), inputs.catalog());
    }

    private static RetrievalRelevancyEvidence evidence() {
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        return new RetrievalRelevancyEvidence(
                JsonMapper.builder().findAndAddModules().build(), inputs.corpus(), inputs.catalog());
    }

    private static RetrievalRelevancySourceEvidence sourceEvidence() {
        return new RetrievalRelevancySourceEvidence("2026-08-28-r5", "f".repeat(64), "1".repeat(64), "2".repeat(40));
    }

    private static RetrievalRelevancyRunSettings settings() {
        return RetrievalRelevancyProtocol.settings(42, 64, Duration.ofMinutes(2));
    }

    private static RetrievalRelevancyEvaluatorBoundary boundary(ChatModel model) {
        return new RetrievalRelevancyEvaluatorBoundary(
                model, EVALUATOR_MODEL_IDENTITY, settings(), RetrievalRelevancyPromptDefinition.load());
    }

    private static RetrievalRelevancyEvaluatorOutcome successfulEvaluatorOutcome(
            RetrievalRelevancyModelIdentity modelIdentity
    ) {
        RetrievalRelevancyPromptContract prompt = RetrievalRelevancyPromptDefinition.load().contract();
        return new RetrievalRelevancyEvaluatorOutcome(
                modelIdentity, prompt.promptId(), prompt.promptSha256(), true, true,
                true, 1.0f, RetrievalRelevancyVerdict.YES, RetrievalRelevancyDiagnosticCategory.NONE,
                "YES", new RetrievalRelevancyResponseMetadata("response_1", "evaluator-test"),
                10, 2, 12, 1L, 1);
    }

    private static ChatInvocationOutcome successfulAnswer(int index) {
        RetrievalEvaluationRetrievedDocument document = retrieval().rows().get(index).retrievedDocuments().stream()
                .findFirst().orElse(null);
        return new ChatInvocationOutcome(
                ANSWER_CHAT_IDENTITY, ChatProviderOptionSupport.supportsAll(), "retrieval-grounded-answer-v1", true,
                document == null ? "NO_SUPPORT" : "Answer [" + document.documentId() + "]",
                "answer_" + index, 10, 3, 13, 1L, 1, ChatInvocationFailureCategory.NONE, null);
    }

    private static ChatInvocationOutcome failedAnswer(ChatInvocationFailureCategory category, int index) {
        return new ChatInvocationOutcome(
                ANSWER_CHAT_IDENTITY, ChatProviderOptionSupport.supportsAll(), "retrieval-grounded-answer-v1", false,
                null, "answer_" + index, null, null, null, 1L, 1, category, "provider diagnostic");
    }

    private static ChatModel modelReturning(String rawResponse) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response(rawResponse, 10, 2));
        return model;
    }

    private static ChatModel failingModel(RuntimeException exception) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(exception);
        return model;
    }

    private static ChatResponse response(String text, int promptTokens, int completionTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .id("response_1")
                        .model("evaluator-test")
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }
}
