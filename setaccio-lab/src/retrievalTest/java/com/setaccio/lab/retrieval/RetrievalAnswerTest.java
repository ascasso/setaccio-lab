package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatInvocationRequest;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalAnswerTest {

    private static final String DIGEST = "a".repeat(64);
    private static final OllamaChatModelIdentity CHAT_IDENTITY = new OllamaChatModelIdentity(
            "ollama", "answer-test", "answer-test", DIGEST);
    private static final RetrievalAnswerModelIdentity MODEL_IDENTITY = new RetrievalAnswerModelIdentity(
            "ollama", "answer-test", "answer-test", DIGEST);

    @TempDir
    Path temporaryDirectory;

    @Test
    void retainsVerifiedRetrievalRowsRenderedPromptsAndSeparateAnswerObservations() {
        List<ChatInvocationRequest> requests = new ArrayList<>();
        RetrievalAnswerResult result = execute((index, request) -> {
            RetrievalEvaluationRetrievedDocument first = retrieval().rows().get(index).retrievedDocuments().stream()
                    .findFirst().orElse(null);
            String answer = first == null ? "NO_SUPPORT" : "Grounded answer [" + first.documentId() + "]";
            return successful(answer, index + 1);
        }, requests);

        assertThat(result.rows()).hasSize(14);
        RetrievalAnswerRow first = result.rows().getFirst();
        assertThat(first.retrieval()).isEqualTo(retrieval().rows().getFirst());
        assertThat(first.renderedPrompt()).contains("documentId=" + first.retrieval().retrievedDocuments().getFirst().documentId());
        assertThat(first.renderedPrompt()).contains(first.retrieval().retrievedDocuments().getFirst().content());
        assertThat(first.invocation().modelIdentity()).isEqualTo(MODEL_IDENTITY);
        assertThat(first.referenceAnalysis().behavior())
                .isEqualTo(RetrievalAnswerReferenceBehavior.RETRIEVED_DOCUMENT_REFERENCES_ONLY);
        assertThat(first.unsupportedAssertionAssessment()).isEqualTo(RetrievalAnswerSupportAssessment.NOT_ASSESSED);
        assertThat(result.rows().get(12).explicitAbstentionObserved()).isTrue();
        assertThat(result.rows().get(12).referenceAnalysis().behavior())
                .isEqualTo(RetrievalAnswerReferenceBehavior.EXPLICIT_ABSTENTION);
        assertThat(requests).hasSize(14);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.modelIdentity()).isEqualTo(CHAT_IDENTITY);
            assertThat(request.prompt().id()).isEqualTo("retrieval-grounded-answer-v1");
            assertThat(request.settings()).isEqualTo(settings().chatSettings());
        });
    }

    @Test
    void recordsMalformedEmptyTimeoutAndProviderFailureOutcomesWithoutChangingRetrieval() {
        RetrievalAnswerResult result = execute((index, request) -> switch (index) {
            case 0 -> successful("Answer [unknown-document]", 1);
            case 1 -> empty(2);
            case 2 -> failed(ChatInvocationFailureCategory.TIMEOUT, 3);
            case 3 -> failed(ChatInvocationFailureCategory.PROVIDER_FAILURE, 4);
            case 4 -> successful("Answer []", 5);
            default -> successful("NO_SUPPORT", index + 1);
        }, new ArrayList<>());

        assertThat(result.rows().get(0).referenceAnalysis().behavior())
                .isEqualTo(RetrievalAnswerReferenceBehavior.UNRETRIEVED_DOCUMENT_REFERENCE);
        assertThat(result.rows().get(0).referenceAnalysis().unretrievedDocumentIds()).containsExactly("unknown-document");
        assertThat(result.rows().get(1).invocation().failureCategory())
                .isEqualTo(ChatInvocationFailureCategory.EMPTY_RESPONSE);
        assertThat(result.rows().get(1).referenceAnalysis().behavior())
                .isEqualTo(RetrievalAnswerReferenceBehavior.NOT_OBSERVED);
        assertThat(result.rows().get(2).invocation().failureCategory())
                .isEqualTo(ChatInvocationFailureCategory.TIMEOUT);
        assertThat(result.rows().get(3).invocation().failureCategory())
                .isEqualTo(ChatInvocationFailureCategory.PROVIDER_FAILURE);
        assertThat(result.rows().get(4).referenceAnalysis().behavior())
                .isEqualTo(RetrievalAnswerReferenceBehavior.MALFORMED_DOCUMENT_REFERENCE);
        assertThat(result.rows().get(2).retrieval()).isEqualTo(retrieval().rows().get(2));
    }

    @Test
    void rejectsProviderModelIdentityDriftBeforeItCanEnterEvidence() {
        OllamaChatModelIdentity drifted = new OllamaChatModelIdentity(
                "ollama", "answer-test", "drifted-answer-test", "f".repeat(64));
        ChatInvocation invocation = request -> new ChatInvocationOutcome(
                drifted,
                ChatProviderOptionSupport.supportsAll(),
                "retrieval-grounded-answer-v1",
                true,
                "Answer [garden-compost-basics]",
                "response_drift",
                10,
                3,
                13,
                1L,
                1,
                ChatInvocationFailureCategory.NONE,
                null);

        assertThatThrownBy(() -> new RetrievalAnswerExecutor(invocation).execute(
                sourceEvidence(),
                retrieval(),
                RetrievalAnswerPromptDefinition.load(),
                MODEL_IDENTITY,
                settings(),
                CHAT_IDENTITY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model identity drifted");
    }

    @Test
    void writesVerifiesAndRepairsOnlyTheDeterministicSummary() throws Exception {
        RetrievalAnswerEvidence evidence = evidence();
        Path run = Files.createDirectory(temporaryDirectory.resolve("answer-run"));
        evidence.write(run, successfulResult(), new EvidenceCodeBaseline("b".repeat(40), false));

        assertThat(evidence.verify(run).failures()).isEmpty();
        Path summary = run.resolve(RetrievalAnswerEvidence.SUMMARY_FILENAME);
        Files.writeString(summary, "changed summary\n");
        assertThat(evidence.verify(run).failures()).isNotEmpty();
        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(evidence.verify(run).failures()).isEmpty();
        assertThat(Files.readString(summary)).contains("# Retrieval Answer Generation");

        Files.writeString(run.resolve(RetrievalAnswerProtocol.RAW_FILENAME), "{}\n");
        assertThat(evidence.reanalyze(run).failures()).isNotEmpty();
    }

    @Test
    void preservesFractionalTimeoutsInTheDeterministicSummary() {
        RetrievalAnswerResult original = successfulResult();
        RetrievalAnswerResult fractionalTimeout = new RetrievalAnswerResult(
                original.protocolVersion(), original.suite(), original.startedAt(), original.finishedAt(),
                original.executionEngine(), original.executionStrategy(), original.sourceEvidence(),
                original.retrievalEvidence(), original.prompt(), original.modelIdentity(),
                RetrievalAnswerProtocol.settings(42, 128, Duration.ofMillis(1_500)), original.rows());

        assertThat(new RetrievalAnswerReport().render(
                fractionalTimeout,
                new RetrievalAnswerAnalyzer.Analysis(List.of()),
                RetrievalAnswerProtocol.RAW_FILENAME,
                "f".repeat(64),
                new EvidenceCodeBaseline("b".repeat(40), false)))
                .contains("timeout `PT1.5S`");
    }

    @Test
    void restrictsNewAnswerEvidenceToTheDedicatedDatedRoot() {
        assertThat(RetrievalAnswerRunner.resolveNewOutputDirectory(
                "local/evidence/retrieval-answer/2026-08-28-r5").getFileName().toString())
                .isEqualTo("2026-08-28-r5");
        assertThatThrownBy(() -> RetrievalAnswerRunner.resolveNewOutputDirectory(
                "local/evidence/elsewhere/2026-08-28-r5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-answer");
        assertThatThrownBy(() -> RetrievalAnswerRunner.resolveNewOutputDirectory(
                "build/retrieval-answer/2026-08-28-r5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-answer");
        assertThatThrownBy(() -> RetrievalAnswerRunner.resolveNewOutputDirectory(
                "local/evidence/retrieval-answer/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    private static RetrievalAnswerResult successfulResult() {
        return execute((index, request) -> {
            RetrievalEvaluationRetrievedDocument first = retrieval().rows().get(index).retrievedDocuments().stream()
                    .findFirst().orElse(null);
            return successful(first == null ? "NO_SUPPORT" : "Answer [" + first.documentId() + "]", index + 1);
        }, new ArrayList<>());
    }

    private static RetrievalAnswerResult execute(
            BiFunction<Integer, ChatInvocationRequest, ChatInvocationOutcome> outcomes,
            List<ChatInvocationRequest> requests
    ) {
        AtomicInteger index = new AtomicInteger();
        ChatInvocation invocation = request -> {
            requests.add(request);
            return outcomes.apply(index.getAndIncrement(), request);
        };
        return new RetrievalAnswerExecutor(invocation).execute(
                sourceEvidence(),
                retrieval(),
                RetrievalAnswerPromptDefinition.load(),
                MODEL_IDENTITY,
                settings(),
                CHAT_IDENTITY);
    }

    private static RetrievalEvaluationResult retrieval() {
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        return new RetrievalEvaluationExecutor(new DeterministicLexicalRetriever())
                .execute(inputs.corpus(), inputs.catalog());
    }

    private static RetrievalAnswerEvidence evidence() {
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        return new RetrievalAnswerEvidence(JsonMapper.builder().findAndAddModules().build(), inputs.corpus(), inputs.catalog());
    }

    private static RetrievalAnswerSourceEvidence sourceEvidence() {
        return new RetrievalAnswerSourceEvidence("2026-08-28-r3", "c".repeat(64), "d".repeat(64), "e".repeat(40));
    }

    private static RetrievalAnswerRunSettings settings() {
        return RetrievalAnswerProtocol.settings(42, 128, Duration.ofMinutes(2));
    }

    private static ChatInvocationOutcome successful(String answer, int number) {
        return new ChatInvocationOutcome(
                CHAT_IDENTITY,
                ChatProviderOptionSupport.supportsAll(),
                "retrieval-grounded-answer-v1",
                true,
                answer,
                "response_" + number,
                10,
                3,
                13,
                1L,
                1,
                ChatInvocationFailureCategory.NONE,
                null);
    }

    private static ChatInvocationOutcome empty(int number) {
        return new ChatInvocationOutcome(
                CHAT_IDENTITY,
                ChatProviderOptionSupport.supportsAll(),
                "retrieval-grounded-answer-v1",
                true,
                null,
                "response_" + number,
                null,
                null,
                null,
                1L,
                1,
                ChatInvocationFailureCategory.EMPTY_RESPONSE,
                null);
    }

    private static ChatInvocationOutcome failed(ChatInvocationFailureCategory category, int number) {
        return new ChatInvocationOutcome(
                CHAT_IDENTITY,
                ChatProviderOptionSupport.supportsAll(),
                "retrieval-grounded-answer-v1",
                false,
                null,
                "response_" + number,
                null,
                null,
                null,
                1L,
                1,
                category,
                "provider diagnostic");
    }
}
