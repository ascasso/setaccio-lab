package com.setaccio.lab.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Validates R6's preserved R5 source and keeps evaluator output distinct from human judgment. */
final class RetrievalRelevancyAnalyzer {

    private final RetrievalAnswerAnalyzer answerAnalyzer;
    private final RetrievalRelevancyPromptDefinition promptDefinition;

    RetrievalRelevancyAnalyzer(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        answerAnalyzer = new RetrievalAnswerAnalyzer(corpus, catalog);
        promptDefinition = RetrievalRelevancyPromptDefinition.load();
    }

    Analysis analyze(RetrievalRelevancyResult result) {
        List<String> failures = new ArrayList<>();
        if (result == null) {
            return new Analysis(List.of("Retrieval relevancy result must not be null."));
        }
        if (result.protocolVersion() != RetrievalRelevancyProtocol.VERSION
                || !RetrievalRelevancyProtocol.SUITE.equals(result.suite())
                || !RetrievalRelevancyProtocol.EXECUTION_ENGINE.equals(result.executionEngine())
                || !RetrievalRelevancyProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Retrieval relevancy result does not use the locked R6 protocol.");
        }
        if (result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Retrieval relevancy finished before it started.");
        }
        RetrievalAnswerAnalyzer.Analysis answer = answerAnalyzer.analyze(result.answerEvidence());
        failures.addAll(answer.integrityFailures());
        if (!promptDefinition.contract().equals(result.prompt())) {
            failures.add("Retrieval relevancy prompt identity differs from the tracked v1 prompt.");
        }
        validateSettings(result.runSettings(), failures);
        if (result.rows().size() != result.answerEvidence().rows().size()) {
            failures.add("Retrieval relevancy row count differs from the verified R5 answer evidence.");
        }
        int count = Math.min(result.rows().size(), result.answerEvidence().rows().size());
        for (int index = 0; index < count; index++) {
            validateRow(result.rows().get(index), result.answerEvidence().rows().get(index), result, failures);
        }
        return new Analysis(List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static void validateRow(
            RetrievalRelevancyRow row,
            RetrievalAnswerRow expectedAnswer,
            RetrievalRelevancyResult result,
            List<String> failures
    ) {
        int sequence = expectedAnswer.sequence();
        if (row.sequence() != sequence || !row.answer().equals(expectedAnswer)) {
            failures.add("Relevancy row " + sequence + " does not retain its exact R5 answer row.");
        }
        if (!RetrievalRelevancyDeterministicExpectation.from(expectedAnswer.retrieval())
                .equals(row.deterministicExpectation())) {
            failures.add("Relevancy row " + sequence + " deterministic retrieval expectation is not reproducible.");
        }
        if (row.modelRelationship() != RetrievalRelevancyExecutor.relationship(
                expectedAnswer.invocation().modelIdentity(), result.modelIdentity())) {
            failures.add("Relevancy row " + sequence + " self-evaluation relationship is not reproducible.");
        }
        if (row.humanSupportJudgment() != RetrievalRelevancyHumanSupportJudgment.NOT_REVIEWED
                || row.answerCorrectness() != RetrievalRelevancyAnswerCorrectness.NOT_ASSESSED) {
            failures.add("Relevancy row " + sequence + " must not claim a human judgment or answer correctness.");
        }
        validateOutcome(row.evaluatorOutcome(), expectedAnswer, result, failures);
    }

    private static void validateOutcome(
            RetrievalRelevancyEvaluatorOutcome outcome,
            RetrievalAnswerRow answer,
            RetrievalRelevancyResult result,
            List<String> failures
    ) {
        int sequence = answer.sequence();
        if (!result.modelIdentity().equals(outcome.modelIdentity())
                || !result.prompt().promptId().equals(outcome.promptId())
                || !result.prompt().promptSha256().equals(outcome.promptSha256())) {
            failures.add("Relevancy row " + sequence + " evaluator model or prompt identity drifted.");
        }
        if (answer.retrieval().retrievedDocuments().isEmpty()) {
            if (outcome.invocationAttempted()
                    || outcome.diagnosticCategory() != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT) {
                failures.add("Relevancy row " + sequence + " must reject missing retrieved context before evaluation.");
            }
            return;
        }
        if (!answer.invocation().successful()) {
            if (outcome.invocationAttempted()
                    || outcome.diagnosticCategory() != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_NO_ANSWER) {
                failures.add("Relevancy row " + sequence + " must not evaluate an unavailable R5 answer.");
            }
            return;
        }
        if (!outcome.invocationAttempted()) {
            failures.add("Relevancy row " + sequence + " has context and an answer but did not invoke the evaluator.");
            return;
        }
        if (outcome.invocationSucceeded()) {
            if (outcome.normalizedVerdict() == RetrievalRelevancyVerdict.YES
                    && (!Boolean.TRUE.equals(outcome.springEvaluatorPassed())
                    || !Float.valueOf(1.0f).equals(outcome.springEvaluatorScore()))) {
                failures.add("Relevancy row " + sequence + " YES verdict does not match Spring evaluator output.");
            }
            if (outcome.normalizedVerdict() == RetrievalRelevancyVerdict.NO
                    && (Boolean.TRUE.equals(outcome.springEvaluatorPassed())
                    || !Float.valueOf(0.0f).equals(outcome.springEvaluatorScore()))) {
                failures.add("Relevancy row " + sequence + " NO verdict does not match Spring evaluator output.");
            }
        }
    }

    private static void validateSettings(RetrievalRelevancyRunSettings settings, List<String> failures) {
        if (!RetrievalRelevancyProtocol.PROVIDER.equals(settings.provider())
                || !RetrievalRelevancyProtocol.ENDPOINT_CATEGORY.equals(settings.endpointCategory())
                || settings.temperature() != RetrievalRelevancyProtocol.TEMPERATURE
                || settings.maxAttempts() != RetrievalRelevancyProtocol.MAX_ATTEMPTS
                || !RetrievalRelevancyProtocol.PULL_MODEL_STRATEGY.equals(settings.pullModelStrategy())) {
            failures.add("Retrieval relevancy run settings differ from the locked local R6 contract.");
        }
    }

    record Analysis(List<String> integrityFailures) {
        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }
}
