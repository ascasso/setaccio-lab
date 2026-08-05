package com.setaccio.lab.chatmatrix;

record ChatPromptIdentity(String id, String sha256) {
    ChatPromptIdentity {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Chat prompt identity ID must not be blank");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chat prompt identity SHA-256 must be a full lowercase digest");
        }
    }
}
