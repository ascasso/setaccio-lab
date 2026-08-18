package com.setaccio.lab.toolcompat;

/** Typed holder for the primary deterministic category populated by later analysis slices. */
record ToolCompatibilityDiagnostic(String category) {

    ToolCompatibilityDiagnostic {
        if (category == null
                || !category.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("diagnostic category must be an uppercase stable identifier");
        }
    }
}
