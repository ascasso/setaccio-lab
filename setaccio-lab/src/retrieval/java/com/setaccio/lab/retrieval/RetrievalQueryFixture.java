package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * One validated retrieval-only question and its document relevance labels.
 *
 * <p>The fixture deliberately contains no expected generated answer.</p>
 */
public record RetrievalQueryFixture(
        String caseId,
        String query,
        List<String> expectedSupportingDocumentIds,
        List<String> allowedSupportingDocumentIds,
        List<String> forbiddenDocumentIds,
        boolean expectedNoMatch,
        RetrievalQueryReviewState humanReviewState
) {

    public RetrievalQueryFixture {
        expectedSupportingDocumentIds = immutable(expectedSupportingDocumentIds);
        allowedSupportingDocumentIds = immutable(allowedSupportingDocumentIds);
        forbiddenDocumentIds = immutable(forbiddenDocumentIds);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
