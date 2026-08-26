package com.setaccio.lab.retrieval;

/**
 * One validated document from the versioned retrieval corpus.
 *
 * <p>The {@code content} is the exact UTF-8 text whose SHA-256 identity is
 * recorded in {@code contentSha256}; later retrieval evidence must retain that
 * identity rather than substituting fixture context.</p>
 */
public record RetrievalDocument(
        String documentId,
        String relativePath,
        String contentSha256,
        String title,
        String topic,
        RetrievalDocumentSource sourceType,
        RetrievalPrivacyReviewState privacyReviewState,
        String content
) {}
