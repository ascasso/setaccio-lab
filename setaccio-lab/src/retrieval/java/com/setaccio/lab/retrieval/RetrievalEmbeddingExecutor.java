package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes one explicit batch of document and query embeddings, then ranks top-K cosine hits. */
final class RetrievalEmbeddingExecutor {

    private final RetrievalEmbeddingClient client;

    RetrievalEmbeddingExecutor(RetrievalEmbeddingClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    RetrievalEmbeddingResult execute(
            RetrievalCorpus corpus,
            RetrievalQueryCatalog catalog,
            RetrievalEmbeddingRunSettings settings,
            RetrievalEmbeddingModelIdentity modelIdentity
    ) {
        if (corpus == null || catalog == null || settings == null || modelIdentity == null) {
            throw new IllegalArgumentException("corpus, catalog, settings, and modelIdentity must not be null");
        }
        RetrievalCorpus approvedCorpus = corpus.requireApprovedPublicSafe();
        RetrievalQueryCatalog confirmedCatalog = catalog.requireConfirmed();
        if (settings.topK() > approvedCorpus.documents().size()) {
            throw new IllegalArgumentException("topK must not exceed the approved corpus document count");
        }
        List<String> inputs = new ArrayList<>(approvedCorpus.documents().size() + confirmedCatalog.fixtures().size());
        approvedCorpus.documents().forEach(document -> inputs.add(document.content()));
        confirmedCatalog.fixtures().forEach(fixture -> inputs.add(fixture.query()));

        Instant startedAt = Instant.now();
        RetrievalEmbeddingClient.EmbeddingResponse response = client.embed(modelIdentity, inputs);
        Instant finishedAt = Instant.now();
        if (response == null) {
            throw new IllegalStateException("Ollama embedding provider returned no response");
        }
        if (!modelIdentity.effectiveModel().equals(response.effectiveModel())) {
            throw new IllegalStateException("Ollama embedding response model differs from the preflight identity");
        }
        if (response.vectors().size() != inputs.size()) {
            throw new IllegalStateException("Ollama embedding response vector count differs from requested inputs");
        }

        List<List<Float>> normalized = response.vectors().stream().map(RetrievalEmbeddingExecutor::normalize).toList();
        int dimension = normalized.getFirst().size();
        if (normalized.stream().anyMatch(vector -> vector.size() != dimension)) {
            throw new IllegalStateException("Ollama embedding response vectors do not share one dimension");
        }

        List<RetrievalEmbeddingDocumentVector> documentVectors = new ArrayList<>(approvedCorpus.documents().size());
        Map<String, RetrievalEmbeddingDocumentVector> documentsById = new LinkedHashMap<>();
        for (int index = 0; index < approvedCorpus.documents().size(); index++) {
            RetrievalDocument document = approvedCorpus.documents().get(index);
            RetrievalEmbeddingDocumentVector vector = new RetrievalEmbeddingDocumentVector(
                    document.documentId(), document.contentSha256(), normalized.get(index));
            documentVectors.add(vector);
            documentsById.put(document.documentId(), vector);
        }
        List<RetrievalEmbeddingQueryVector> queryVectors = new ArrayList<>(confirmedCatalog.fixtures().size());
        for (int index = 0; index < confirmedCatalog.fixtures().size(); index++) {
            RetrievalQueryFixture fixture = confirmedCatalog.fixtures().get(index);
            queryVectors.add(new RetrievalEmbeddingQueryVector(
                    fixture.caseId(),
                    sha256(fixture.query()),
                    normalized.get(approvedCorpus.documents().size() + index)));
        }
        List<RetrievalEmbeddingRow> rows = rank(
                confirmedCatalog.fixtures(), queryVectors, documentsById, settings.topK());
        return new RetrievalEmbeddingResult(
                RetrievalEmbeddingProtocol.VERSION,
                RetrievalEmbeddingProtocol.SUITE,
                startedAt,
                finishedAt,
                RetrievalEmbeddingProtocol.EXECUTION_STRATEGY,
                RetrievalEmbeddingProtocol.PULL_MODEL_STRATEGY,
                settings,
                modelIdentity,
                approvedCorpus.catalogId(),
                approvedCorpus.catalogVersion(),
                approvedCorpus.catalogSha256(),
                confirmedCatalog.catalogId(),
                confirmedCatalog.catalogVersion(),
                confirmedCatalog.catalogSha256(),
                dimension,
                response.totalDurationNanos(),
                response.loadDurationNanos(),
                response.promptEvalCount(),
                documentVectors,
                queryVectors,
                rows);
    }

    private static List<RetrievalEmbeddingRow> rank(
            List<RetrievalQueryFixture> fixtures,
            List<RetrievalEmbeddingQueryVector> queryVectors,
            Map<String, RetrievalEmbeddingDocumentVector> documentsById,
            int topK
    ) {
        List<RetrievalEmbeddingRow> rows = new ArrayList<>(fixtures.size());
        for (int index = 0; index < fixtures.size(); index++) {
            RetrievalQueryFixture fixture = fixtures.get(index);
            RetrievalEmbeddingQueryVector query = queryVectors.get(index);
            List<ScoredDocument> ranked = documentsById.values().stream()
                    .map(document -> new ScoredDocument(document, cosine(query.values(), document.values())))
                    .sorted(Comparator.comparingDouble(ScoredDocument::similarity).reversed()
                            .thenComparing(scored -> scored.document().documentId()))
                    .limit(topK)
                    .toList();
            List<RetrievalEmbeddingHit> hits = new ArrayList<>(ranked.size());
            for (int rank = 0; rank < ranked.size(); rank++) {
                ScoredDocument scored = ranked.get(rank);
                hits.add(new RetrievalEmbeddingHit(
                        rank + 1,
                        scored.document().documentId(),
                        scored.document().contentSha256(),
                        scored.similarity()));
            }
            rows.add(new RetrievalEmbeddingRow(index + 1, fixture.caseId(), query.querySha256(), hits));
        }
        return List.copyOf(rows);
    }

    static List<Float> normalize(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }
        double sumOfSquares = 0.0;
        for (Float value : vector) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding vector must contain only finite values");
            }
            sumOfSquares += (double) value * value;
        }
        if (!Double.isFinite(sumOfSquares) || sumOfSquares <= 0.0) {
            throw new IllegalArgumentException("embedding vector must have non-zero finite L2 norm");
        }
        double norm = Math.sqrt(sumOfSquares);
        List<Float> normalized = new ArrayList<>(vector.size());
        for (Float value : vector) {
            normalized.add((float) (value / norm));
        }
        return List.copyOf(normalized);
    }

    static double cosine(List<Float> left, List<Float> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            throw new IllegalArgumentException("embedding vectors must have one non-empty shared dimension");
        }
        double similarity = 0.0;
        for (int index = 0; index < left.size(); index++) {
            similarity += (double) left.get(index) * right.get(index);
        }
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return EvidenceIntegrity.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record ScoredDocument(RetrievalEmbeddingDocumentVector document, double similarity) {}
}
