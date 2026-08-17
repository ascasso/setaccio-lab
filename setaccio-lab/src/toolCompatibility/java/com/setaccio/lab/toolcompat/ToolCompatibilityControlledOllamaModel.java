package com.setaccio.lab.toolcompat;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Temporary T1.3 construction result that keeps the verified installed identity
 * attached to the controlled model without defining a persisted result row.
 */
record ToolCompatibilityControlledOllamaModel(
        ChatModel chatModel,
        ToolCompatibilityModelIdentity modelIdentity
) {

    ToolCompatibilityControlledOllamaModel {
        Objects.requireNonNull(chatModel, "chatModel must not be null");
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
    }
}
