package com.setaccio.lab.retrieval;

/** Safe evaluator response identifiers captured when the provider exposes them. */
public record RetrievalRelevancyResponseMetadata(String responseId, String responseModel) {

    public RetrievalRelevancyResponseMetadata {
        responseId = responseId == null ? "" : responseId;
        responseModel = responseModel == null ? "" : responseModel;
        if (!responseId.isEmpty() && !responseId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("responseId must be a safe opaque identifier");
        }
    }
}
