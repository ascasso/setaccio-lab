package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatInvocationPrompt;

record ChatPromptCase(String id, String text, String sha256) {

    ChatPromptCase {
        if (id == null || id.isBlank() || !id.equals(id.strip())) {
            throw new IllegalArgumentException("Chat prompt ID must be nonblank and trimmed");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Chat prompt text must not be blank");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chat prompt SHA-256 must be a full lowercase digest");
        }
    }

    ChatInvocationPrompt invocationPrompt() {
        return new ChatInvocationPrompt(id, text);
    }
}
