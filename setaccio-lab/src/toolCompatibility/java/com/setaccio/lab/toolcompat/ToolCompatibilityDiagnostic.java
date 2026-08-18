package com.setaccio.lab.toolcompat;

/** Typed holder for a deterministic diagnostic category. */
record ToolCompatibilityDiagnostic(String category) {

    static final String VISIBLE_REASONING_TEXT = "VISIBLE_REASONING_TEXT";

    ToolCompatibilityDiagnostic {
        if (category == null
                || !category.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("diagnostic category must be an uppercase stable identifier");
        }
    }
}
