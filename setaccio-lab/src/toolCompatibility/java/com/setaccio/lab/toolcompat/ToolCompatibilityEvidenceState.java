package com.setaccio.lab.toolcompat;

/** Four-state lifecycle evidence used where a nullable boolean would be ambiguous. */
enum ToolCompatibilityEvidenceState {
    NOT_REACHED,
    UNOBSERVABLE,
    SUCCEEDED,
    FAILED
}
