package com.setaccio.lab.toolcompat;

/** Deterministic observations of visible reasoning-style markers in assistant text. */
record ToolCompatibilityVisibleReasoningEvidence(
        boolean thinkTagDetected,
        boolean markerDetectedAnywhere,
        boolean otherReasoningMarkerDetected,
        boolean markerDetectedBeforeFirstToolCall,
        boolean markerDetectedAfterToolExecution,
        boolean visibleReasoningTextInFinalOutput
) {

    ToolCompatibilityVisibleReasoningEvidence {
        if (thinkTagDetected && !markerDetectedAnywhere) {
            throw new IllegalArgumentException("a think tag is also a visible reasoning marker");
        }
        if ((otherReasoningMarkerDetected
                        || markerDetectedBeforeFirstToolCall
                        || markerDetectedAfterToolExecution
                        || visibleReasoningTextInFinalOutput)
                && !markerDetectedAnywhere) {
            throw new IllegalArgumentException(
                    "lifecycle-specific visible reasoning evidence requires a marker");
        }
    }
}
