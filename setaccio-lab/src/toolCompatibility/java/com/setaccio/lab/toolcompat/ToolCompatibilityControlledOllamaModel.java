package com.setaccio.lab.toolcompat;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;

/** Keeps the verified installed identity attached to one controlled seeded model. */
record ToolCompatibilityControlledOllamaModel(
        ChatModel chatModel,
        ToolCompatibilityModelIdentity modelIdentity
) {

    ToolCompatibilityControlledOllamaModel {
        Objects.requireNonNull(chatModel, "chatModel must not be null");
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
    }
}
