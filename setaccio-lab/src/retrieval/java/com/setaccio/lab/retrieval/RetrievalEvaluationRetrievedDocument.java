package com.setaccio.lab.retrieval;

import java.util.List;

/** A complete retrieved corpus document preserved with its lexical ranking evidence. */
public record RetrievalEvaluationRetrievedDocument(
        int rank,
        String documentId,
        String contentSha256,
        String content,
        int matchedTermCount,
        int retainedQueryTermCount,
        List<String> matchedTerms
) {

    public RetrievalEvaluationRetrievedDocument {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
