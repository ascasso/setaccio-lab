package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * R3 fixture expectation retained beside R6 output. It deliberately makes no claim about an
 * answer's support or correctness.
 */
public record RetrievalRelevancyDeterministicExpectation(
        String caseId,
        List<String> expectedSupportingDocumentIds,
        List<String> allowedSupportingDocumentIds,
        List<String> forbiddenDocumentIds,
        boolean expectedNoMatch
) {

    public RetrievalRelevancyDeterministicExpectation {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        expectedSupportingDocumentIds = copy(expectedSupportingDocumentIds);
        allowedSupportingDocumentIds = copy(allowedSupportingDocumentIds);
        forbiddenDocumentIds = copy(forbiddenDocumentIds);
    }

    static RetrievalRelevancyDeterministicExpectation from(RetrievalEvaluationRow row) {
        return new RetrievalRelevancyDeterministicExpectation(
                row.caseId(),
                row.expectedSupportingDocumentIds(),
                row.allowedSupportingDocumentIds(),
                row.forbiddenDocumentIds(),
                row.expectedNoMatch());
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
