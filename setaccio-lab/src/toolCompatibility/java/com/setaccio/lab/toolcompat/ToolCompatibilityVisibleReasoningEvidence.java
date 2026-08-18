package com.setaccio.lab.toolcompat;

/** Deterministic observations of visible reasoning-style markers in assistant text. */
record ToolCompatibilityVisibleReasoningEvidence(
        boolean thinkTagDetected,
        boolean markerDetectedAnywhere,
        boolean markerDetectedBeforeFirstToolCall,
        boolean markerDetectedAfterToolExecution,
        boolean visibleReasoningTextInFinalOutput
) {

    ToolCompatibilityVisibleReasoningEvidence {
        if (thinkTagDetected && !markerDetectedAnywhere) {
            throw new IllegalArgumentException("a think tag is also a visible reasoning marker");
        }
        if ((markerDetectedBeforeFirstToolCall
                        || markerDetectedAfterToolExecution
                        || visibleReasoningTextInFinalOutput)
                && !markerDetectedAnywhere) {
            throw new IllegalArgumentException(
                    "lifecycle-specific visible reasoning evidence requires a marker");
        }
    }
}
