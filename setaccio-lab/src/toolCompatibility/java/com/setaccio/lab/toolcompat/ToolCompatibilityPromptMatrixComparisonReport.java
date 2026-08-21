package com.setaccio.lab.toolcompat;

import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the deterministic, paired Phase 2 observation report after strict comparison parity
 * has already been established.
 *
 * <p>The report deliberately renders transitions and deltas without aggregating them into a
 * score, winner, or prompt-adoption recommendation.</p>
 */
final class ToolCompatibilityPromptMatrixComparisonReport {

    String render(
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline,
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate
    ) {
        if (baseline == null || candidate == null) {
            throw new IllegalArgumentException("verified baseline and candidate conditions are required");
        }
        ToolCompatibilityPromptMatrixResult baselineResult = baseline.result();
        ToolCompatibilityPromptMatrixResult candidateResult = candidate.result();
        Map<RowKey, ToolCompatibilityRow> baselineRows = rowsByCaseAndRepetition(baselineResult.rows());
        Map<RowKey, ToolCompatibilityRow> candidateRows = rowsByCaseAndRepetition(candidateResult.rows());

        StringBuilder out = new StringBuilder("# Offline Tool Compatibility Prompt Comparison\n\n");
        out.append("- Baseline run: `").append(baseline.manifest().runId()).append("`\n");
        out.append("- Candidate run: `").append(candidate.manifest().runId()).append("`\n");
        out.append("- Prompt conditions: `")
                .append(baselineResult.promptCondition().wireValue())
                .append("` → `")
                .append(candidateResult.promptCondition().wireValue())
                .append("`\n");
        out.append("- Shared Git commit: `")
                .append(baseline.manifest().codeBaseline().gitCommit())
                .append("`\n");
        out.append("- Paired schedule: `")
                .append(baselineResult.pairedExecutionSchedule().id())
                .append("` version `")
                .append(baselineResult.pairedExecutionSchedule().version())
                .append("` (`")
                .append(baselineResult.pairedExecutionSchedule().sha256())
                .append("`)\n");
        out.append("- Protocol: ")
                .append(baselineResult.orderedCaseIds().size())
                .append(" case(s) × ")
                .append(baselineResult.runSettings().repetitions())
                .append(" repetition(s) = ")
                .append(baselineResult.rows().size())
                .append(" paired row(s).\n\n");
        out.append("This report contains deterministic paired evidence only. It does not declare an "
                + "overall winner, aggregate score, prompt-adoption decision, or human interpretation.\n\n");

        out.append("## Contract and behavioral transitions\n\n");
        out.append("| Case | Repetition | Contract | Required tools | Forbidden tools | Exact calls | "
                + "Semantic arguments | Final response | Visible reasoning |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        forEachPair(baselineResult, baselineRows, candidateRows, (caseId, repetition, baselineRow, candidateRow) ->
                out.append("| `").append(caseId).append("` | ")
                        .append(repetition)
                        .append(" | ").append(contractTransition(baselineRow, candidateRow))
                        .append(" | ").append(requiredToolTransitions(caseId, baselineRow, candidateRow))
                        .append(" | ").append(forbiddenToolTransitions(caseId, baselineRow, candidateRow))
                        .append(" | ").append(exactCallTransition(baselineRow, candidateRow))
                        .append(" | ").append(semanticArgumentTransitions(caseId, baselineRow, candidateRow))
                        .append(" | ").append(finalResponseTransition(baselineRow, candidateRow))
                        .append(" | ").append(reasoningMarkerTransition(baselineRow, candidateRow))
                        .append(" |\n"));

        out.append("\n## Provider and resource deltas\n\n");
        out.append("| Case | Repetition | Provider turns and later failures | Output-limit states | "
                + "Aggregate completion tokens | Row latency |\n");
        out.append("| --- | --- | --- | --- | --- | --- |\n");
        forEachPair(baselineResult, baselineRows, candidateRows, (caseId, repetition, baselineRow, candidateRow) ->
                out.append("| `").append(caseId).append("` | ")
                        .append(repetition)
                        .append(" | ").append(providerTurnTransition(baselineRow, candidateRow))
                        .append(" | ").append(outputLimitTransition(baselineRow, candidateRow))
                        .append(" | ").append(completionTokenDelta(baselineRow, candidateRow))
                        .append(" | ").append(latencyDelta(baselineRow, candidateRow))
                        .append(" |\n"));

        out.append("\nProvider-turn and output-limit fields preserve observed evidence states; `n/a` means "
                + "the corresponding completion-token value was unavailable.\n");
        return out.toString();
    }

    private static void forEachPair(
            ToolCompatibilityPromptMatrixResult result,
            Map<RowKey, ToolCompatibilityRow> baselineRows,
            Map<RowKey, ToolCompatibilityRow> candidateRows,
            PairConsumer consumer
    ) {
        for (String caseId : result.orderedCaseIds()) {
            for (int repetition = 1; repetition <= result.runSettings().repetitions(); repetition++) {
                RowKey key = new RowKey(caseId, repetition);
                ToolCompatibilityRow baseline = baselineRows.get(key);
                ToolCompatibilityRow candidate = candidateRows.get(key);
                if (baseline == null || candidate == null) {
                    throw new IllegalArgumentException(
                            "complete verified case/repetition pairs are required for comparison reporting");
                }
                consumer.accept(caseId, repetition, baseline, candidate);
            }
        }
    }

    private static Map<RowKey, ToolCompatibilityRow> rowsByCaseAndRepetition(
            List<ToolCompatibilityRow> rows
    ) {
        Map<RowKey, ToolCompatibilityRow> byKey = new LinkedHashMap<>();
        for (ToolCompatibilityRow row : rows) {
            RowKey key = new RowKey(row.caseId(), row.repetition());
            if (byKey.putIfAbsent(key, row) != null) {
                throw new IllegalArgumentException(
                        "complete verified case/repetition pairs must not be duplicated for reporting");
            }
        }
        return Map.copyOf(byKey);
    }

    private static String contractTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        if (!baseline.caseContractPassed() && candidate.caseContractPassed()) {
            return "fail → pass";
        }
        if (baseline.caseContractPassed() && !candidate.caseContractPassed()) {
            return "pass → fail";
        }
        return baseline.caseContractPassed() ? "unchanged pass" : "unchanged fail";
    }

    private static String requiredToolTransitions(
            String caseId,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate
    ) {
        List<String> required = ToolCompatibilityProtocol.caseOracle().requireCase(caseId).calls().stream()
                .map(ToolCompatibilityExpectedCall::toolName)
                .distinct()
                .toList();
        if (required.isEmpty()) {
            return "n/a (no required tools)";
        }
        List<String> transitions = new ArrayList<>();
        for (String toolName : required) {
            boolean baselineSelected = selected(baseline, toolName);
            boolean candidateSelected = selected(candidate, toolName);
            if (!baselineSelected && candidateSelected) {
                transitions.add("newly selected `" + toolName + "`");
            } else if (baselineSelected && !candidateSelected) {
                transitions.add("newly missed `" + toolName + "`");
            }
        }
        return transitions.isEmpty() ? "none" : String.join("<br>", transitions);
    }

    private static String forbiddenToolTransitions(
            String caseId,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate
    ) {
        List<String> forbidden = canonicalCase(caseId).expectation().forbiddenExecutedTools();
        if (forbidden.isEmpty()) {
            return "n/a (no forbidden tools)";
        }
        List<String> transitions = new ArrayList<>();
        for (String toolName : forbidden) {
            boolean baselineSelected = selected(baseline, toolName);
            boolean candidateSelected = selected(candidate, toolName);
            if (!baselineSelected && candidateSelected) {
                transitions.add("newly selected `" + toolName + "`");
            } else if (baselineSelected && !candidateSelected) {
                transitions.add("no longer selected `" + toolName + "`");
            }
        }
        return transitions.isEmpty() ? "none" : String.join("<br>", transitions);
    }

    private static ToolBenchmarkPrompt canonicalCase(String caseId) {
        return ToolCompatibilityProtocol.caseSelection().cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown canonical tool compatibility case: " + caseId));
    }

    private static boolean selected(ToolCompatibilityRow row, String toolName) {
        // This is provider tool selection evidence, intentionally distinct from callback completion.
        return row.toolCalls().stream()
                .map(ToolCompatibilityToolCallEvidence::toolName)
                .anyMatch(toolName::equals);
    }

    private static String exactCallTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        if (!baseline.exactCallSequenceMatched() && candidate.exactCallSequenceMatched()) {
            return "newly matched";
        }
        if (baseline.exactCallSequenceMatched() && !candidate.exactCallSequenceMatched()) {
            return "newly mismatched";
        }
        return baseline.exactCallSequenceMatched() ? "unchanged matched" : "unchanged mismatched";
    }

    private static String semanticArgumentTransitions(
            String caseId,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate
    ) {
        List<ToolCompatibilityExpectedCall> expected = ToolCompatibilityProtocol.caseOracle()
                .requireCase(caseId)
                .calls();
        if (expected.isEmpty()) {
            return "n/a (no expected calls)";
        }
        List<String> transitions = new ArrayList<>();
        for (int index = 0; index < expected.size(); index++) {
            int callSequence = index + 1;
            transitions.add("#" + callSequence + " `" + expected.get(index).toolName() + "`: "
                    + semanticTransition(
                            semanticState(baseline, callSequence),
                            semanticState(candidate, callSequence)));
        }
        return String.join("<br>", transitions);
    }

    private static ToolCompatibilityEvidenceState semanticState(
            ToolCompatibilityRow row,
            int expectedCallSequence
    ) {
        return row.toolCalls().stream()
                .filter(call -> Integer.valueOf(expectedCallSequence).equals(call.expectedCallSequence()))
                .findFirst()
                .map(ToolCompatibilityToolCallEvidence::expectedArgumentsState)
                .orElse(ToolCompatibilityEvidenceState.NOT_REACHED);
    }

    private static String semanticTransition(
            ToolCompatibilityEvidenceState baseline,
            ToolCompatibilityEvidenceState candidate
    ) {
        if (baseline != ToolCompatibilityEvidenceState.SUCCEEDED
                && candidate == ToolCompatibilityEvidenceState.SUCCEEDED) {
            return "newly matched";
        }
        if (baseline == ToolCompatibilityEvidenceState.SUCCEEDED
                && candidate != ToolCompatibilityEvidenceState.SUCCEEDED) {
            return "newly mismatched";
        }
        if (baseline == ToolCompatibilityEvidenceState.SUCCEEDED) {
            return "unchanged matched";
        }
        if (baseline == ToolCompatibilityEvidenceState.FAILED
                && candidate == ToolCompatibilityEvidenceState.FAILED) {
            return "unchanged mismatched";
        }
        return semanticStateLabel(baseline) + " → " + semanticStateLabel(candidate);
    }

    private static String semanticStateLabel(ToolCompatibilityEvidenceState state) {
        return switch (state) {
            case SUCCEEDED -> "matched";
            case FAILED -> "mismatched";
            case NOT_REACHED -> "not reached";
            case UNOBSERVABLE -> "unobservable";
        };
    }

    private static String finalResponseTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        if (!baseline.finalResponsePresent() && candidate.finalResponsePresent()) {
            return "newly present";
        }
        if (baseline.finalResponsePresent() && !candidate.finalResponsePresent()) {
            return "newly empty";
        }
        return baseline.finalResponsePresent() ? "unchanged present" : "unchanged empty";
    }

    private static String reasoningMarkerTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        if (!baseline.reasoningMarkerDetected() && candidate.reasoningMarkerDetected()) {
            return "introduced";
        }
        if (baseline.reasoningMarkerDetected() && !candidate.reasoningMarkerDetected()) {
            return "removed";
        }
        return baseline.reasoningMarkerDetected() ? "unchanged present" : "unchanged absent";
    }

    private static String providerTurnTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        List<Integer> baselineLaterFailures = laterTurnFailures(baseline);
        List<Integer> candidateLaterFailures = laterTurnFailures(candidate);
        if (baseline.providerTurns().size() == candidate.providerTurns().size()
                && baselineLaterFailures.equals(candidateLaterFailures)) {
            return "unchanged: " + baseline.providerTurns().size() + " turn(s); later failures "
                    + laterFailureLabel(baselineLaterFailures);
        }
        return baseline.providerTurns().size() + " → " + candidate.providerTurns().size()
                + " turn(s); later failures " + laterFailureLabel(baselineLaterFailures)
                + " → " + laterFailureLabel(candidateLaterFailures);
    }

    private static List<Integer> laterTurnFailures(ToolCompatibilityRow row) {
        return row.providerTurns().stream()
                .filter(turn -> turn.sequence() > 1
                        && turn.invocationState() == ToolCompatibilityEvidenceState.FAILED)
                .map(ToolCompatibilityProviderTurnEvidence::sequence)
                .toList();
    }

    private static String laterFailureLabel(List<Integer> failures) {
        if (failures.isEmpty()) {
            return "none";
        }
        return failures.stream()
                .map(sequence -> "#" + sequence)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String outputLimitTransition(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        int observedTurns = Math.max(baseline.providerTurns().size(), candidate.providerTurns().size());
        List<String> baselineStates = new ArrayList<>();
        List<String> candidateStates = new ArrayList<>();
        for (int sequence = 1; sequence <= observedTurns; sequence++) {
            baselineStates.add(outputLimitStateAt(baseline, sequence));
            candidateStates.add(outputLimitStateAt(candidate, sequence));
        }
        boolean changed = !baselineStates.equals(candidateStates)
                || baseline.anyProviderTurnReachedOutputLimit()
                        != candidate.anyProviderTurnReachedOutputLimit();
        String aggregate = "row aggregate "
                + outputLimitAggregateState(baseline)
                + (baseline.anyProviderTurnReachedOutputLimit()
                        == candidate.anyProviderTurnReachedOutputLimit()
                                ? ""
                                : " → " + outputLimitAggregateState(candidate));
        if (!changed) {
            List<String> unchangedPerTurn = new ArrayList<>();
            for (int index = 0; index < baselineStates.size(); index++) {
                unchangedPerTurn.add("#" + (index + 1) + " " + baselineStates.get(index));
            }
            return "unchanged: " + String.join("; ", unchangedPerTurn) + "; " + aggregate;
        }
        List<String> changedPerTurn = new ArrayList<>();
        for (int index = 0; index < baselineStates.size(); index++) {
            changedPerTurn.add("#" + (index + 1) + " " + baselineStates.get(index)
                    + " → " + candidateStates.get(index));
        }
        return String.join("; ", changedPerTurn) + "; " + aggregate;
    }

    private static String outputLimitStateAt(ToolCompatibilityRow row, int sequence) {
        if (sequence > row.providerTurns().size()) {
            return "absent";
        }
        return switch (row.providerTurns().get(sequence - 1).outputLimitState()) {
            case UNOBSERVABLE -> "unobservable";
            case NOT_REACHED -> "not reached";
            case REACHED -> "reached";
        };
    }

    private static String outputLimitAggregateState(ToolCompatibilityRow row) {
        return row.anyProviderTurnReachedOutputLimit() ? "reached" : "not reached";
    }

    private static String completionTokenDelta(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        Integer baselineTokens = baseline.aggregateUsage().completionTokens();
        Integer candidateTokens = candidate.aggregateUsage().completionTokens();
        return tokenValue(baselineTokens) + " → " + tokenValue(candidateTokens)
                + " (delta " + tokenDelta(baselineTokens, candidateTokens) + ")";
    }

    private static String tokenValue(Integer tokens) {
        return tokens == null ? "n/a" : Integer.toString(tokens);
    }

    private static String tokenDelta(Integer baseline, Integer candidate) {
        if (baseline == null || candidate == null) {
            return "n/a";
        }
        return signed(candidate - baseline);
    }

    private static String latencyDelta(ToolCompatibilityRow baseline, ToolCompatibilityRow candidate) {
        long baselineMillis = baseline.rowLatency().toMillis();
        long candidateMillis = candidate.rowLatency().toMillis();
        return baselineMillis + " ms → " + candidateMillis + " ms (" + signed(candidateMillis - baselineMillis)
                + " ms)";
    }

    private static String signed(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private record RowKey(String caseId, int repetition) {}

    @FunctionalInterface
    private interface PairConsumer {
        void accept(String caseId, int repetition, ToolCompatibilityRow baseline, ToolCompatibilityRow candidate);
    }
}
