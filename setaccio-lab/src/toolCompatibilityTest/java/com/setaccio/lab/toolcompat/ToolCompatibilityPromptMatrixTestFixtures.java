package com.setaccio.lab.toolcompat;

import java.time.Instant;
import java.util.List;

/** Provider-free canonical rows for paired prompt-matrix tests. */
final class ToolCompatibilityPromptMatrixTestFixtures {

    private static final Instant STARTED = Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-21T11:00:00Z");

    private ToolCompatibilityPromptMatrixTestFixtures() {}

    static ToolCompatibilityPromptMatrixResult result(ToolCompatibilityPromptCondition condition) {
        ToolCompatibilityPairedSchedule pairedSchedule = ToolCompatibilityPairedSchedule.locked();
        ToolCompatibilitySystemPromptIdentity prompt = condition.prompt(
                ToolCompatibilityProtocol.systemPromptCatalog());
        List<ToolCompatibilityRow> rows = ToolCompatibilityAnalysisTestFixtures.schedule().stream()
                .map(scheduled -> withPromptAndPair(
                        ToolCompatibilityAnalysisTestFixtures.successfulRow(
                                scheduled, scheduled.sequence() * 10L),
                        prompt,
                        pairedSchedule.requireEntry(condition, scheduled.sequence())))
                .toList();
        return ToolCompatibilityPromptMatrixResult.create(
                STARTED,
                FINISHED,
                ToolCompatibilityAnalysisTestFixtures.MODEL_IDENTITY,
                condition,
                pairedSchedule,
                rows);
    }

    static ToolCompatibilityRow withPromptAndPair(
            ToolCompatibilityRow source,
            ToolCompatibilitySystemPromptIdentity prompt,
            ToolCompatibilityPairedSchedule.Entry pair
    ) {
        if (source == null || prompt == null || pair == null) {
            throw new IllegalArgumentException("source row, prompt, and paired entry are required");
        }
        return new ToolCompatibilityRow(
                source.sequence(),
                source.caseId(),
                source.repetition(),
                source.seed(),
                source.provider(),
                source.requestedModel(),
                source.effectiveModel(),
                source.modelDigest(),
                prompt.id(),
                prompt.version(),
                prompt.sha256(),
                pair.globalPairSequence(),
                pair.conditionExecutionPosition(),
                source.temperature(),
                source.maxOutputTokensPerProviderTurn(),
                source.rowAttemptDeadline(),
                source.attemptCount(),
                source.providerTurns(),
                source.toolCalls(),
                source.toolResponses(),
                source.assertions(),
                source.rowAttemptCompleted(),
                source.exactCallSequenceMatched(),
                source.allExpectedArgumentsMatched(),
                source.finalResponsePresent(),
                source.caseContractPassed(),
                source.finalAssistantOutput(),
                source.thinkTagDetected(),
                source.reasoningMarkerDetected(),
                source.reasoningMarkerDetectedBeforeFirstToolCall(),
                source.reasoningMarkerDetectedAfterToolExecution(),
                source.visibleReasoningTextInFinalOutput(),
                source.anyProviderTurnReachedOutputLimit(),
                source.aggregateUsage(),
                source.rowLatency(),
                source.failureCategory(),
                source.diagnosticCategory(),
                source.safeErrorMessage());
    }
}
