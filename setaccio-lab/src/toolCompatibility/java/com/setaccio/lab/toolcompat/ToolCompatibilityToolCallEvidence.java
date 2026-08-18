package com.setaccio.lab.toolcompat;

record ToolCompatibilityToolCallEvidence(
        int sequence,
        int providerTurnSequence,
        String callId,
        String type,
        String toolName,
        String rawArgumentJson,
        ToolCompatibilityEvidenceState rawArgumentJsonState,
        ToolCompatibilityEvidenceState declaredSchemaState,
        ToolCompatibilitySchemaIssue rawArgumentIssue,
        Integer expectedCallSequence,
        ToolCompatibilityEvidenceState expectedCallAtSequenceState,
        ToolCompatibilityEvidenceState expectedArgumentsState,
        ToolCompatibilityEvidenceState callbackBindingState,
        ToolCompatibilityEvidenceState callbackExecutionState,
        Integer toolResponseSequence
) {

    ToolCompatibilityToolCallEvidence {
        if (sequence < 1 || providerTurnSequence < 1) {
            throw new IllegalArgumentException("tool-call sequences must be positive");
        }
        callId = requireText(callId, "callId");
        if (type != null) {
            type = requireText(type, "type");
        }
        toolName = requireText(toolName, "toolName");
        requireState(rawArgumentJsonState, "rawArgumentJsonState");
        requireState(declaredSchemaState, "declaredSchemaState");
        requireState(expectedCallAtSequenceState, "expectedCallAtSequenceState");
        requireState(expectedArgumentsState, "expectedArgumentsState");
        requireState(callbackBindingState, "callbackBindingState");
        requireState(callbackExecutionState, "callbackExecutionState");
        if (rawArgumentJsonState != ToolCompatibilityEvidenceState.SUCCEEDED
                && rawArgumentJsonState != ToolCompatibilityEvidenceState.FAILED) {
            throw new IllegalArgumentException("raw JSON parsing must have succeeded or failed");
        }
        if (rawArgumentJsonState == ToolCompatibilityEvidenceState.FAILED
                && (declaredSchemaState != ToolCompatibilityEvidenceState.NOT_REACHED
                        || rawArgumentIssue != ToolCompatibilitySchemaIssue.MALFORMED_JSON)) {
            throw new IllegalArgumentException("malformed JSON must prevent declared-schema validation");
        }
        if (rawArgumentJsonState == ToolCompatibilityEvidenceState.SUCCEEDED
                && declaredSchemaState == ToolCompatibilityEvidenceState.NOT_REACHED) {
            throw new IllegalArgumentException("valid JSON must have an observed or unobservable schema outcome");
        }
        if (declaredSchemaState == ToolCompatibilityEvidenceState.FAILED
                && (rawArgumentIssue == null
                        || rawArgumentIssue == ToolCompatibilitySchemaIssue.MALFORMED_JSON
                        || rawArgumentIssue == ToolCompatibilitySchemaIssue.UNSUPPORTED_SCHEMA)) {
            throw new IllegalArgumentException("failed schema evidence requires a supported mismatch category");
        }
        if ((declaredSchemaState == ToolCompatibilityEvidenceState.SUCCEEDED
                        || declaredSchemaState == ToolCompatibilityEvidenceState.UNOBSERVABLE)
                && rawArgumentIssue != null) {
            throw new IllegalArgumentException("passing or unobservable schema evidence must not contain an issue");
        }
        if (expectedCallSequence != null && expectedCallSequence < 1) {
            throw new IllegalArgumentException("expectedCallSequence must be positive when present");
        }
        if (expectedCallAtSequenceState != ToolCompatibilityEvidenceState.SUCCEEDED
                && expectedCallAtSequenceState != ToolCompatibilityEvidenceState.FAILED) {
            throw new IllegalArgumentException("each observed call must have an expected-sequence outcome");
        }
        if (expectedArgumentsState == ToolCompatibilityEvidenceState.UNOBSERVABLE) {
            throw new IllegalArgumentException("semantic argument comparison is never unobservable");
        }
        if (expectedArgumentsState != ToolCompatibilityEvidenceState.NOT_REACHED
                && (expectedCallSequence == null
                        || rawArgumentJsonState != ToolCompatibilityEvidenceState.SUCCEEDED)) {
            throw new IllegalArgumentException(
                    "semantic argument comparison requires expected and parsed arguments");
        }
        if (callbackBindingState == ToolCompatibilityEvidenceState.NOT_REACHED
                && callbackExecutionState != ToolCompatibilityEvidenceState.NOT_REACHED) {
            throw new IllegalArgumentException("callback execution cannot precede binding");
        }
        if (callbackBindingState == ToolCompatibilityEvidenceState.FAILED
                && callbackExecutionState != ToolCompatibilityEvidenceState.NOT_REACHED) {
            throw new IllegalArgumentException("failed binding must prevent callback execution");
        }
        if ((callbackExecutionState == ToolCompatibilityEvidenceState.SUCCEEDED
                        || callbackExecutionState == ToolCompatibilityEvidenceState.FAILED)
                && callbackBindingState != ToolCompatibilityEvidenceState.SUCCEEDED
                && callbackBindingState != ToolCompatibilityEvidenceState.UNOBSERVABLE) {
            throw new IllegalArgumentException("observed callback execution requires completed binding");
        }
        if ((callbackBindingState == ToolCompatibilityEvidenceState.FAILED
                        || callbackExecutionState == ToolCompatibilityEvidenceState.SUCCEEDED
                        || callbackExecutionState == ToolCompatibilityEvidenceState.FAILED)
                && toolResponseSequence == null) {
            throw new IllegalArgumentException("completed callback outcome requires linked response evidence");
        }
        if (toolResponseSequence != null && toolResponseSequence < 1) {
            throw new IllegalArgumentException("toolResponseSequence must be positive when present");
        }
    }

    private static ToolCompatibilityEvidenceState requireState(
            ToolCompatibilityEvidenceState value,
            String field
    ) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
