package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Executes the fixed R3 fixture order without provider, network, or model access. */
final class RetrievalEvaluationExecutor {

    private final DeterministicLexicalRetriever retriever;

    RetrievalEvaluationExecutor(DeterministicLexicalRetriever retriever) {
        if (retriever == null) {
            throw new IllegalArgumentException("retriever must not be null");
        }
        this.retriever = retriever;
    }

    RetrievalEvaluationResult execute(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        if (corpus == null) {
            throw new IllegalArgumentException("corpus must not be null");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        RetrievalCorpus approvedCorpus = corpus.requireApprovedPublicSafe();
        RetrievalQueryCatalog confirmedCatalog = catalog.requireConfirmed();
        Map<String, RetrievalDocument> documentsById = approvedCorpus.documents().stream()
                .collect(Collectors.toMap(RetrievalDocument::documentId, document -> document));
        Instant startedAt = Instant.now();
        List<RetrievalEvaluationRow> rows = new ArrayList<>(confirmedCatalog.fixtures().size());
        int sequence = 1;
        for (RetrievalQueryFixture fixture : confirmedCatalog.fixtures()) {
            RetrievalLexicalResult first = retriever.retrieve(approvedCorpus, fixture);
            RetrievalLexicalResult immediateRepeat = retriever.retrieve(approvedCorpus, fixture);
            rows.add(new RetrievalEvaluationRow(
                    sequence++,
                    fixture.caseId(),
                    fixture.query(),
                    fixture.expectedSupportingDocumentIds(),
                    fixture.allowedSupportingDocumentIds(),
                    fixture.forbiddenDocumentIds(),
                    fixture.expectedNoMatch(),
                    first,
                    first.hits().stream().map(hit -> capture(hit, documentsById)).toList(),
                    first.equals(immediateRepeat)));
        }
        return new RetrievalEvaluationResult(
                RetrievalEvaluationProtocol.VERSION,
                RetrievalEvaluationProtocol.SUITE,
                startedAt,
                Instant.now(),
                RetrievalEvaluationProtocol.EXECUTION_ENGINE,
                RetrievalEvaluationProtocol.EXECUTION_STRATEGY,
                approvedCorpus.catalogId(),
                approvedCorpus.catalogVersion(),
                approvedCorpus.catalogSha256(),
                confirmedCatalog.catalogId(),
                confirmedCatalog.catalogVersion(),
                confirmedCatalog.catalogSha256(),
                DeterministicLexicalRetriever.parameters(),
                rows);
    }

    private static RetrievalEvaluationRetrievedDocument capture(
            RetrievalLexicalHit hit,
            Map<String, RetrievalDocument> documentsById
    ) {
        RetrievalDocument document = documentsById.get(hit.documentId());
        if (document == null) {
            throw new IllegalStateException("Lexical result references an unknown corpus document: " + hit.documentId());
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
}
