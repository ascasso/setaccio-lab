package com.setaccio.lab.retrieval;

import java.util.List;

/** One saved retrieval-only row with human-confirmed fixture labels and ranked document identities. */
public record RetrievalEvaluationRow(
        int sequence,
        String caseId,
        String query,
        List<String> expectedSupportingDocumentIds,
        List<String> allowedSupportingDocumentIds,
        List<String> forbiddenDocumentIds,
        boolean expectedNoMatch,
        RetrievalLexicalResult retrieval,
        List<RetrievalEvaluationRetrievedDocument> retrievedDocuments,
        boolean stableAcrossImmediateRepeat
) {

    public RetrievalEvaluationRow {
        expectedSupportingDocumentIds = immutable(expectedSupportingDocumentIds);
        allowedSupportingDocumentIds = immutable(allowedSupportingDocumentIds);
        forbiddenDocumentIds = immutable(forbiddenDocumentIds);
        retrievedDocuments = retrievedDocuments == null ? List.of() : List.copyOf(retrievedDocuments);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
