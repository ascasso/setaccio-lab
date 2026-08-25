package com.setaccio.lab.toolcompat;

import com.setaccio.lab.model.ToolBenchmarkAssertion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Produces the deterministic, deployment-specific T3.4 cohort projections. */
final class ToolCompatibilityCohortAnalyzer {

    private static final String NO_MATCH_CASE = "catalog-no-match";
    private static final String MULTI_STEP_CASE = "catalog-multi-step";
    private static final String FAILURE_CASE = "deterministic-tool-failure";
    private static final Pattern ERROR_MARKERS = Pattern.compile(
            "\\b(?:error|fail|failed|failure|unable)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SUCCESS_CLAIM_MARKERS = Pattern.compile(
            "\\b(?:success|succeeded|completed successfully)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final List<String> FORMAT_POLLUTION_MARKERS =
            List.of("```", "<tool_call", "</tool_call", "\"tool_calls\"", "\"arguments\":");

    ToolCompatibilityCohortAnalysis analyze(ToolCompatibilityCohortResult result) {
        if (result == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility cohort result must not be null");
        }
        ToolCompatibilityAnalyzer analyzer = new ToolCompatibilityAnalyzer();
        List<ToolCompatibilityCohortAnalysis.ModelAnalysis> models = new ArrayList<>();
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        boolean completeFormatCoverage = true;
        for (ToolCompatibilityCohortModelRun run : result.modelRuns()) {
            ToolCompatibilityAnalysis compatibility = analyzer.analyzeCohortModelRun(run);
            models.add(analyzeModel(run, compatibility));
            ToolCompatibilityMetadataField format =
                    run.modelIdentity().metadata().artifactRuntimeFormat();
            if (format.availability() == ToolCompatibilityMetadataField.Availability.AVAILABLE) {
                formats.add(format.value());
            } else {
                completeFormatCoverage = false;
            }
        }
        if (models.size() != result.orderedModels().size()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort analysis model count drifted from the result");
        }
        for (int index = 0; index < models.size(); index++) {
            if (!models.get(index).modelIdentity().equals(result.orderedModels().get(index))) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Cohort analysis model order drifted from the result");
            }
        }
        return new ToolCompatibilityCohortAnalysis(
                models,
                List.copyOf(formats),
                completeFormatCoverage,
                formats.size() > 1);
    }

    private static ToolCompatibilityCohortAnalysis.ModelAnalysis analyzeModel(
            ToolCompatibilityCohortModelRun run,
            ToolCompatibilityAnalysis compatibility
    ) {
        List<ToolCompatibilityRow> rows = run.rows();
        return new ToolCompatibilityCohortAnalysis.ModelAnalysis(
                run.modelIdentity(),
                compatibility,
                discipline(rows),
                arguments(rows),
                multiStep(rows),
                failureRecovery(rows),
                outputBehavior(rows),
                efficiency(rows, compatibility));
    }

    private static ToolCompatibilityCohortAnalysis.Discipline discipline(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityRow> noMatchRows = caseRows(rows, NO_MATCH_CASE);
        return new ToolCompatibilityCohortAnalysis.Discipline(
                noMatchRows.size(),
                count(noMatchRows, row ->
                        row.exactCallSequenceMatched() && row.allExpectedArgumentsMatched()),
                count(noMatchRows, row -> assertionPassed(row, "tool_response_contains")),
                count(noMatchRows, ToolCompatibilityRow::caseContractPassed));
    }

    private static ToolCompatibilityCohortAnalysis.ArgumentDetails arguments(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityToolCallEvidence> calls = rows.stream()
                .flatMap(row -> row.toolCalls().stream())
                .toList();
        return new ToolCompatibilityCohortAnalysis.ArgumentDetails(
                countCalls(calls, call -> call.rawArgumentIssue()
                        == ToolCompatibilitySchemaIssue.MISSING_REQUIRED_ARGUMENT),
                countCalls(calls, call -> call.rawArgumentIssue()
                        == ToolCompatibilitySchemaIssue.UNKNOWN_ARGUMENT),
                countCalls(calls, call -> call.expectedArgumentsState()
                        == ToolCompatibilityEvidenceState.FAILED));
    }

    private static ToolCompatibilityCohortAnalysis.MultiStepBehavior multiStep(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityRow> multiRows = caseRows(rows, MULTI_STEP_CASE);
        return new ToolCompatibilityCohortAnalysis.MultiStepBehavior(
                multiRows.size(),
                count(multiRows, row -> expectedCallCorrect(row, 1)),
                count(multiRows, row -> expectedCallCorrect(row, 2)),
                count(multiRows, ToolCompatibilityRow::exactCallSequenceMatched),
                count(multiRows, ToolCompatibilityCohortAnalyzer::continuedAfterFirstCallback),
                count(multiRows, row -> row.finalResponsePresent()
                        && observedExpectedCalls(row) < 2),
                count(multiRows, ToolCompatibilityCohortAnalyzer::hasDuplicateCalls));
    }

    private static ToolCompatibilityCohortAnalysis.FailureRecovery failureRecovery(
            List<ToolCompatibilityRow> rows
    ) {
        List<ToolCompatibilityRow> failureRows = caseRows(rows, FAILURE_CASE);
        return new ToolCompatibilityCohortAnalysis.FailureRecovery(
                failureRows.size(),
                count(failureRows, ToolCompatibilityCohortAnalyzer::retainedDeterministicFailure),
                count(failureRows, row -> retainedDeterministicFailure(row)
                        && contains(row.finalAssistantOutput(), ERROR_MARKERS)),
                count(failureRows, row -> retainedDeterministicFailure(row)
                        && contains(row.finalAssistantOutput(), SUCCESS_CLAIM_MARKERS)
                        && !contains(row.finalAssistantOutput(), ERROR_MARKERS)),
                count(failureRows, row -> !row.finalResponsePresent()));
    }

    private static ToolCompatibilityCohortAnalysis.OutputBehavior outputBehavior(
            List<ToolCompatibilityRow> rows
    ) {
        List<Integer> lengths = rows.stream()
                .filter(ToolCompatibilityRow::finalResponsePresent)
                .map(row -> row.finalAssistantOutput().codePointCount(
                        0, row.finalAssistantOutput().length()))
                .sorted()
                .toList();
        return new ToolCompatibilityCohortAnalysis.OutputBehavior(
                count(rows, ToolCompatibilityRow::finalResponsePresent),
                count(rows, row -> !row.finalResponsePresent()),
                count(rows, ToolCompatibilityRow::reasoningMarkerDetected),
                count(rows, ToolCompatibilityRow::anyProviderTurnReachedOutputLimit),
                count(rows, row -> row.finalResponsePresent()
                        && containsAny(row.finalAssistantOutput(), FORMAT_POLLUTION_MARKERS)),
                medianIntegers(lengths),
                lengths.isEmpty() ? null : lengths.getFirst(),
                lengths.isEmpty() ? null : lengths.getLast());
    }

    private static ToolCompatibilityCohortAnalysis.Efficiency efficiency(
            List<ToolCompatibilityRow> rows,
            ToolCompatibilityAnalysis compatibility
    ) {
        List<ToolCompatibilityProviderTurnEvidence> turns = rows.stream()
                .flatMap(row -> row.providerTurns().stream())
                .toList();
        int passingRows = count(rows, ToolCompatibilityRow::caseContractPassed);
        Double tokensPerPassingRow = null;
        if (passingRows > 0) {
            List<ToolCompatibilityRow> passing = rows.stream()
                    .filter(ToolCompatibilityRow::caseContractPassed)
                    .toList();
            boolean complete = passing.stream().allMatch(row ->
                    row.aggregateUsage().availability() == ToolCompatibilityUsageAvailability.COMPLETE
                            && row.aggregateUsage().totalTokens() != null);
            if (complete) {
                long total = passing.stream()
                        .map(ToolCompatibilityRow::aggregateUsage)
                        .map(ToolCompatibilityTokenUsageEvidence::totalTokens)
                        .mapToLong(Integer::longValue)
                        .sum();
                tokensPerPassingRow = total / (double) passingRows;
            }
        }
        ToolCompatibilityAnalysis.UsageLatencySummary usage = compatibility.usageAndLatency();
        return new ToolCompatibilityCohortAnalysis.Efficiency(
                passingRows,
                usage.medianSuccessfulRowLatencyMillis(),
                usage.minimumSuccessfulRowLatencyMillis(),
                usage.maximumSuccessfulRowLatencyMillis(),
                tokenObservation(turns, UsageField.PROMPT),
                tokenObservation(turns, UsageField.COMPLETION),
                tokenObservation(turns, UsageField.TOTAL),
                tokensPerPassingRow);
    }

    private static ToolCompatibilityCohortAnalysis.TokenObservation tokenObservation(
            List<ToolCompatibilityProviderTurnEvidence> turns,
            UsageField field
    ) {
        long total = 0;
        int observed = 0;
        for (ToolCompatibilityProviderTurnEvidence turn : turns) {
            Integer value = switch (field) {
                case PROMPT -> turn.usage().promptTokens();
                case COMPLETION -> turn.usage().completionTokens();
                case TOTAL -> turn.usage().totalTokens();
            };
            if (value != null) {
                total = Math.addExact(total, value.longValue());
                observed++;
            }
        }
        return new ToolCompatibilityCohortAnalysis.TokenObservation(
                observed == 0 ? null : total,
                observed,
                turns.size());
    }

    private static boolean expectedCallCorrect(ToolCompatibilityRow row, int expectedSequence) {
        return row.toolCalls().stream().anyMatch(call ->
                Integer.valueOf(expectedSequence).equals(call.expectedCallSequence())
                        && call.expectedCallAtSequenceState()
                                == ToolCompatibilityEvidenceState.SUCCEEDED
                        && call.expectedArgumentsState()
                                == ToolCompatibilityEvidenceState.SUCCEEDED);
    }

    private static boolean continuedAfterFirstCallback(ToolCompatibilityRow row) {
        ToolCompatibilityToolCallEvidence first = expectedCall(row, 1);
        ToolCompatibilityToolCallEvidence second = expectedCall(row, 2);
        if (first == null || second == null || first.toolResponseSequence() == null) {
            return false;
        }
        boolean firstResponseRetained = row.toolResponses().stream().anyMatch(response ->
                response.sequence() == first.toolResponseSequence()
                        && response.toolCallSequence() == first.sequence());
        return firstResponseRetained
                && second.providerTurnSequence() > first.providerTurnSequence();
    }

    private static ToolCompatibilityToolCallEvidence expectedCall(
            ToolCompatibilityRow row,
            int expectedSequence
    ) {
        return row.toolCalls().stream()
                .filter(call -> Integer.valueOf(expectedSequence).equals(call.expectedCallSequence()))
                .findFirst()
                .orElse(null);
    }

    private static int observedExpectedCalls(ToolCompatibilityRow row) {
        return (int) row.toolCalls().stream()
                .map(ToolCompatibilityToolCallEvidence::expectedCallSequence)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    }

    private static boolean hasDuplicateCalls(ToolCompatibilityRow row) {
        Set<String> names = new LinkedHashSet<>();
        return row.toolCalls().stream()
                .map(ToolCompatibilityToolCallEvidence::toolName)
                .anyMatch(name -> !names.add(name));
    }

    private static boolean retainedDeterministicFailure(ToolCompatibilityRow row) {
        return row.exactCallSequenceMatched()
                && row.allExpectedArgumentsMatched()
                && row.toolResponses().stream().anyMatch(response ->
                response.callbackResultState() == ToolCompatibilityEvidenceState.FAILED
                        && response.failure() != null
                        && ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE.equals(
                                response.failure().category())
                        && response.responseData() != null
                        && !response.responseData().isBlank());
    }

    private static boolean assertionPassed(
            ToolCompatibilityRow row,
            String check
    ) {
        return row.assertions().stream()
                .filter(assertion -> check.equals(assertion.check()))
                .anyMatch(ToolBenchmarkAssertion::passed);
    }

    private static List<ToolCompatibilityRow> caseRows(
            List<ToolCompatibilityRow> rows,
            String caseId
    ) {
        return rows.stream().filter(row -> caseId.equals(row.caseId())).toList();
    }

    private static boolean containsAny(String value, List<String> markers) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return markers.stream().anyMatch(normalized::contains);
    }

    private static boolean contains(String value, Pattern markers) {
        return value != null && markers.matcher(value).find();
    }

    private static int count(
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

    private static Double medianIntegers(List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle).doubleValue();
        }
        return sorted.get(middle - 1) / 2.0 + sorted.get(middle) / 2.0;
    }

    private enum UsageField {
        PROMPT,
        COMPLETION,
        TOTAL
    }
}
