package com.setaccio.lab.retrieval;

/** Versioned prompt identity retained globally and on every R5 answer row. */
public record RetrievalAnswerPromptContract(String promptId, String promptSha256) {

    public RetrievalAnswerPromptContract {
        if (promptId == null || !promptId.matches("[a-z0-9]+(?:-[a-z0-9]+)*-v[0-9]+")) {
            throw new IllegalArgumentException("promptId must be a versioned lowercase-hyphen identifier");
        }
        if (promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("promptSha256 must be a full lowercase SHA-256 digest");
        }
    }
}
