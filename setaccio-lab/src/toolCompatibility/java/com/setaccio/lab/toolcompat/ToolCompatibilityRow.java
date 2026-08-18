package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

record ToolCompatibilityRow(
        int sequence,
        String caseId,
        int repetition,
        Integer seed,

        String provider,
        String requestedModel,
        String effectiveModel,
        String modelDigest,

        String systemPromptId,
        int systemPromptVersion,
        String systemPromptSha256,

        double temperature,
        int maxOutputTokensPerProviderTurn,
        Duration rowAttemptDeadline,
        int attemptCount,

        List<ToolCompatibilityProviderTurnEvidence> providerTurns,
        List<ToolCompatibilityToolCallEvidence> toolCalls,
        List<ToolCompatibilityToolResponseEvidence> toolResponses,
        List<ToolBenchmarkAssertion> assertions,

        boolean rowAttemptCompleted,
        boolean exactCallSequenceMatched,
        boolean allExpectedArgumentsMatched,
        boolean finalResponsePresent,
        boolean caseContractPassed,

        String finalAssistantOutput,

        boolean thinkTagDetected,
        boolean reasoningMarkerDetected,
        boolean reasoningMarkerDetectedBeforeFirstToolCall,
        boolean reasoningMarkerDetectedAfterToolExecution,
        boolean visibleReasoningTextInFinalOutput,
        boolean anyProviderTurnReachedOutputLimit,

        ToolCompatibilityTokenUsageEvidence aggregateUsage,
        Duration rowLatency,

        String failureCategory,
        String diagnosticCategory,
        String safeErrorMessage
) {

    ToolCompatibilityRow {
        ToolCompatibilityCaseSelection.ScheduledCase scheduledCase = requireScheduledCase(sequence);
        if (!scheduledCase.caseId().equals(caseId)
                || scheduledCase.repetition() != repetition
                || !Objects.equals(scheduledCase.seed(), seed)) {
            throw new IllegalArgumentException("row identity must match the locked schedule");
        }
        if (!ToolCompatibilityProtocol.PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("provider must equal the locked Ollama provider");
        }
        new ToolCompatibilityModelIdentity(requestedModel, effectiveModel, modelDigest);
        ToolCompatibilitySystemPromptIdentity systemPrompt = ToolCompatibilityProtocol.systemPromptIdentity();
        if (!systemPrompt.id().equals(systemPromptId)
                || systemPrompt.version() != systemPromptVersion
                || !systemPrompt.sha256().equals(systemPromptSha256)) {
            throw new IllegalArgumentException("row system-prompt identity must equal the untreated baseline");
        }
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        if (Double.compare(temperature, settings.temperature()) != 0
                || maxOutputTokensPerProviderTurn != settings.maxOutputTokensPerProviderTurn()
                || rowAttemptDeadline == null
                || !rowAttemptDeadline.equals(Duration.ofMillis(settings.rowTimeoutMillis()))
                || attemptCount != settings.logicalRowAttempts()) {
            throw new IllegalArgumentException("row settings must equal the locked protocol");
        }

        providerTurns = List.copyOf(providerTurns == null ? List.of() : providerTurns);
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        toolResponses = List.copyOf(toolResponses == null ? List.of() : toolResponses);
        assertions = List.copyOf(assertions == null ? List.of() : assertions);
        if (aggregateUsage == null || rowLatency == null || rowLatency.isNegative()) {
            throw new IllegalArgumentException("aggregate usage and non-negative row latency are required");
        }
        Duration minimumLatency = ToolCompatibilityRowAnalyzer.minimumObservedLatency(providerTurns);
        if (rowLatency.compareTo(minimumLatency) < 0) {
            throw new IllegalArgumentException(
                    "row latency cannot be shorter than its ordered provider-turn latency");
        }
        ToolCompatibilityRowAnalyzer.Projection projection = ToolCompatibilityRowAnalyzer.project(
                caseId,
                providerTurns,
                toolCalls,
                toolResponses,
                failureCategory,
                safeErrorMessage);
        ToolCompatibilityVisibleReasoningEvidence visibleReasoning = projection.visibleReasoning();
        String expectedDiagnosticCategory = visibleReasoning.markerDetectedAnywhere()
                ? ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT
                : null;
        if (!assertions.equals(projection.assertions())
                || rowAttemptCompleted != projection.rowAttemptCompleted()
                || exactCallSequenceMatched != projection.exactCallSequenceMatched()
                || allExpectedArgumentsMatched != projection.allExpectedArgumentsMatched()
                || finalResponsePresent != projection.finalResponsePresent()
                || caseContractPassed != projection.caseContractPassed()
                || !Objects.equals(finalAssistantOutput, projection.finalAssistantOutput())
                || thinkTagDetected != visibleReasoning.thinkTagDetected()
                || reasoningMarkerDetected != visibleReasoning.markerDetectedAnywhere()
                || reasoningMarkerDetectedBeforeFirstToolCall
                        != visibleReasoning.markerDetectedBeforeFirstToolCall()
                || reasoningMarkerDetectedAfterToolExecution
                        != visibleReasoning.markerDetectedAfterToolExecution()
                || visibleReasoningTextInFinalOutput
                        != visibleReasoning.visibleReasoningTextInFinalOutput()
                || anyProviderTurnReachedOutputLimit
                        != projection.anyProviderTurnReachedOutputLimit()
                || !aggregateUsage.equals(projection.aggregateUsage())
                || !Objects.equals(diagnosticCategory, expectedDiagnosticCategory)) {
            throw new IllegalArgumentException(
                    "row aggregates must be exact deterministic projections of authoritative evidence");
        }
    }

    @JsonIgnore
    ToolCompatibilityFailure failure() {
        return failureCategory == null
                ? null
                : new ToolCompatibilityFailure(failureCategory, safeErrorMessage);
    }

    @JsonIgnore
    ToolCompatibilityDiagnostic diagnostic() {
        return diagnosticCategory == null ? null : new ToolCompatibilityDiagnostic(diagnosticCategory);
    }

    private static ToolCompatibilityCaseSelection.ScheduledCase requireScheduledCase(int sequence) {
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule = ToolCompatibilityProtocol.schedule(
                ToolCompatibilityProtocol.caseSelection(),
                ToolCompatibilityProtocol.runSettings());
        if (sequence < 1 || sequence > schedule.size()) {
            throw new IllegalArgumentException("row sequence is outside the locked schedule");
        }
        return schedule.get(sequence - 1);
    }
}
