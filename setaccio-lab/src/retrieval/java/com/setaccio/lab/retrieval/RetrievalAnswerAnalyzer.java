package com.setaccio.lab.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Validates that R5 preserves its verified retrieval input and keeps answer observations separate. */
final class RetrievalAnswerAnalyzer {

    private final RetrievalEvaluationAnalyzer retrievalAnalyzer;
    private final RetrievalAnswerPromptDefinition promptDefinition;

    RetrievalAnswerAnalyzer(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        retrievalAnalyzer = new RetrievalEvaluationAnalyzer(corpus, catalog, new DeterministicLexicalRetriever());
        promptDefinition = RetrievalAnswerPromptDefinition.load();
    }

    Analysis analyze(RetrievalAnswerResult result) {
        List<String> failures = new ArrayList<>();
        if (result == null) {
            return new Analysis(List.of("Retrieval answer result must not be null."));
        }
        if (result.protocolVersion() != RetrievalAnswerProtocol.VERSION
                || !RetrievalAnswerProtocol.SUITE.equals(result.suite())
                || !RetrievalAnswerProtocol.EXECUTION_ENGINE.equals(result.executionEngine())
                || !RetrievalAnswerProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Retrieval answer result does not use the locked R5 protocol.");
        }
        if (result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Retrieval answer finished before it started.");
        }
        RetrievalEvaluationAnalyzer.Analysis retrieval = retrievalAnalyzer.analyze(result.retrievalEvidence());
        failures.addAll(retrieval.integrityFailures());
        if (!promptDefinition.contract().equals(result.prompt())) {
            failures.add("Retrieval answer prompt identity differs from the tracked v1 prompt.");
        }
        validateSettings(result.runSettings(), failures);
        if (result.rows().size() != result.retrievalEvidence().rows().size()) {
            failures.add("Retrieval answer row count differs from the verified retrieval evidence.");
        }
        int count = Math.min(result.rows().size(), result.retrievalEvidence().rows().size());
        for (int index = 0; index < count; index++) {
            validateRow(result.rows().get(index), result.retrievalEvidence().rows().get(index), result, failures);
        }
        return new Analysis(List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateRow(
            RetrievalAnswerRow row,
            RetrievalEvaluationRow expectedRetrieval,
            RetrievalAnswerResult result,
            List<String> failures
    ) {
        if (!row.retrieval().equals(expectedRetrieval) || row.sequence() != expectedRetrieval.sequence()) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " does not retain its exact R3 retrieval row.");
        }
        String expectedPrompt = promptDefinition.render(expectedRetrieval.query(), expectedRetrieval.retrievedDocuments());
        if (!expectedPrompt.equals(row.renderedPrompt())) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " does not retain the exact rendered prompt.");
        }
        RetrievalAnswerInvocationOutcome invocation = row.invocation();
        if (!result.modelIdentity().equals(invocation.modelIdentity())
                || !result.prompt().promptId().equals(invocation.promptId())
                || !result.prompt().promptSha256().equals(invocation.promptSha256())) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " model or prompt identity drifted.");
        }
        RetrievalAnswerReferenceAnalysis expectedReferences = RetrievalAnswerExecutor.analyzeReferences(
                invocation.answerText(), expectedRetrieval.retrievedDocuments(), invocation.successful());
        if (!expectedReferences.equals(row.referenceAnalysis())) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " reference analysis is not reproducible.");
        }
        boolean expectedAbstention = invocation.successful() && "NO_SUPPORT".equals(invocation.answerText().strip());
        if (expectedAbstention != row.explicitAbstentionObserved()) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " explicit abstention observation is not reproducible.");
        }
        if (row.unsupportedAssertionAssessment() != RetrievalAnswerSupportAssessment.NOT_ASSESSED) {
            failures.add("Answer row " + expectedRetrieval.sequence() + " must not claim an R5 support assessment.");
        }
    }

    private static void validateSettings(RetrievalAnswerRunSettings settings, List<String> failures) {
        if (!RetrievalAnswerProtocol.PROVIDER.equals(settings.provider())
                || !RetrievalAnswerProtocol.ENDPOINT_CATEGORY.equals(settings.endpointCategory())
                || settings.temperature() != RetrievalAnswerProtocol.TEMPERATURE
                || settings.maxAttempts() != RetrievalAnswerProtocol.MAX_ATTEMPTS
                || !RetrievalAnswerProtocol.PULL_MODEL_STRATEGY.equals(settings.pullModelStrategy())) {
            failures.add("Retrieval answer run settings differ from the locked local R5 contract.");
        }
    }

    record Analysis(List<String> integrityFailures) {
        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }
}
