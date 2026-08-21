package com.setaccio.lab.toolcompat;

import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates and summarizes one complete canonical result without provider access. */
final class ToolCompatibilityAnalyzer {

    ToolCompatibilityAnalysis analyze(ToolCompatibilityResult result) {
        requireIntegrity(result);
        return analyzeRows(result.rows());
    }

    ToolCompatibilityAnalysis analyzePromptMatrix(ToolCompatibilityPromptMatrixResult result) {
        requireIntegrity(result);
        return analyzeRows(result.rows());
    }

    private static ToolCompatibilityAnalysis analyzeRows(List<ToolCompatibilityRow> rows) {
        return new ToolCompatibilityAnalysis(
                invocation(rows),
                selection(rows),
                arguments(rows),
                execution(rows),
                completion(rows),
                reasoning(rows),
                usageAndLatency(rows),
                diagnosticCounts(rows),
                failedContractDiagnostics(rows));
    }

    private static void requireIntegrity(ToolCompatibilityResult result) {
        if (result == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility result must not be null");
        }
        try {
            ToolCompatibilityResult canonical = ToolCompatibilityResult.create(
                    result.startedAt(),
                    result.finishedAt(),
                    result.modelIdentity(),
                    result.rows());
            if (!canonical.equals(result)) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Tool compatibility result identity drifted from the locked protocol");
            }
            requireRowDiagnostics(result.rows());
        } catch (ToolCompatibilityProtocolIntegrityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility result failed deterministic integrity validation",
                    exception);
        }
    }

    private static void requireIntegrity(ToolCompatibilityPromptMatrixResult result) {
        if (result == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility prompt-matrix result must not be null");
        }
        try {
            ToolCompatibilityPromptMatrixResult canonical = ToolCompatibilityPromptMatrixResult.create(
                    result.startedAt(),
                    result.finishedAt(),
                    result.modelIdentity(),
                    result.promptCondition(),
                    result.pairedExecutionSchedule(),
                    result.rows());
            if (!canonical.equals(result)) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Tool compatibility prompt-matrix result identity drifted from the locked protocol");
            }
            requireRowDiagnostics(result.rows());
        } catch (ToolCompatibilityProtocolIntegrityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility prompt-matrix result failed deterministic integrity validation",
                    exception);
        }
    }

    private static void requireRowDiagnostics(List<ToolCompatibilityRow> rows) {
        for (ToolCompatibilityRow row : rows) {
            ToolCompatibilityRowAnalyzer.Projection projection = ToolCompatibilityRowAnalyzer.project(
                    row.caseId(),
                    row.providerTurns(),
                    row.toolCalls(),
                    row.toolResponses(),
                    row.failureCategory(),
                    row.safeErrorMessage());
            if (!Objects.equals(row.diagnosticCategory(), projection.diagnosticCategory())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Tool compatibility row diagnostic drifted at sequence " + row.sequence());
            }
            if (!row.caseContractPassed()
                    && (row.diagnosticCategory() == null
                            || ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT.equals(
                                    row.diagnosticCategory()))) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Failed contract lacks one primary diagnostic at sequence " + row.sequence());
            }
            if (row.caseContractPassed()
                    && row.diagnosticCategory() != null
                    && !ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT.equals(
                            row.diagnosticCategory())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Passing contract contains a failure diagnostic at sequence " + row.sequence());
            }
        }
    }

    private static ToolCompatibilityAnalysis.InvocationSummary invocation(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityAnalysis.ProviderTurnReference> failedTurns = new ArrayList<>();
        int observedTurns = 0;
        int successfulTurns = 0;
        int emptyTurnsWithoutCalls = 0;
        for (ToolCompatibilityRow row : rows) {
            for (ToolCompatibilityProviderTurnEvidence turn : row.providerTurns()) {
                observedTurns++;
                if (turn.invocationState() == ToolCompatibilityEvidenceState.SUCCEEDED) {
                    successfulTurns++;
                    if (turn.orderedToolCallIds().isEmpty()
                            && (turn.assistantText() == null || turn.assistantText().isBlank())) {
                        emptyTurnsWithoutCalls++;
                    }
                } else {
                    failedTurns.add(new ToolCompatibilityAnalysis.ProviderTurnReference(
                            row.sequence(), turn.sequence()));
                }
            }
        }
        return new ToolCompatibilityAnalysis.InvocationSummary(
                rows.size(),
                countRows(rows, ToolCompatibilityRow::rowAttemptCompleted),
                countRows(rows, row -> ToolCompatibilityFailure.ROW_TIMEOUT.equals(row.failureCategory())),
                observedTurns,
                successfulTurns,
                failedTurns.size(),
                failedTurns,
                // Installed-model absence is a preflight integrity failure, so a valid result records zero.
                0,
                emptyTurnsWithoutCalls);
    }

    private static ToolCompatibilityAnalysis.ToolSelectionSummary selection(
            List<ToolCompatibilityRow> rows
    ) {
        int requiredSelected = 0;
        int requiredMissing = 0;
        int forbiddenSelected = 0;
        int unnecessaryCalls = 0;
        int validAbstentions = 0;
        int missingRows = 0;
        int additionalRows = 0;
        int reorderedRows = 0;
        int duplicateRows = 0;
        for (ToolCompatibilityRow row : rows) {
            List<String> expected = expectedToolNames(row.caseId());
            List<String> observed = row.toolCalls().stream()
                    .map(ToolCompatibilityToolCallEvidence::toolName)
                    .toList();
            Map<String, Integer> expectedCounts = frequencies(expected);
            Map<String, Integer> observedCounts = frequencies(observed);
            int rowMissing = deficit(expectedCounts, observedCounts);
            int rowAdditional = deficit(observedCounts, expectedCounts);
            requiredSelected += expected.size() - rowMissing;
            requiredMissing += rowMissing;
            unnecessaryCalls += rowAdditional;
            if (rowMissing > 0) {
                missingRows++;
            }
            if (rowAdditional > 0) {
                additionalRows++;
            }
            if (expectedCounts.equals(observedCounts) && !expected.equals(observed)) {
                reorderedRows++;
            }
            if (observedCounts.entrySet().stream().anyMatch(entry ->
                    entry.getValue() > Math.max(1, expectedCounts.getOrDefault(entry.getKey(), 0)))) {
                duplicateRows++;
            }
            List<String> forbidden = canonicalCase(row.caseId())
                    .expectation()
                    .forbiddenExecutedTools();
            forbiddenSelected += (int) observed.stream().filter(forbidden::contains).count();
            if (expected.isEmpty() && observed.isEmpty() && row.caseContractPassed()) {
                validAbstentions++;
            }
        }
        return new ToolCompatibilityAnalysis.ToolSelectionSummary(
                requiredSelected,
                requiredMissing,
                forbiddenSelected,
                unnecessaryCalls,
                validAbstentions,
                countRows(rows, ToolCompatibilityRow::exactCallSequenceMatched),
                missingRows,
                additionalRows,
                reorderedRows,
                duplicateRows);
    }

    private static ToolCompatibilityAnalysis.ToolArgumentSummary arguments(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityToolCallEvidence> calls = allCalls(rows);
        int coercedSchemaMismatch = countCalls(calls, call ->
                bindingSucceeded(call)
                        && call.declaredSchemaState() == ToolCompatibilityEvidenceState.FAILED);
        int coercedSemanticMismatch = countCalls(calls, call ->
                bindingSucceeded(call)
                        && call.expectedArgumentsState() == ToolCompatibilityEvidenceState.FAILED);
        return new ToolCompatibilityAnalysis.ToolArgumentSummary(
                calls.size(),
                countCalls(calls, call ->
                        call.rawArgumentJsonState() == ToolCompatibilityEvidenceState.SUCCEEDED),
                countCalls(calls, call ->
                        call.rawArgumentJsonState() == ToolCompatibilityEvidenceState.FAILED),
                countCalls(calls, call ->
                        call.declaredSchemaState() == ToolCompatibilityEvidenceState.SUCCEEDED),
                countCalls(calls, call ->
                        call.declaredSchemaState() == ToolCompatibilityEvidenceState.FAILED),
                countCalls(calls, call ->
                        call.declaredSchemaState() == ToolCompatibilityEvidenceState.UNOBSERVABLE),
                countCalls(calls, call ->
                        call.declaredSchemaState() == ToolCompatibilityEvidenceState.NOT_REACHED),
                countCalls(calls, call ->
                        call.expectedArgumentsState() == ToolCompatibilityEvidenceState.SUCCEEDED),
                countCalls(calls, call ->
                        call.expectedArgumentsState() == ToolCompatibilityEvidenceState.FAILED),
                countCalls(calls, call ->
                        call.expectedArgumentsState() == ToolCompatibilityEvidenceState.NOT_REACHED),
                countRows(rows, ToolCompatibilityRow::allExpectedArgumentsMatched),
                countCalls(calls, call -> bindingSucceeded(call)
                        && (call.declaredSchemaState() == ToolCompatibilityEvidenceState.FAILED
                                || call.expectedArgumentsState() == ToolCompatibilityEvidenceState.FAILED)),
                coercedSchemaMismatch,
                coercedSemanticMismatch);
    }

    private static ToolCompatibilityAnalysis.ToolExecutionSummary execution(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityToolCallEvidence> calls = allCalls(rows);
        List<ToolCompatibilityToolResponseEvidence> responses = rows.stream()
                .flatMap(row -> row.toolResponses().stream())
                .toList();
        return new ToolCompatibilityAnalysis.ToolExecutionSummary(
                countCalls(calls, ToolCompatibilityAnalyzer::validToolCall),
                countCalls(calls, call ->
                        call.rawArgumentJsonState() == ToolCompatibilityEvidenceState.FAILED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackBindingState,
                        ToolCompatibilityEvidenceState.SUCCEEDED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackBindingState,
                        ToolCompatibilityEvidenceState.FAILED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackBindingState,
                        ToolCompatibilityEvidenceState.UNOBSERVABLE),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackBindingState,
                        ToolCompatibilityEvidenceState.NOT_REACHED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackExecutionState,
                        ToolCompatibilityEvidenceState.SUCCEEDED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackExecutionState,
                        ToolCompatibilityEvidenceState.FAILED),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackExecutionState,
                        ToolCompatibilityEvidenceState.UNOBSERVABLE),
                countState(calls, ToolCompatibilityToolCallEvidence::callbackExecutionState,
                        ToolCompatibilityEvidenceState.NOT_REACHED),
                (int) responses.stream().filter(response ->
                        response.callbackResultState() == ToolCompatibilityEvidenceState.SUCCEEDED).count(),
                (int) responses.stream().filter(response ->
                        response.callbackResultState() == ToolCompatibilityEvidenceState.FAILED).count(),
                countRows(rows, ToolCompatibilityAnalyzer::expectedDeterministicFailureRetained));
    }

    private static ToolCompatibilityAnalysis.CompletionSummary completion(
            List<ToolCompatibilityRow> rows
    ) {
        return new ToolCompatibilityAnalysis.CompletionSummary(
                countRows(rows, ToolCompatibilityRow::finalResponsePresent),
                countRows(rows, row -> !row.finalResponsePresent()),
                countRows(rows, ToolCompatibilityRow::caseContractPassed),
                countRows(rows, ToolCompatibilityAnalyzer::toolSucceededButFinalAnswerFailed),
                rows.stream().mapToInt(row -> (int) row.providerTurns().stream()
                        .filter(turn -> turn.outputLimitState() == ToolCompatibilityOutputLimitState.REACHED)
                        .count()).sum(),
                countRows(rows, ToolCompatibilityRow::anyProviderTurnReachedOutputLimit));
    }

    private static ToolCompatibilityAnalysis.ReasoningStyleSummary reasoning(
            List<ToolCompatibilityRow> rows
    ) {
        int thinkTags = 0;
        int otherMarkers = 0;
        int beforeFirstCall = 0;
        int afterExecution = 0;
        int inFinalResponse = 0;
        ToolCompatibilityVisibleReasoningDetector detector =
                new ToolCompatibilityVisibleReasoningDetector();
        for (ToolCompatibilityRow row : rows) {
            ToolCompatibilityVisibleReasoningEvidence evidence =
                    detector.detect(row.providerTurns(), row.toolCalls());
            thinkTags += evidence.thinkTagDetected() ? 1 : 0;
            otherMarkers += evidence.otherReasoningMarkerDetected() ? 1 : 0;
            beforeFirstCall += evidence.markerDetectedBeforeFirstToolCall() ? 1 : 0;
            afterExecution += evidence.markerDetectedAfterToolExecution() ? 1 : 0;
            inFinalResponse += evidence.visibleReasoningTextInFinalOutput() ? 1 : 0;
        }
        return new ToolCompatibilityAnalysis.ReasoningStyleSummary(
                thinkTags,
                otherMarkers,
                beforeFirstCall,
                afterExecution,
                inFinalResponse);
    }

    private static ToolCompatibilityAnalysis.UsageLatencySummary usageAndLatency(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityAnalysis.ProviderTurnUsage> turns = new ArrayList<>();
        List<ToolCompatibilityAnalysis.RowUsage> rowAggregates = new ArrayList<>();
        List<Long> successfulLatencies = new ArrayList<>();
        for (ToolCompatibilityRow row : rows) {
            for (ToolCompatibilityProviderTurnEvidence turn : row.providerTurns()) {
                turns.add(new ToolCompatibilityAnalysis.ProviderTurnUsage(
                        row.sequence(), turn.sequence(), turn.usage(), turn.latency()));
            }
            rowAggregates.add(new ToolCompatibilityAnalysis.RowUsage(
                    row.sequence(), row.aggregateUsage()));
            if (row.caseContractPassed()) {
                successfulLatencies.add(row.rowLatency().toMillis());
            }
        }
        Collections.sort(successfulLatencies);
        return new ToolCompatibilityAnalysis.UsageLatencySummary(
                countUsage(turns, ToolCompatibilityUsageAvailability.COMPLETE),
                countUsage(turns, ToolCompatibilityUsageAvailability.PARTIAL),
                countUsage(turns, ToolCompatibilityUsageAvailability.ABSENT),
                turns,
                rowAggregates,
                median(successfulLatencies),
                successfulLatencies.isEmpty() ? null : successfulLatencies.getFirst(),
                successfulLatencies.isEmpty() ? null : successfulLatencies.getLast());
    }

    private static Map<String, Integer> diagnosticCounts(List<ToolCompatibilityRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ToolCompatibilityDiagnostic.categories().forEach(category -> counts.put(category, 0));
        for (ToolCompatibilityRow row : rows) {
            if (row.diagnosticCategory() != null) {
                counts.compute(row.diagnosticCategory(), (category, count) -> count + 1);
            }
        }
        return counts;
    }

    private static List<ToolCompatibilityAnalysis.RowDiagnostic> failedContractDiagnostics(
            List<ToolCompatibilityRow> rows
    ) {
        return rows.stream()
                .filter(row -> !row.caseContractPassed())
                .map(row -> new ToolCompatibilityAnalysis.RowDiagnostic(
                        row.sequence(), row.caseId(), row.repetition(), row.diagnosticCategory()))
                .toList();
    }

    private static boolean validToolCall(ToolCompatibilityToolCallEvidence call) {
        return call.rawArgumentJsonState() == ToolCompatibilityEvidenceState.SUCCEEDED
                && call.declaredSchemaState() == ToolCompatibilityEvidenceState.SUCCEEDED
                && call.expectedCallAtSequenceState() == ToolCompatibilityEvidenceState.SUCCEEDED
                && call.expectedArgumentsState() == ToolCompatibilityEvidenceState.SUCCEEDED;
    }

    private static boolean bindingSucceeded(ToolCompatibilityToolCallEvidence call) {
        return call.callbackBindingState() == ToolCompatibilityEvidenceState.SUCCEEDED;
    }

    private static boolean expectedDeterministicFailureRetained(ToolCompatibilityRow row) {
        if (!"deterministic-tool-failure".equals(row.caseId())
                || !row.exactCallSequenceMatched()
                || !row.allExpectedArgumentsMatched()) {
            return false;
        }
        boolean retainedFailure = row.toolResponses().stream().anyMatch(response ->
                response.failure() != null
                        && ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE.equals(
                                response.failure().category())
                        && response.responseData() != null
                        && !response.responseData().isBlank());
        boolean responseContractPassed = row.assertions().stream().anyMatch(assertion ->
                "tool_response_contains".equals(assertion.check()) && assertion.passed());
        return retainedFailure && responseContractPassed;
    }

    private static boolean toolSucceededButFinalAnswerFailed(ToolCompatibilityRow row) {
        boolean toolSucceeded = row.toolResponses().stream().anyMatch(response ->
                response.callbackResultState() == ToolCompatibilityEvidenceState.SUCCEEDED);
        boolean outputContractFailed = row.assertions().stream().anyMatch(assertion ->
                "output_contains".equals(assertion.check()) && !assertion.passed());
        return toolSucceeded && (!row.finalResponsePresent() || outputContractFailed);
    }

    private static ToolBenchmarkPrompt canonicalCase(String caseId) {
        return ToolBenchmarkCases.defaults().stream()
                .filter(prompt -> prompt.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new ToolCompatibilityProtocolIntegrityException(
                        "Unknown canonical case during deterministic analysis: " + caseId));
    }

    private static List<String> expectedToolNames(String caseId) {
        return ToolCompatibilityProtocol.caseOracle()
                .requireCase(caseId)
                .calls()
                .stream()
                .map(ToolCompatibilityExpectedCall::toolName)
                .toList();
    }

    private static Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        values.forEach(value -> frequencies.merge(value, 1, Integer::sum));
        return frequencies;
    }

    private static int deficit(Map<String, Integer> required, Map<String, Integer> observed) {
        return required.entrySet().stream()
                .mapToInt(entry -> Math.max(0, entry.getValue() - observed.getOrDefault(entry.getKey(), 0)))
                .sum();
    }

    private static List<ToolCompatibilityToolCallEvidence> allCalls(List<ToolCompatibilityRow> rows) {
        return rows.stream().flatMap(row -> row.toolCalls().stream()).toList();
    }

    private static int countRows(
            List<ToolCompatibilityRow> rows,
            java.util.function.Predicate<ToolCompatibilityRow> predicate
    ) {
        return (int) rows.stream().filter(predicate).count();
    }

    private static int countCalls(
            List<ToolCompatibilityToolCallEvidence> calls,
            java.util.function.Predicate<ToolCompatibilityToolCallEvidence> predicate
    ) {
        return (int) calls.stream().filter(predicate).count();
    }

    private static int countState(
            List<ToolCompatibilityToolCallEvidence> calls,
            java.util.function.Function<ToolCompatibilityToolCallEvidence, ToolCompatibilityEvidenceState> state,
            ToolCompatibilityEvidenceState expected
    ) {
        return (int) calls.stream().filter(call -> state.apply(call) == expected).count();
    }

    private static int countUsage(
            List<ToolCompatibilityAnalysis.ProviderTurnUsage> turns,
            ToolCompatibilityUsageAvailability availability
    ) {
        return (int) turns.stream()
                .filter(turn -> turn.usage().availability() == availability)
                .count();
    }

    private static Double median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle).doubleValue();
        }
        return sorted.get(middle - 1) / 2.0 + sorted.get(middle) / 2.0;
    }
}
