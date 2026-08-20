package com.setaccio.lab.toolcompat;

record ToolCompatibilityToolResponseEvidence(
        int sequence,
        int toolCallSequence,
        String callId,
        String toolName,
        ToolCompatibilityEvidenceState callbackResultState,
        String responseData,
        ToolCompatibilityFailure failure
) {

    ToolCompatibilityToolResponseEvidence {
        if (sequence < 1 || toolCallSequence < 1) {
            throw new IllegalArgumentException("tool-response sequences must be positive");
        }
        callId = requireText(callId, "callId");
        toolName = requireText(toolName, "toolName");
        if (callbackResultState != ToolCompatibilityEvidenceState.SUCCEEDED
                && callbackResultState != ToolCompatibilityEvidenceState.FAILED) {
            throw new IllegalArgumentException("a callback response must have succeeded or failed");
        }
        if (callbackResultState == ToolCompatibilityEvidenceState.SUCCEEDED && failure != null) {
            throw new IllegalArgumentException("successful callback response must not contain a failure");
        }
        if (callbackResultState == ToolCompatibilityEvidenceState.FAILED
                && (failure == null
                        || ToolCompatibilityFailure.PROVIDER_FAILURE.equals(failure.category())
                        || ToolCompatibilityFailure.ROW_TIMEOUT.equals(failure.category()))) {
            throw new IllegalArgumentException("failed callback response requires a callback failure");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
