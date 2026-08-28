package com.setaccio.lab.retrieval;

/** Deterministic observation of the answer's bracketed document-reference syntax. */
public enum RetrievalAnswerReferenceBehavior {
    NOT_OBSERVED,
    EXPLICIT_ABSTENTION,
    RETRIEVED_DOCUMENT_REFERENCES_ONLY,
    NO_DOCUMENT_REFERENCE,
    UNRETRIEVED_DOCUMENT_REFERENCE,
    MALFORMED_DOCUMENT_REFERENCE
}
