package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * A validated, corpus-bound catalog of ordered retrieval query fixtures.
 */
public record RetrievalQueryCatalog(
        String catalogId,
        int catalogVersion,
        String catalogSha256,
        String corpusCatalogId,
        int corpusCatalogVersion,
        String corpusCatalogSha256,
        RetrievalQueryReviewState humanReviewState,
        List<RetrievalQueryFixture> fixtures
) {

    public RetrievalQueryCatalog {
        fixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
    }

    /**
     * Rejects a query catalog until a human has confirmed every relevance
     * judgment against the exact bound corpus.
     *
     * @return this confirmed catalog
     */
    public RetrievalQueryCatalog requireConfirmed() {
        if (humanReviewState != RetrievalQueryReviewState.CONFIRMED
                || fixtures.stream().anyMatch(fixture ->
                fixture.humanReviewState() != RetrievalQueryReviewState.CONFIRMED)) {
            throw new IllegalStateException(
                    "Retrieval query catalog is not confirmed by a human content review");
        }
        return this;
    }
}
