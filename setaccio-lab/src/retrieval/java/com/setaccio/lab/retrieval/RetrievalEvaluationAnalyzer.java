package com.setaccio.lab.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates R3 raw evidence and derives retrieval-only metrics from its locked fixture labels. */
final class RetrievalEvaluationAnalyzer {

    private final RetrievalCorpus corpus;
    private final RetrievalQueryCatalog catalog;
    private final DeterministicLexicalRetriever retriever;
    private final Map<String, RetrievalDocument> documentsById;

    RetrievalEvaluationAnalyzer(
            RetrievalCorpus corpus,
            RetrievalQueryCatalog catalog,
            DeterministicLexicalRetriever retriever
    ) {
        if (corpus == null || catalog == null || retriever == null) {
            throw new IllegalArgumentException("corpus, catalog, and retriever must not be null");
        }
        this.corpus = corpus.requireApprovedPublicSafe();
        this.catalog = catalog.requireConfirmed();
        this.retriever = retriever;
        documentsById = this.corpus.documents().stream().collect(Collectors.toMap(
                RetrievalDocument::documentId,
                document -> document,
                (first, ignored) -> first));
    }

    Analysis analyze(RetrievalEvaluationResult result) {
        LinkedHashSet<String> failures = new LinkedHashSet<>();
        if (result == null) {
            return new Analysis(0, 0, 0, 0, 0, 0, 0, 0, List.of("Retrieval evaluation result must not be null."));
        }
        validateEnvelope(result, failures);
        validateRows(result, failures);
        Metrics metrics = metrics(result.rows());
        return new Analysis(
                metrics.matchingFixtures(),
                metrics.expectedRetrieved(),
                metrics.expectedTop1(),
                metrics.expectedTop3(),
                metrics.forbiddenRetrieved(),
                metrics.noMatchFixtures(),
                metrics.correctNoMatch(),
                metrics.stableRows(),
                List.copyOf(failures));
    }

    private void validateEnvelope(RetrievalEvaluationResult result, Set<String> failures) {
        if (result.protocolVersion() != RetrievalEvaluationProtocol.VERSION) {
            failures.add("Retrieval evaluation protocol version is not 1.");
        }
        if (!RetrievalEvaluationProtocol.SUITE.equals(result.suite())) {
            failures.add("Retrieval evaluation suite is not public-safe-retrieval-evaluation.");
        }
        if (result.startedAt() == null || result.finishedAt() == null
                || result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Retrieval evaluation timestamps are missing or out of order.");
        }
        if (!RetrievalEvaluationProtocol.EXECUTION_ENGINE.equals(result.executionEngine())) {
            failures.add("Retrieval evaluation execution engine is not the locked lexical baseline.");
        }
        if (!RetrievalEvaluationProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Retrieval evaluation execution strategy is not the locked sequential repeatability check.");
        }
        if (!Objects.equals(corpus.catalogId(), result.corpusCatalogId())
                || corpus.catalogVersion() != result.corpusCatalogVersion()
                || !Objects.equals(corpus.catalogSha256(), result.corpusCatalogSha256())) {
            failures.add("Retrieval evaluation corpus identity differs from the approved corpus.");
        }
        if (!Objects.equals(catalog.catalogId(), result.queryCatalogId())
                || catalog.catalogVersion() != result.queryCatalogVersion()
                || !Objects.equals(catalog.catalogSha256(), result.queryCatalogSha256())) {
            failures.add("Retrieval evaluation query-catalog identity differs from the confirmed catalog.");
        }
        if (!Objects.equals(DeterministicLexicalRetriever.parameters(), result.lexicalParameters())) {
            failures.add("Retrieval evaluation lexical parameters differ from the locked baseline.");
        }
    }

    private void validateRows(RetrievalEvaluationResult result, Set<String> failures) {
        List<RetrievalEvaluationRow> rows = result.rows();
        if (rows.size() != catalog.fixtures().size()) {
            failures.add("Retrieval evaluation must contain exactly " + catalog.fixtures().size() + " rows.");
        }
        int rowsToInspect = Math.min(rows.size(), catalog.fixtures().size());
        for (int index = 0; index < rowsToInspect; index++) {
            RetrievalEvaluationRow row = rows.get(index);
            RetrievalQueryFixture fixture = catalog.fixtures().get(index);
            if (row == null) {
                failures.add("Retrieval evaluation row " + (index + 1) + " must not be null.");
                continue;
            }
            validateFixtureIdentity(row, fixture, index + 1, failures);
            validateRetrieval(row, fixture, index + 1, failures);
        }
    }

    private static void validateFixtureIdentity(
            RetrievalEvaluationRow row,
            RetrievalQueryFixture fixture,
            int sequence,
            Set<String> failures
    ) {
        if (row.sequence() != sequence) {
            failures.add("Retrieval evaluation row sequence is not contiguous at " + sequence + ".");
        }
        if (!Objects.equals(row.caseId(), fixture.caseId())
                || !Objects.equals(row.query(), fixture.query())
                || !Objects.equals(row.expectedSupportingDocumentIds(), fixture.expectedSupportingDocumentIds())
                || !Objects.equals(row.allowedSupportingDocumentIds(), fixture.allowedSupportingDocumentIds())
                || !Objects.equals(row.forbiddenDocumentIds(), fixture.forbiddenDocumentIds())
                || row.expectedNoMatch() != fixture.expectedNoMatch()) {
            failures.add("Retrieval evaluation row does not match confirmed fixture " + fixture.caseId() + ".");
        }
    }

    private void validateRetrieval(
            RetrievalEvaluationRow row,
            RetrievalQueryFixture fixture,
            int sequence,
            Set<String> failures
    ) {
        if (!row.stableAcrossImmediateRepeat()) {
            failures.add("Retrieval evaluation row is not stable across its immediate repeat: " + fixture.caseId() + ".");
        }
        RetrievalLexicalResult expected = retriever.retrieve(corpus, fixture);
        if (!Objects.equals(row.retrieval(), expected)) {
            failures.add("Saved retrieval result differs from deterministic reanalysis for " + fixture.caseId() + ".");
            return;
        }
        List<RetrievalLexicalHit> hits = row.retrieval().hits();
        for (int hitIndex = 0; hitIndex < hits.size(); hitIndex++) {
            RetrievalLexicalHit hit = hits.get(hitIndex);
            if (hit.rank() != hitIndex + 1) {
                failures.add("Retrieval hit ranks are not contiguous for row " + sequence + ".");
            }
            RetrievalDocument document = documentsById.get(hit.documentId());
            if (document == null || !Objects.equals(document.contentSha256(), hit.contentSha256())) {
                failures.add("Retrieval hit document identity is not in the approved corpus for " + fixture.caseId() + ".");
            }
        }
        long distinctHitIds = hits.stream().map(RetrievalLexicalHit::documentId).distinct().count();
        if (distinctHitIds != hits.size()) {
            failures.add("Retrieval result contains duplicate document IDs for " + fixture.caseId() + ".");
        }
        List<RetrievalEvaluationRetrievedDocument> expectedDocuments = hits.stream()
                .map(hit -> capturedDocument(hit, documentsById.get(hit.documentId())))
                .toList();
        if (!Objects.equals(row.retrievedDocuments(), expectedDocuments)) {
            failures.add("Saved retrieved document content differs from the approved corpus for " + fixture.caseId() + ".");
        }
    }

    private static RetrievalEvaluationRetrievedDocument capturedDocument(
            RetrievalLexicalHit hit,
            RetrievalDocument document
    ) {
        if (document == null) {
            return null;
        }
        return new RetrievalEvaluationRetrievedDocument(
                hit.rank(),
                hit.documentId(),
                hit.contentSha256(),
                document.content(),
                hit.matchedTermCount(),
                hit.retainedQueryTermCount(),
                hit.matchedTerms());
    }

    private Metrics metrics(List<RetrievalEvaluationRow> rows) {
        int matchingFixtures = 0;
        int expectedRetrieved = 0;
        int expectedTop1 = 0;
        int expectedTop3 = 0;
        int forbiddenRetrieved = 0;
        int noMatchFixtures = 0;
        int correctNoMatch = 0;
        int stableRows = 0;

        for (RetrievalEvaluationRow row : rows) {
            if (row == null) {
                continue;
            }
            if (row.stableAcrossImmediateRepeat()) {
                stableRows++;
            }
            List<String> retrieved = row.retrieval() == null
                    ? List.of()
                    : row.retrievedDocuments().stream()
                            .map(RetrievalEvaluationRetrievedDocument::documentId)
                            .toList();
            if (row.expectedNoMatch()) {
                noMatchFixtures++;
                if (retrieved.isEmpty()) {
                    correctNoMatch++;
                }
            } else {
                matchingFixtures++;
                if (retrieved.containsAll(row.expectedSupportingDocumentIds())) {
                    expectedRetrieved++;
                }
                if (retrieved.stream().limit(1).toList().containsAll(row.expectedSupportingDocumentIds())) {
                    expectedTop1++;
                }
                if (retrieved.stream().limit(3).toList().containsAll(row.expectedSupportingDocumentIds())) {
                    expectedTop3++;
                }
            }
            if (retrieved.stream().anyMatch(row.forbiddenDocumentIds()::contains)) {
                forbiddenRetrieved++;
            }
        }
        return new Metrics(
                matchingFixtures,
                expectedRetrieved,
                expectedTop1,
                expectedTop3,
                forbiddenRetrieved,
                noMatchFixtures,
                correctNoMatch,
                stableRows);
    }

    record Analysis(
            int matchingFixtures,
            int expectedSupportingDocumentsRetrieved,
            int expectedSupportingDocumentsInTop1,
            int expectedSupportingDocumentsInTop3,
            int forbiddenDocumentRetrievedFixtures,
            int noMatchFixtures,
            int correctNoMatchFixtures,
            int stableRows,
            List<String> integrityFailures
    ) {

        Analysis {
            integrityFailures = integrityFailures == null ? List.of() : List.copyOf(integrityFailures);
        }

        boolean valid() {
            return integrityFailures.isEmpty();
        }

        int totalFixtures() {
            return matchingFixtures + noMatchFixtures;
        }
    }

    private record Metrics(
            int matchingFixtures,
            int expectedRetrieved,
            int expectedTop1,
            int expectedTop3,
            int forbiddenRetrieved,
            int noMatchFixtures,
            int correctNoMatch,
            int stableRows
    ) {}
}
