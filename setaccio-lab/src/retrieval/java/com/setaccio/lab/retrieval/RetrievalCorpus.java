package com.setaccio.lab.retrieval;

import java.util.List;

/**
 * A validated, versioned retrieval corpus and its catalog identity.
 */
public record RetrievalCorpus(
        String catalogId,
        int catalogVersion,
        String catalogSha256,
        RetrievalPrivacyReviewState privacyReviewState,
        List<RetrievalDocument> documents
) {

    public RetrievalCorpus {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /**
     * Rejects a corpus until a human has approved both its catalog and every
     * document as public-safe.
     *
     * @return this approved corpus
     */
    public RetrievalCorpus requireApprovedPublicSafe() {
        if (privacyReviewState != RetrievalPrivacyReviewState.APPROVED_PUBLIC_SAFE
                || documents.stream().anyMatch(document ->
                document.privacyReviewState() != RetrievalPrivacyReviewState.APPROVED_PUBLIC_SAFE)) {
            throw new IllegalStateException(
                    "Retrieval corpus is not approved for formal use by a human public-safety review");
        }
        return this;
    }
}
