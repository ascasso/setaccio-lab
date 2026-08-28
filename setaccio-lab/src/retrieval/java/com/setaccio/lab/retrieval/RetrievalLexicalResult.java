package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * Deterministic provider-free ranking for one query against one exact corpus.
 */
public record RetrievalLexicalResult(
        String queryId,
        String query,
        String corpusCatalogId,
        int corpusCatalogVersion,
        String corpusCatalogSha256,
        RetrievalLexicalParameters parameters,
        List<String> retainedQueryTerms,
        List<RetrievalLexicalHit> hits
) {

    public RetrievalLexicalResult {
        retainedQueryTerms = retainedQueryTerms == null ? List.of() : List.copyOf(retainedQueryTerms);
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
