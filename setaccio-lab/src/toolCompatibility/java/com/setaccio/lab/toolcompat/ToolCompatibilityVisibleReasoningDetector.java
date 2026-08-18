package com.setaccio.lab.toolcompat;

import java.util.List;
import java.util.Locale;

/**
 * Pure detector for the locked visible reasoning-style markers in assistant
 * output. These observations do not establish a model's private reasoning.
 */
final class ToolCompatibilityVisibleReasoningDetector {

    private static final String OPEN_THINK_TAG = "<think>";
    private static final String CLOSE_THINK_TAG = "</think>";
    private static final String THINKING_PREFIX = "thinking...";
    private static final String DONE_THINKING = "...done thinking.";
    private static final String THINKING_PROCESS = "here's a thinking process:";

    ToolCompatibilityVisibleReasoningEvidence detect(
            List<ToolCompatibilityProviderTurnEvidence> providerTurns,
            List<ToolCompatibilityToolCallEvidence> toolCalls
    ) {
        providerTurns = List.copyOf(providerTurns == null ? List.of() : providerTurns);
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);

        Integer firstToolCallTurn = toolCalls.stream()
                .map(ToolCompatibilityToolCallEvidence::providerTurnSequence)
                .min(Integer::compareTo)
                .orElse(null);
        Integer firstExecutedToolTurn = toolCalls.stream()
                .filter(ToolCompatibilityVisibleReasoningDetector::executionWasAttempted)
                .map(ToolCompatibilityToolCallEvidence::providerTurnSequence)
                .min(Integer::compareTo)
                .orElse(null);
        Integer finalOutputTurn = providerTurns.stream()
                .filter(turn -> turn.invocationState() == ToolCompatibilityEvidenceState.SUCCEEDED)
                .filter(turn -> turn.orderedToolCallIds().isEmpty())
                .map(ToolCompatibilityProviderTurnEvidence::sequence)
                .reduce((left, right) -> right)
                .orElse(null);

        boolean thinkTagDetected = false;
        boolean markerDetectedAnywhere = false;
        boolean otherReasoningMarkerDetected = false;
        boolean markerDetectedBeforeFirstToolCall = false;
        boolean markerDetectedAfterToolExecution = false;
        boolean visibleReasoningTextInFinalOutput = false;
        for (ToolCompatibilityProviderTurnEvidence turn : providerTurns) {
            TextDetection textDetection = detectText(turn.assistantText());
            thinkTagDetected |= textDetection.thinkTagDetected();
            otherReasoningMarkerDetected |= textDetection.otherReasoningMarkerDetected();
            if (!textDetection.markerDetected()) {
                continue;
            }
            markerDetectedAnywhere = true;
            markerDetectedBeforeFirstToolCall |= firstToolCallTurn != null
                    && turn.sequence() <= firstToolCallTurn;
            markerDetectedAfterToolExecution |= firstExecutedToolTurn != null
                    && turn.sequence() > firstExecutedToolTurn;
            visibleReasoningTextInFinalOutput |= finalOutputTurn != null
                    && turn.sequence() == finalOutputTurn;
        }
        return new ToolCompatibilityVisibleReasoningEvidence(
                thinkTagDetected,
                markerDetectedAnywhere,
                otherReasoningMarkerDetected,
                markerDetectedBeforeFirstToolCall,
                markerDetectedAfterToolExecution,
                visibleReasoningTextInFinalOutput);
    }

    private static boolean executionWasAttempted(ToolCompatibilityToolCallEvidence call) {
        return call.callbackExecutionState() == ToolCompatibilityEvidenceState.SUCCEEDED
                || call.callbackExecutionState() == ToolCompatibilityEvidenceState.FAILED;
    }

    private static TextDetection detectText(String text) {
        if (text == null || text.isEmpty()) {
            return new TextDetection(false, false, false);
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        boolean thinkTagDetected = normalized.contains(OPEN_THINK_TAG)
                || normalized.contains(CLOSE_THINK_TAG);
        boolean otherReasoningMarkerDetected = containsAtWordBoundary(normalized, THINKING_PREFIX)
                || normalized.contains(DONE_THINKING)
                || containsAtWordBoundary(normalized, THINKING_PROCESS);
        return new TextDetection(
                thinkTagDetected,
                thinkTagDetected || otherReasoningMarkerDetected,
                otherReasoningMarkerDetected);
    }

    private static boolean containsAtWordBoundary(String text, String marker) {
        int index = text.indexOf(marker);
        while (index >= 0) {
            if (index == 0 || !isWordCharacter(text.charAt(index - 1))) {
                return true;
            }
            index = text.indexOf(marker, index + 1);
        }
        return false;
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private record TextDetection(
            boolean thinkTagDetected,
            boolean markerDetected,
            boolean otherReasoningMarkerDetected
    ) {}
}
