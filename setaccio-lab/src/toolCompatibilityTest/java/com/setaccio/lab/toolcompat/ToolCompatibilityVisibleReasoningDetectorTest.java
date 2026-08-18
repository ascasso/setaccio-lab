package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCompatibilityVisibleReasoningDetectorTest {

    private final ToolCompatibilityVisibleReasoningDetector detector =
            new ToolCompatibilityVisibleReasoningDetector();

    @ParameterizedTest
    @ValueSource(strings = {
        "<think>",
        "</think>",
        "Thinking...",
        "...done thinking.",
        "Here's a thinking process:"
    })
    void detectsEveryLockedMarkerInFinalAssistantOutput(String marker) {
        ToolCompatibilityVisibleReasoningEvidence evidence = detector.detect(
                List.of(turn(1, "prefix " + marker + " suffix", List.of())),
                List.of());

        assertThat(evidence.markerDetectedAnywhere()).isTrue();
        assertThat(evidence.otherReasoningMarkerDetected())
                .isEqualTo(!marker.startsWith("<"));
        assertThat(evidence.visibleReasoningTextInFinalOutput()).isTrue();
        assertThat(evidence.markerDetectedBeforeFirstToolCall()).isFalse();
        assertThat(evidence.markerDetectedAfterToolExecution()).isFalse();
        assertThat(evidence.thinkTagDetected()).isEqualTo(marker.contains("think>"));
    }

    @Test
    void recordsMarkersOnTheFirstToolRequestTurnAndAfterAnExecutedTool() {
        ToolCompatibilityVisibleReasoningEvidence evidence = detector.detect(
                List.of(
                        turn(1, "Thinking... selecting a tool", List.of("call-1")),
                        turn(2, "...done thinking. requesting another tool", List.of("call-2")),
                        turn(3, "Here's a thinking process: final answer", List.of())),
                List.of(executedCall(1, 1, "call-1"), executedCall(2, 2, "call-2")));

        assertThat(evidence.markerDetectedAnywhere()).isTrue();
        assertThat(evidence.markerDetectedBeforeFirstToolCall()).isTrue();
        assertThat(evidence.markerDetectedAfterToolExecution()).isTrue();
        assertThat(evidence.visibleReasoningTextInFinalOutput()).isTrue();
    }

    @Test
    void doesNotTreatNearMissesAsLockedMarkers() {
        List<String> nearMisses = List.of(
                "<thinking>",
                "&lt;think&gt;",
                "Thinking about which tool to use...",
                "The model is done thinking through the answer.",
                "Here is a thinking process:",
                "Rethinking... the selection");

        assertThat(nearMisses)
                .allSatisfy(text -> assertThat(detector.detect(
                                List.of(turn(1, text, List.of())), List.of()))
                        .isEqualTo(none()));
    }

    @Test
    void handlesNullEmptyAndBlankAssistantText() {
        assertThat(detector.detect(null, null)).isEqualTo(none());
        assertThat(detector.detect(
                        List.of(
                                turn(1, null, List.of("call-1")),
                                turn(2, "", List.of("call-2")),
                                turn(3, "   ", List.of())),
                        List.of(executedCall(1, 1, "call-1"), executedCall(2, 2, "call-2"))))
                .isEqualTo(none());
    }

    @Test
    void requiresAnAttemptedExecutionBeforeClassifyingLaterTextAsPostExecution() {
        ToolCompatibilityToolCallEvidence bindingFailure = new ToolCompatibilityToolCallEvidence(
                1,
                1,
                "call-1",
                "function",
                "lab_add_numbers",
                "{}",
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.UNOBSERVABLE,
                null,
                1,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.FAILED,
                ToolCompatibilityEvidenceState.NOT_REACHED,
                1);

        ToolCompatibilityVisibleReasoningEvidence evidence = detector.detect(
                List.of(
                        turn(1, null, List.of("call-1")),
                        turn(2, "Thinking... after binding failed", List.of())),
                List.of(bindingFailure));

        assertThat(evidence.markerDetectedAnywhere()).isTrue();
        assertThat(evidence.markerDetectedAfterToolExecution()).isFalse();
        assertThat(evidence.visibleReasoningTextInFinalOutput()).isTrue();
    }

    private static ToolCompatibilityVisibleReasoningEvidence none() {
        return new ToolCompatibilityVisibleReasoningEvidence(false, false, false, false, false, false);
    }

    private static ToolCompatibilityProviderTurnEvidence turn(
            int sequence,
            String assistantText,
            List<String> callIds
    ) {
        return new ToolCompatibilityProviderTurnEvidence(
                sequence,
                assistantText,
                callIds,
                "turn-" + sequence,
                "fake-model",
                Map.of(),
                callIds.isEmpty() ? "stop" : "tool_calls",
                ToolCompatibilityTokenUsageEvidence.observed(null, null, null),
                ToolCompatibilityOutputLimitState.UNOBSERVABLE,
                Duration.ZERO,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                null);
    }

    private static ToolCompatibilityToolCallEvidence executedCall(
            int sequence,
            int providerTurnSequence,
            String callId
    ) {
        return new ToolCompatibilityToolCallEvidence(
                sequence,
                providerTurnSequence,
                callId,
                "function",
                "lab_add_numbers",
                "{}",
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.UNOBSERVABLE,
                null,
                sequence,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                sequence);
    }
}
