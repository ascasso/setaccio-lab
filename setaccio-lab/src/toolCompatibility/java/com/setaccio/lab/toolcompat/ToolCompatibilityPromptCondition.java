package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

enum ToolCompatibilityPromptCondition {

    UNTREATED("untreated", ToolCompatibilitySystemPromptIdentity.UNTREATED_ID),
    PROMPTED("prompted", ToolCompatibilitySystemPromptIdentity.DISCIPLINE_ID);

    private final String wireValue;
    private final String promptId;

    ToolCompatibilityPromptCondition(String wireValue, String promptId) {
        this.wireValue = wireValue;
        this.promptId = promptId;
    }

    ToolCompatibilitySystemPromptIdentity prompt(ToolCompatibilitySystemPromptCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("system-prompt catalog must not be null");
        }
        ToolCompatibilitySystemPromptIdentity identity = catalog.requirePrompt(promptId);
        if (this == UNTREATED) {
            identity.requireUntreated();
        } else {
            identity.requireToolDiscipline();
        }
        return identity;
    }

    @JsonValue
    String wireValue() {
        return wireValue;
    }

    @JsonCreator
    static ToolCompatibilityPromptCondition fromWireValue(String value) {
        for (ToolCompatibilityPromptCondition condition : values()) {
            if (condition.wireValue.equals(value)) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown tool compatibility prompt condition: " + value);
    }
}
