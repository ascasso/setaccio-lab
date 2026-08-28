package com.setaccio.lab.retrieval;

import java.util.List;

/** Citation/reference observation kept separate from any semantic answer judgment. */
public record RetrievalAnswerReferenceAnalysis(
        RetrievalAnswerReferenceBehavior behavior,
        List<String> referencedDocumentIds,
        List<String> unretrievedDocumentIds,
        List<String> malformedReferenceTokens
) {

    public RetrievalAnswerReferenceAnalysis {
        if (behavior == null) {
            throw new IllegalArgumentException("behavior must not be null");
        }
        referencedDocumentIds = referencedDocumentIds == null ? List.of() : List.copyOf(referencedDocumentIds);
        unretrievedDocumentIds = unretrievedDocumentIds == null ? List.of() : List.copyOf(unretrievedDocumentIds);
        malformedReferenceTokens = malformedReferenceTokens == null ? List.of() : List.copyOf(malformedReferenceTokens);
    }
}
