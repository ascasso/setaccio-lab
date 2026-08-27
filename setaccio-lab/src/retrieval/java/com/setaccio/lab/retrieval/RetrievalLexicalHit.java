package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * One ranked lexical retrieval hit with an exact rational score.
 */
public record RetrievalLexicalHit(
        int rank,
        String documentId,
        String contentSha256,
        int matchedTermCount,
        int retainedQueryTermCount,
        List<String> matchedTerms
) {

    public RetrievalLexicalHit {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }

    /**
     * Returns the display form of the exact score. Ranking and thresholds use
     * integer fields directly and never depend on floating-point rounding.
     */
    public double score() {
        return retainedQueryTermCount == 0
                ? 0.0
                : (double) matchedTermCount / retainedQueryTermCount;
    }
}
