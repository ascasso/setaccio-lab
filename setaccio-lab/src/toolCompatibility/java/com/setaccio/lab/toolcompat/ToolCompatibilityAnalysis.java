package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Multidimensional deterministic analysis with no aggregate score or rank. */
record ToolCompatibilityAnalysis(
        InvocationSummary invocation,
        ToolSelectionSummary toolSelection,
        ToolArgumentSummary toolArguments,
        ToolExecutionSummary toolExecution,
        CompletionSummary completion,
        ReasoningStyleSummary reasoningStyleOutput,
        UsageLatencySummary usageAndLatency,
        Map<String, Integer> diagnosticCounts,
        List<RowDiagnostic> failedContractDiagnostics
) {

    ToolCompatibilityAnalysis {
        if (invocation == null
                || toolSelection == null
                || toolArguments == null
                || toolExecution == null
                || completion == null
                || reasoningStyleOutput == null
                || usageAndLatency == null) {
            throw new IllegalArgumentException("every tool compatibility analysis dimension is required");
        }
        diagnosticCounts = orderedDiagnosticCounts(diagnosticCounts);
        failedContractDiagnostics = List.copyOf(
                failedContractDiagnostics == null ? List.of() : failedContractDiagnostics);
        requireCrossDimensionConsistency(
                invocation,
                toolSelection,
                toolArguments,
                toolExecution,
                completion,
                reasoningStyleOutput,
                usageAndLatency,
                diagnosticCounts,
                failedContractDiagnostics);
    }

    private static void requireCrossDimensionConsistency(
            InvocationSummary invocation,
            ToolSelectionSummary selection,
            ToolArgumentSummary arguments,
            ToolExecutionSummary execution,
            CompletionSummary completion,
            ReasoningStyleSummary reasoning,
            UsageLatencySummary usage,
            Map<String, Integer> diagnosticCounts,
            List<RowDiagnostic> failedDiagnostics
    ) {
        int plannedRows = invocation.plannedRows();
        int observedCalls = arguments.observedToolCalls();
        int bindingStates = execution.callbackBindingSucceeded()
                + execution.callbackBindingFailed()
                + execution.callbackBindingUnobservable()
                + execution.callbackBindingNotReached();
        int executionStates = execution.callbackExecutionSucceeded()
                + execution.callbackExecutionFailed()
                + execution.callbackExecutionUnobservable()
                + execution.callbackExecutionNotReached();
        if (completion.finalResponsesPresent() + completion.finalResponsesEmpty() != plannedRows
                || usage.rowAggregates().size() != plannedRows
                || usage.providerTurnUsage().size() != invocation.observedProviderTurns()
                || bindingStates != observedCalls
                || executionStates != observedCalls
                || execution.malformedToolCalls() != arguments.rawJsonInvalidCalls()
                || execution.validToolCalls() > observedCalls
                || execution.callbackResultsSucceeded() + execution.callbackResultsFailed() > observedCalls
                || selection.exactExpectedCallSequencesMatched() > plannedRows
                || arguments.rowsWhereAllExpectedArgumentsMatched() > plannedRows
                || completion.providerTurnsReachedOutputLimit() > invocation.observedProviderTurns()
                || completion.rowsWithAnyProviderTurnReachedOutputLimit() > plannedRows
                || failedDiagnostics.size() != plannedRows - completion.finalContractsPassed()
                || reasoning.thinkTagDetected() > plannedRows
                || reasoning.otherReasoningMarkerDetected() > plannedRows
                || reasoning.reasoningMarkerBeforeFirstToolCall() > plannedRows
                || reasoning.reasoningMarkerAfterToolExecution() > plannedRows
                || reasoning.reasoningMarkerInFinalResponse() > plannedRows) {
            throw new IllegalArgumentException("tool compatibility analysis dimensions are inconsistent");
        }
        boolean successfulLatencyPresent = usage.medianSuccessfulRowLatencyMillis() != null;
        if (successfulLatencyPresent != (completion.finalContractsPassed() > 0)) {
            throw new IllegalArgumentException("successful-row latency must track passing contracts");
        }
        java.util.Set<Integer> failedSequences = new java.util.LinkedHashSet<>();
        Map<String, Integer> failedCounts = new LinkedHashMap<>();
        failedDiagnostics.forEach(diagnostic -> {
            if (!failedSequences.add(diagnostic.rowSequence())) {
                throw new IllegalArgumentException("failed-contract diagnostics must have unique row sequences");
            }
            failedCounts.merge(diagnostic.primaryCategory(), 1, Integer::sum);
        });
        failedCounts.forEach((category, count) -> {
            if (diagnosticCounts.getOrDefault(category, 0) < count) {
                throw new IllegalArgumentException("failed-contract diagnostics exceed category counts");
            }
        });
    }

    private static Map<String, Integer> orderedDiagnosticCounts(Map<String, Integer> counts) {
        if (counts == null || !counts.keySet().equals(java.util.Set.copyOf(
                ToolCompatibilityDiagnostic.categories()))) {
            throw new IllegalArgumentException("diagnostic counts must contain every known category exactly once");
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String category : ToolCompatibilityDiagnostic.categories()) {
            Integer count = counts.get(category);
            requireNonNegative(count, category + " diagnostic count");
            ordered.put(category, count);
        }
        return Collections.unmodifiableMap(ordered);
    }

    record InvocationSummary(
            int plannedRows,
            int completedLogicalRowAttempts,
            int timedOutLogicalRowAttempts,
            int observedProviderTurns,
            int successfulProviderTurns,
            int failedProviderTurns,
            List<ProviderTurnReference> failedProviderTurnSequences,
            int unavailableModels,
            int emptyProviderTurnsWithoutToolCall
    ) {

        InvocationSummary {
            requireNonNegative(plannedRows, "planned rows");
            requireNonNegative(completedLogicalRowAttempts, "completed logical row attempts");
            requireNonNegative(timedOutLogicalRowAttempts, "timed-out logical row attempts");
            requireNonNegative(observedProviderTurns, "observed provider turns");
            requireNonNegative(successfulProviderTurns, "successful provider turns");
            requireNonNegative(failedProviderTurns, "failed provider turns");
            requireNonNegative(unavailableModels, "unavailable models");
            requireNonNegative(emptyProviderTurnsWithoutToolCall, "empty provider turns");
            failedProviderTurnSequences = List.copyOf(
                    failedProviderTurnSequences == null ? List.of() : failedProviderTurnSequences);
            if (successfulProviderTurns + failedProviderTurns != observedProviderTurns
                    || failedProviderTurns != failedProviderTurnSequences.size()) {
                throw new IllegalArgumentException("provider-turn counts contradict their ordered evidence");
            }
        }
    }

    record ToolSelectionSummary(
            int requiredToolSelections,
            int requiredToolsMissing,
            int forbiddenToolSelections,
            int unnecessaryToolCalls,
            int validAbstentions,
            int exactExpectedCallSequencesMatched,
            int rowsWithMissingCalls,
            int rowsWithAdditionalCalls,
            int rowsWithReorderedCalls,
            int rowsWithDuplicateCalls
    ) {

        ToolSelectionSummary {
            requireNonNegative(requiredToolSelections, "required tool selections");
            requireNonNegative(requiredToolsMissing, "required tools missing");
            requireNonNegative(forbiddenToolSelections, "forbidden tool selections");
            requireNonNegative(unnecessaryToolCalls, "unnecessary tool calls");
            requireNonNegative(validAbstentions, "valid abstentions");
            requireNonNegative(exactExpectedCallSequencesMatched, "exact call sequences");
            requireNonNegative(rowsWithMissingCalls, "rows with missing calls");
            requireNonNegative(rowsWithAdditionalCalls, "rows with additional calls");
            requireNonNegative(rowsWithReorderedCalls, "rows with reordered calls");
            requireNonNegative(rowsWithDuplicateCalls, "rows with duplicate calls");
        }
    }

    record ToolArgumentSummary(
            int observedToolCalls,
            int rawJsonValidCalls,
            int rawJsonInvalidCalls,
            int declaredSchemaValidCalls,
            int declaredSchemaInvalidCalls,
            int declaredSchemaUnobservableCalls,
            int declaredSchemaNotReachedCalls,
            int expectedArgumentsMatchedCalls,
            int expectedArgumentsMismatchedCalls,
            int expectedArgumentsNotReachedCalls,
            int rowsWhereAllExpectedArgumentsMatched,
            int callbackCoercedMismatchCalls,
            int callbackCoercedSchemaMismatchCalls,
            int callbackCoercedSemanticMismatchCalls
    ) {

        ToolArgumentSummary {
            requireNonNegative(observedToolCalls, "observed tool calls");
            requireNonNegative(rawJsonValidCalls, "raw JSON valid calls");
            requireNonNegative(rawJsonInvalidCalls, "raw JSON invalid calls");
            requireNonNegative(declaredSchemaValidCalls, "declared-schema valid calls");
            requireNonNegative(declaredSchemaInvalidCalls, "declared-schema invalid calls");
            requireNonNegative(declaredSchemaUnobservableCalls, "declared-schema unobservable calls");
            requireNonNegative(declaredSchemaNotReachedCalls, "declared-schema not-reached calls");
            requireNonNegative(expectedArgumentsMatchedCalls, "expected-argument matches");
            requireNonNegative(expectedArgumentsMismatchedCalls, "expected-argument mismatches");
            requireNonNegative(expectedArgumentsNotReachedCalls, "expected arguments not reached");
            requireNonNegative(rowsWhereAllExpectedArgumentsMatched, "rows with all arguments matched");
            requireNonNegative(callbackCoercedMismatchCalls, "callback-coerced mismatches");
            requireNonNegative(callbackCoercedSchemaMismatchCalls, "callback-coerced schema mismatches");
            requireNonNegative(callbackCoercedSemanticMismatchCalls, "callback-coerced semantic mismatches");
            if (rawJsonValidCalls + rawJsonInvalidCalls != observedToolCalls
                    || declaredSchemaValidCalls
                                    + declaredSchemaInvalidCalls
                                    + declaredSchemaUnobservableCalls
                                    + declaredSchemaNotReachedCalls
                            != observedToolCalls
                    || expectedArgumentsMatchedCalls
                                    + expectedArgumentsMismatchedCalls
                                    + expectedArgumentsNotReachedCalls
                            != observedToolCalls) {
                throw new IllegalArgumentException("tool-argument state counts must cover every observed call");
            }
        }
    }

    record ToolExecutionSummary(
            int validToolCalls,
            int malformedToolCalls,
            int callbackBindingSucceeded,
            int callbackBindingFailed,
            int callbackBindingUnobservable,
            int callbackBindingNotReached,
            int callbackExecutionSucceeded,
            int callbackExecutionFailed,
            int callbackExecutionUnobservable,
            int callbackExecutionNotReached,
            int callbackResultsSucceeded,
            int callbackResultsFailed,
            int expectedDeterministicCallbackFailuresRetained
    ) {

        ToolExecutionSummary {
            requireNonNegative(validToolCalls, "valid tool calls");
            requireNonNegative(malformedToolCalls, "malformed tool calls");
            requireNonNegative(callbackBindingSucceeded, "callback bindings succeeded");
            requireNonNegative(callbackBindingFailed, "callback bindings failed");
            requireNonNegative(callbackBindingUnobservable, "callback bindings unobservable");
            requireNonNegative(callbackBindingNotReached, "callback bindings not reached");
            requireNonNegative(callbackExecutionSucceeded, "callback executions succeeded");
            requireNonNegative(callbackExecutionFailed, "callback executions failed");
            requireNonNegative(callbackExecutionUnobservable, "callback executions unobservable");
            requireNonNegative(callbackExecutionNotReached, "callback executions not reached");
            requireNonNegative(callbackResultsSucceeded, "callback results succeeded");
            requireNonNegative(callbackResultsFailed, "callback results failed");
            requireNonNegative(
                    expectedDeterministicCallbackFailuresRetained,
                    "expected deterministic callback failures retained");
        }
    }

    record CompletionSummary(
            int finalResponsesPresent,
            int finalResponsesEmpty,
            int finalContractsPassed,
            int toolSucceededButFinalAnswerFailed,
            int providerTurnsReachedOutputLimit,
            int rowsWithAnyProviderTurnReachedOutputLimit
    ) {

        CompletionSummary {
            requireNonNegative(finalResponsesPresent, "final responses present");
            requireNonNegative(finalResponsesEmpty, "final responses empty");
            requireNonNegative(finalContractsPassed, "final contracts passed");
            requireNonNegative(toolSucceededButFinalAnswerFailed, "tool succeeded but final answer failed");
            requireNonNegative(providerTurnsReachedOutputLimit, "provider turns reaching output limit");
            requireNonNegative(rowsWithAnyProviderTurnReachedOutputLimit, "rows reaching output limit");
        }
    }

    record ReasoningStyleSummary(
            int thinkTagDetected,
            int otherReasoningMarkerDetected,
            int reasoningMarkerBeforeFirstToolCall,
            int reasoningMarkerAfterToolExecution,
            int reasoningMarkerInFinalResponse
    ) {

        ReasoningStyleSummary {
            requireNonNegative(thinkTagDetected, "think-tag rows");
            requireNonNegative(otherReasoningMarkerDetected, "other-reasoning-marker rows");
            requireNonNegative(reasoningMarkerBeforeFirstToolCall, "pre-tool reasoning-marker rows");
            requireNonNegative(reasoningMarkerAfterToolExecution, "post-tool reasoning-marker rows");
            requireNonNegative(reasoningMarkerInFinalResponse, "final-response reasoning-marker rows");
        }
    }

    record UsageLatencySummary(
            int providerTurnsWithCompleteUsage,
            int providerTurnsWithPartialUsage,
            int providerTurnsWithAbsentUsage,
            List<ProviderTurnUsage> providerTurnUsage,
            List<RowUsage> rowAggregates,
            Double medianSuccessfulRowLatencyMillis,
            Long minimumSuccessfulRowLatencyMillis,
            Long maximumSuccessfulRowLatencyMillis
    ) {

        UsageLatencySummary {
            requireNonNegative(providerTurnsWithCompleteUsage, "provider turns with complete usage");
            requireNonNegative(providerTurnsWithPartialUsage, "provider turns with partial usage");
            requireNonNegative(providerTurnsWithAbsentUsage, "provider turns with absent usage");
            providerTurnUsage = List.copyOf(providerTurnUsage == null ? List.of() : providerTurnUsage);
            rowAggregates = List.copyOf(rowAggregates == null ? List.of() : rowAggregates);
            if (providerTurnsWithCompleteUsage
                                    + providerTurnsWithPartialUsage
                                    + providerTurnsWithAbsentUsage
                            != providerTurnUsage.size()) {
                throw new IllegalArgumentException("usage-availability counts must cover every provider turn");
            }
            boolean noLatency = medianSuccessfulRowLatencyMillis == null
                    && minimumSuccessfulRowLatencyMillis == null
                    && maximumSuccessfulRowLatencyMillis == null;
            boolean completeLatency = medianSuccessfulRowLatencyMillis != null
                    && minimumSuccessfulRowLatencyMillis != null
                    && maximumSuccessfulRowLatencyMillis != null;
            if ((!noLatency && !completeLatency)
                    || (completeLatency
                            && (!Double.isFinite(medianSuccessfulRowLatencyMillis)
                                    || medianSuccessfulRowLatencyMillis < 0
                                    || minimumSuccessfulRowLatencyMillis < 0
                                    || maximumSuccessfulRowLatencyMillis < minimumSuccessfulRowLatencyMillis
                                    || medianSuccessfulRowLatencyMillis < minimumSuccessfulRowLatencyMillis
                                    || medianSuccessfulRowLatencyMillis > maximumSuccessfulRowLatencyMillis))) {
                throw new IllegalArgumentException("successful-row latency statistics are inconsistent");
            }
        }
    }

    record ProviderTurnReference(int rowSequence, int providerTurnSequence) {

        ProviderTurnReference {
            requirePositive(rowSequence, "row sequence");
            requirePositive(providerTurnSequence, "provider-turn sequence");
        }
    }

    record ProviderTurnUsage(
            int rowSequence,
            int providerTurnSequence,
            ToolCompatibilityTokenUsageEvidence usage,
            Duration latency
    ) {

        ProviderTurnUsage {
            requirePositive(rowSequence, "row sequence");
            requirePositive(providerTurnSequence, "provider-turn sequence");
            if (usage == null || latency == null || latency.isNegative()) {
                throw new IllegalArgumentException("provider-turn usage and latency are required");
            }
        }
    }

    record RowUsage(int rowSequence, ToolCompatibilityTokenUsageEvidence aggregateUsage) {

        RowUsage {
            requirePositive(rowSequence, "row sequence");
            if (aggregateUsage == null) {
                throw new IllegalArgumentException("row aggregate usage is required");
            }
        }
    }

    record RowDiagnostic(
            int rowSequence,
            String caseId,
            int repetition,
            String primaryCategory
    ) {

        RowDiagnostic {
            requirePositive(rowSequence, "row sequence");
            requirePositive(repetition, "repetition");
            if (caseId == null || caseId.isBlank() || !caseId.equals(caseId.strip())) {
                throw new IllegalArgumentException("diagnostic case ID must be nonblank and trimmed");
            }
            new ToolCompatibilityDiagnostic(primaryCategory);
            if (ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT.equals(primaryCategory)) {
                throw new IllegalArgumentException("visible reasoning alone is not a failed-contract category");
            }
        }
    }

    private static void requirePositive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
