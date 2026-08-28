package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * Complete locked parameters for the version-one deterministic lexical
 * baseline.
 */
public record RetrievalLexicalParameters(
        String methodId,
        int methodVersion,
        String tokenizerId,
        String lowercaseLocale,
        String tokenPattern,
        String stopWordsId,
        List<String> stopWords,
        int maximumDocumentFrequency,
        int minimumMatchedTerms,
        int minimumCoverageNumerator,
        int minimumCoverageDenominator,
        String tieBreak
) {

    public RetrievalLexicalParameters {
        stopWords = stopWords == null ? List.of() : List.copyOf(stopWords);
    }
}
