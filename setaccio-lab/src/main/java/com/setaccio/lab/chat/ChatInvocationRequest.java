package com.setaccio.lab.chat;

import java.util.Objects;

public record ChatInvocationRequest(
        ChatProviderModelIdentity modelIdentity,
        ChatInvocationPrompt prompt,
        ChatGenerationSettings settings
) {
    public ChatInvocationRequest {
        modelIdentity = Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        prompt = Objects.requireNonNull(prompt, "prompt must not be null");
        settings = Objects.requireNonNull(settings, "settings must not be null");
    }
}
