package com.setaccio.lab.toolcompat;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;

/** Keeps one cohort identity and its actual seed handling attached to a controlled model. */
record ToolCompatibilityCohortControlledOllamaModel(
        ChatModel chatModel,
        ToolCompatibilityCohortModelIdentity modelIdentity,
        Integer effectiveSeed
) {

    ToolCompatibilityCohortControlledOllamaModel {
        Objects.requireNonNull(chatModel, "chatModel must not be null");
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        if (modelIdentity.seedSemantics() == ToolCompatibilityCohortSeedSemantics.SUPPORTED
                && effectiveSeed == null) {
            throw new IllegalArgumentException("seed-supported cohort model requires an explicit seed");
        }
        if (modelIdentity.seedSemantics() == ToolCompatibilityCohortSeedSemantics.UNSUPPORTED
                && effectiveSeed != null) {
            throw new IllegalArgumentException("seed-unsupported cohort model must not simulate a seed");
        }
        if (effectiveSeed != null && !ToolCompatibilityProtocol.SEEDS.contains(effectiveSeed)) {
            throw new IllegalArgumentException("effective seed is outside the locked protocol");
        }
    }
}
