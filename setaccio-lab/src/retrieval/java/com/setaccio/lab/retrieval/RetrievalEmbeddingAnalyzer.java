package com.setaccio.lab.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Offline integrity analysis for saved R4 vectors and cosine-ranked retrieval rows. */
final class RetrievalEmbeddingAnalyzer {

    private static final double UNIT_NORM_TOLERANCE = 0.0001;
    private static final double SCORE_TOLERANCE = 0.000000000001;

    private final RetrievalCorpus corpus;
    private final RetrievalQueryCatalog catalog;

    RetrievalEmbeddingAnalyzer(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        if (corpus == null || catalog == null) {
            throw new IllegalArgumentException("corpus and catalog must not be null");
        }
        this.corpus = corpus.requireApprovedPublicSafe();
        this.catalog = catalog.requireConfirmed();
    }

    Analysis analyze(RetrievalEmbeddingResult result) {
        LinkedHashSet<String> failures = new LinkedHashSet<>();
        if (result == null) {
            return new Analysis(List.of("Retrieval embedding result must not be null."));
        }
        validateEnvelope(result, failures);
        validateProviderMetadata(result, failures);
        Map<String, RetrievalEmbeddingDocumentVector> documentVectors = validateDocumentVectors(result, failures);
        Map<String, RetrievalEmbeddingQueryVector> queryVectors = validateQueryVectors(result, failures);
        validateRows(result, documentVectors, queryVectors, failures);
        return new Analysis(List.copyOf(failures));
    }

    private void validateEnvelope(RetrievalEmbeddingResult result, Set<String> failures) {
        if (result.protocolVersion() != RetrievalEmbeddingProtocol.VERSION) {
            failures.add("Retrieval embedding protocol version is not 1.");
        }
        if (!RetrievalEmbeddingProtocol.SUITE.equals(result.suite())) {
            failures.add("Retrieval embedding suite is not public-safe-retrieval-embedding.");
        }
        if (result.startedAt() == null || result.finishedAt() == null
                || result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Retrieval embedding timestamps are missing or out of order.");
        }
        if (!RetrievalEmbeddingProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Retrieval embedding execution strategy is not single-batch-one-attempt.");
        }
        if (!RetrievalEmbeddingProtocol.PULL_MODEL_STRATEGY.equals(result.pullModelStrategy())) {
            failures.add("Retrieval embedding pull strategy is not never.");
        }
        if (!Objects.equals(corpus.catalogId(), result.corpusCatalogId())
                || corpus.catalogVersion() != result.corpusCatalogVersion()
                || !Objects.equals(corpus.catalogSha256(), result.corpusCatalogSha256())) {
            failures.add("Retrieval embedding corpus identity differs from the approved corpus.");
        }
        if (!Objects.equals(catalog.catalogId(), result.queryCatalogId())
                || catalog.catalogVersion() != result.queryCatalogVersion()
                || !Objects.equals(catalog.catalogSha256(), result.queryCatalogSha256())) {
            failures.add("Retrieval embedding query-catalog identity differs from the confirmed catalog.");
        }
        if (result.runSettings() == null) {
            failures.add("Retrieval embedding run settings are missing.");
        } else {
            try {
                if (!RetrievalEmbeddingProtocol.settings(result.runSettings().topK()).equals(result.runSettings())) {
                    failures.add("Retrieval embedding run settings differ from the locked protocol.");
                }
            } catch (Exception exception) {
                failures.add("Retrieval embedding run settings are invalid.");
            }
            if (result.runSettings().topK() > corpus.documents().size()) {
                failures.add("Retrieval embedding topK exceeds the approved corpus document count.");
            }
        }
        if (result.modelIdentity() == null) {
            failures.add("Retrieval embedding model identity is missing.");
        }
        if (result.vectorDimension() < 1) {
            failures.add("Retrieval embedding vector dimension must be positive.");
        }
    }

    private static void validateProviderMetadata(RetrievalEmbeddingResult result, Set<String> failures) {
        if (negative(result.providerTotalDurationNanos())) {
            failures.add("Retrieval embedding provider total duration must not be negative.");
        }
        if (negative(result.providerLoadDurationNanos())) {
            failures.add("Retrieval embedding provider load duration must not be negative.");
        }
        if (negative(result.providerPromptEvalCount())) {
            failures.add("Retrieval embedding provider prompt-evaluation count must not be negative.");
        }
    }

    private Map<String, RetrievalEmbeddingDocumentVector> validateDocumentVectors(
            RetrievalEmbeddingResult result,
            Set<String> failures
    ) {
        if (result.documentVectors().size() != corpus.documents().size()) {
            failures.add("Retrieval embedding must retain one vector for every approved corpus document.");
        }
        int count = Math.min(result.documentVectors().size(), corpus.documents().size());
        LinkedHashMap<String, RetrievalEmbeddingDocumentVector> byId = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            RetrievalEmbeddingDocumentVector vector = result.documentVectors().get(index);
            RetrievalDocument document = corpus.documents().get(index);
            if (vector == null) {
                failures.add("Retrieval embedding document vector " + (index + 1) + " must not be null.");
                continue;
            }
            boolean identityMatches = Objects.equals(vector.documentId(), document.documentId())
                    && Objects.equals(vector.contentSha256(), document.contentSha256());
            if (!identityMatches) {
                failures.add("Retrieval embedding document vector identity differs from the approved corpus at "
                        + (index + 1) + ".");
            }
            boolean normalized = validateNormalizedVector(vector.values(), result.vectorDimension(),
                    "document " + document.documentId(), failures);
            if (identityMatches && normalized && byId.put(document.documentId(), vector) != null) {
                failures.add("Retrieval embedding contains duplicate document vector ID: " + vector.documentId() + ".");
            }
        }
        return Map.copyOf(byId);
    }

    private Map<String, RetrievalEmbeddingQueryVector> validateQueryVectors(
            RetrievalEmbeddingResult result,
            Set<String> failures
    ) {
        if (result.queryVectors().size() != catalog.fixtures().size()) {
            failures.add("Retrieval embedding must retain one vector for every confirmed query fixture.");
        }
        int count = Math.min(result.queryVectors().size(), catalog.fixtures().size());
        LinkedHashMap<String, RetrievalEmbeddingQueryVector> byCaseId = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            RetrievalEmbeddingQueryVector vector = result.queryVectors().get(index);
            RetrievalQueryFixture fixture = catalog.fixtures().get(index);
            if (vector == null) {
                failures.add("Retrieval embedding query vector " + (index + 1) + " must not be null.");
                continue;
            }
            boolean identityMatches = Objects.equals(vector.caseId(), fixture.caseId())
                    && Objects.equals(vector.querySha256(), RetrievalEmbeddingExecutor.sha256(fixture.query()));
            if (!identityMatches) {
                failures.add("Retrieval embedding query vector identity differs from confirmed fixture "
                        + fixture.caseId() + ".");
            }
            boolean normalized = validateNormalizedVector(vector.values(), result.vectorDimension(),
                    "query " + fixture.caseId(), failures);
            if (identityMatches && normalized && byCaseId.put(fixture.caseId(), vector) != null) {
                failures.add("Retrieval embedding contains duplicate query vector caseId: " + vector.caseId() + ".");
            }
        }
        return Map.copyOf(byCaseId);
    }

    private void validateRows(
            RetrievalEmbeddingResult result,
            Map<String, RetrievalEmbeddingDocumentVector> documentVectors,
            Map<String, RetrievalEmbeddingQueryVector> queryVectors,
            Set<String> failures
    ) {
        if (result.rows().size() != catalog.fixtures().size()) {
            failures.add("Retrieval embedding must contain one retrieval row for every confirmed query fixture.");
        }
        int count = Math.min(result.rows().size(), catalog.fixtures().size());
        int topK = result.runSettings() == null ? 0 : result.runSettings().topK();
        if (topK < 1 || topK > corpus.documents().size()) {
            return;
        }
        for (int index = 0; index < count; index++) {
            RetrievalEmbeddingRow row = result.rows().get(index);
            RetrievalQueryFixture fixture = catalog.fixtures().get(index);
            if (row == null) {
                failures.add("Retrieval embedding row " + (index + 1) + " must not be null.");
                continue;
            }
            if (row.sequence() != index + 1
                    || !Objects.equals(row.caseId(), fixture.caseId())
                    || !Objects.equals(row.querySha256(), RetrievalEmbeddingExecutor.sha256(fixture.query()))) {
                failures.add("Retrieval embedding row does not match confirmed fixture " + fixture.caseId() + ".");
            }
            RetrievalEmbeddingQueryVector query = queryVectors.get(fixture.caseId());
            if (query == null || documentVectors.size() != corpus.documents().size()) {
                continue;
            }
            List<RetrievalEmbeddingHit> expected = expectedHits(query, documentVectors, topK);
            if (!sameHits(row.hits(), expected)) {
                failures.add("Retrieval embedding ranks or cosine scores differ from saved vectors for "
                        + fixture.caseId() + ".");
            }
        }
    }

    private static List<RetrievalEmbeddingHit> expectedHits(
            RetrievalEmbeddingQueryVector query,
            Map<String, RetrievalEmbeddingDocumentVector> documents,
            int topK
    ) {
        List<ScoredDocument> ranked = documents.values().stream()
                .map(document -> new ScoredDocument(
                        document,
                        RetrievalEmbeddingExecutor.cosine(query.values(), document.values())))
                .sorted(Comparator.comparingDouble(ScoredDocument::similarity).reversed()
                        .thenComparing(scored -> scored.document().documentId()))
                .limit(topK)
                .toList();
        List<RetrievalEmbeddingHit> hits = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            ScoredDocument scored = ranked.get(index);
            hits.add(new RetrievalEmbeddingHit(
                    index + 1,
                    scored.document().documentId(),
                    scored.document().contentSha256(),
                    scored.similarity()));
        }
        return List.copyOf(hits);
    }

    private static boolean sameHits(List<RetrievalEmbeddingHit> actual, List<RetrievalEmbeddingHit> expected) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            RetrievalEmbeddingHit actualHit = actual.get(index);
            RetrievalEmbeddingHit expectedHit = expected.get(index);
            if (actualHit == null
                    || actualHit.rank() != expectedHit.rank()
                    || !Objects.equals(actualHit.documentId(), expectedHit.documentId())
                    || !Objects.equals(actualHit.contentSha256(), expectedHit.contentSha256())
                    || !Double.isFinite(actualHit.cosineSimilarity())
                    || Math.abs(actualHit.cosineSimilarity() - expectedHit.cosineSimilarity()) > SCORE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateNormalizedVector(
            List<Float> values,
            int dimension,
            String label,
            Set<String> failures
    ) {
        if (values == null || values.size() != dimension) {
            failures.add("Retrieval embedding vector dimension differs for " + label + ".");
            return false;
        }
        double sumOfSquares = 0.0;
        for (Float value : values) {
            if (value == null || !Float.isFinite(value)) {
                failures.add("Retrieval embedding vector contains a non-finite value for " + label + ".");
                return false;
            }
            sumOfSquares += (double) value * value;
        }
        if (!Double.isFinite(sumOfSquares) || Math.abs(Math.sqrt(sumOfSquares) - 1.0) > UNIT_NORM_TOLERANCE) {
            failures.add("Retrieval embedding vector is not unit-L2 normalized for " + label + ".");
            return false;
        }
        return true;
    }

    private static boolean negative(Number value) {
        return value != null && value.longValue() < 0;
    }

    record Analysis(List<String> integrityFailures) {

        Analysis {
            integrityFailures = integrityFailures == null ? List.of() : List.copyOf(integrityFailures);
        }

        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }

    private record ScoredDocument(RetrievalEmbeddingDocumentVector document, double similarity) {}
}
