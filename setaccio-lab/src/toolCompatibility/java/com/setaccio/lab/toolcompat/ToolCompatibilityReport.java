package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.util.Locale;

/** Deterministic Markdown projection used by the later offline evidence slice. */
final class ToolCompatibilityReport {

    String render(ToolCompatibilityResult result, ToolCompatibilityAnalysis analysis) {
        if (result == null || analysis == null) {
            throw new IllegalArgumentException("result and analysis are required");
        }
        ToolCompatibilityAnalysis expectedAnalysis = new ToolCompatibilityAnalyzer().analyze(result);
        if (!expectedAnalysis.equals(analysis)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool compatibility report analysis does not match its canonical result");
        }
        StringBuilder report = new StringBuilder();
        report.append("# Tool Compatibility Deterministic Summary\n\n");
        protocol(report, result);
        invocation(report, analysis.invocation());
        selection(report, analysis.toolSelection());
        arguments(report, analysis.toolArguments());
        execution(report, analysis.toolExecution());
        completion(report, analysis.completion());
        reasoning(report, analysis.reasoningStyleOutput());
        usage(report, analysis.usageAndLatency());
        diagnostics(report, analysis);
        report.append("\n## Interpretation Boundary\n\n")
                .append("This report is a deterministic compatibility summary. It keeps invocation, ")
                .append("selection, argument, callback, completion, observable reasoning-style, usage, ")
                .append("and latency dimensions separate. It makes no semantic quality judgment and ")
                .append("does not combine dimensions into a score or model ordering. Successful-row ")
                .append("latency uses only the median and observed range.\n");
        return report.toString();
    }

    private static void protocol(StringBuilder report, ToolCompatibilityResult result) {
        report.append("## Protocol\n\n");
        line(report, "Suite", result.suite());
        line(report, "Protocol version", Integer.toString(result.protocolVersion()));
        line(report, "Provider", result.provider());
        line(report, "Execution engine", result.executionEngine());
        line(report, "Execution strategy", result.executionStrategy());
        line(report, "Pull strategy", result.pullModelStrategy());
        line(report, "Requested model", result.modelIdentity().requestedModel());
        line(report, "Effective model", result.modelIdentity().effectiveModel());
        line(report, "Model digest", result.modelIdentity().digest());
        line(report, "Case oracle", result.caseOracleId() + " v" + result.caseOracleVersion());
        line(report, "Case oracle SHA-256", result.caseOracleSha256());
        line(report, "System prompt", result.systemPromptIdentity().id()
                + " v" + result.systemPromptIdentity().version());
        line(report, "System prompt SHA-256", result.systemPromptIdentity().sha256());
        line(report, "Temperature", Double.toString(result.runSettings().temperature()));
        line(report, "Seeds", result.runSettings().seeds().toString());
        line(report, "Maximum output tokens per provider turn",
                Integer.toString(result.runSettings().maxOutputTokensPerProviderTurn()));
        line(report, "Logical row deadline",
                Duration.ofMillis(result.runSettings().rowTimeoutMillis()).toString());
        line(report, "Attempts per logical row", Integer.toString(result.runSettings().logicalRowAttempts()));
        line(report, "Started", result.startedAt().toString());
        line(report, "Finished", result.finishedAt().toString());
    }

    private static void invocation(
            StringBuilder report,
            ToolCompatibilityAnalysis.InvocationSummary summary
    ) {
        report.append("\n## Invocation\n\n");
        line(report, "Planned rows", summary.plannedRows());
        line(report, "Completed logical row attempts", summary.completedLogicalRowAttempts());
        line(report, "Timed-out logical row attempts", summary.timedOutLogicalRowAttempts());
        line(report, "Observed provider turns", summary.observedProviderTurns());
        line(report, "Successful provider turns", summary.successfulProviderTurns());
        line(report, "Failed provider turns", summary.failedProviderTurns());
        line(report, "Failed provider-turn sequences", failedProviderTurns(summary));
        line(report, "Unavailable models", summary.unavailableModels());
        line(report, "Empty provider turns without a tool call", summary.emptyProviderTurnsWithoutToolCall());
    }

    private static void selection(
            StringBuilder report,
            ToolCompatibilityAnalysis.ToolSelectionSummary summary
    ) {
        report.append("\n## Tool Selection\n\n");
        line(report, "Required tool selections", summary.requiredToolSelections());
        line(report, "Required tools missing", summary.requiredToolsMissing());
        line(report, "Forbidden tool selections", summary.forbiddenToolSelections());
        line(report, "Unnecessary tool calls", summary.unnecessaryToolCalls());
        line(report, "Valid abstentions", summary.validAbstentions());
        line(report, "Exact expected call sequences matched", summary.exactExpectedCallSequencesMatched());
        line(report, "Rows with missing calls", summary.rowsWithMissingCalls());
        line(report, "Rows with additional calls", summary.rowsWithAdditionalCalls());
        line(report, "Rows with reordered calls", summary.rowsWithReorderedCalls());
        line(report, "Rows with duplicate calls", summary.rowsWithDuplicateCalls());
    }

    private static void arguments(
            StringBuilder report,
            ToolCompatibilityAnalysis.ToolArgumentSummary summary
    ) {
        report.append("\n## Tool Arguments\n\n");
        line(report, "Observed tool calls", summary.observedToolCalls());
        line(report, "Raw argument JSON valid", summary.rawJsonValidCalls());
        line(report, "Raw argument JSON invalid", summary.rawJsonInvalidCalls());
        line(report, "Declared schema valid", summary.declaredSchemaValidCalls());
        line(report, "Declared schema invalid", summary.declaredSchemaInvalidCalls());
        line(report, "Declared schema unobservable", summary.declaredSchemaUnobservableCalls());
        line(report, "Declared schema not reached", summary.declaredSchemaNotReachedCalls());
        line(report, "Expected arguments matched", summary.expectedArgumentsMatchedCalls());
        line(report, "Expected arguments mismatched", summary.expectedArgumentsMismatchedCalls());
        line(report, "Expected arguments not reached", summary.expectedArgumentsNotReachedCalls());
        line(report, "Rows where all expected arguments matched",
                summary.rowsWhereAllExpectedArgumentsMatched());
        line(report, "Callback-coerced raw or semantic mismatches",
                summary.callbackCoercedMismatchCalls());
        line(report, "Callback-coerced schema mismatches",
                summary.callbackCoercedSchemaMismatchCalls());
        line(report, "Callback-coerced semantic mismatches",
                summary.callbackCoercedSemanticMismatchCalls());
    }

    private static void execution(
            StringBuilder report,
            ToolCompatibilityAnalysis.ToolExecutionSummary summary
    ) {
        report.append("\n## Tool Execution\n\n");
        line(report, "Valid tool calls", summary.validToolCalls());
        line(report, "Malformed tool calls", summary.malformedToolCalls());
        line(report, "Callback binding succeeded", summary.callbackBindingSucceeded());
        line(report, "Callback binding failed", summary.callbackBindingFailed());
        line(report, "Callback binding unobservable", summary.callbackBindingUnobservable());
        line(report, "Callback binding not reached", summary.callbackBindingNotReached());
        line(report, "Callback execution succeeded", summary.callbackExecutionSucceeded());
        line(report, "Callback execution failed", summary.callbackExecutionFailed());
        line(report, "Callback execution unobservable", summary.callbackExecutionUnobservable());
        line(report, "Callback execution not reached", summary.callbackExecutionNotReached());
        line(report, "Callback results succeeded", summary.callbackResultsSucceeded());
        line(report, "Callback results failed", summary.callbackResultsFailed());
        line(report, "Expected deterministic callback failures retained",
                summary.expectedDeterministicCallbackFailuresRetained());
    }

    private static void completion(
            StringBuilder report,
            ToolCompatibilityAnalysis.CompletionSummary summary
    ) {
        report.append("\n## Completion\n\n");
        line(report, "Final responses present", summary.finalResponsesPresent());
        line(report, "Final responses empty", summary.finalResponsesEmpty());
        line(report, "Final contracts passed", summary.finalContractsPassed());
        line(report, "Tool succeeded but final answer failed", summary.toolSucceededButFinalAnswerFailed());
        line(report, "Provider turns reaching the output limit", summary.providerTurnsReachedOutputLimit());
        line(report, "Rows with any provider turn reaching the output limit",
                summary.rowsWithAnyProviderTurnReachedOutputLimit());
    }

    private static void reasoning(
            StringBuilder report,
            ToolCompatibilityAnalysis.ReasoningStyleSummary summary
    ) {
        report.append("\n## Reasoning-Style Output\n\n");
        line(report, "Think-tag marker detected", summary.thinkTagDetected());
        line(report, "Other reasoning marker detected", summary.otherReasoningMarkerDetected());
        line(report, "Reasoning marker before first tool call",
                summary.reasoningMarkerBeforeFirstToolCall());
        line(report, "Reasoning marker after tool execution",
                summary.reasoningMarkerAfterToolExecution());
        line(report, "Reasoning marker in final response",
                summary.reasoningMarkerInFinalResponse());
    }

    private static void usage(
            StringBuilder report,
            ToolCompatibilityAnalysis.UsageLatencySummary summary
    ) {
        report.append("\n## Usage and Latency\n\n");
        line(report, "Provider turns with complete usage", summary.providerTurnsWithCompleteUsage());
        line(report, "Provider turns with partial usage", summary.providerTurnsWithPartialUsage());
        line(report, "Provider turns with absent usage", summary.providerTurnsWithAbsentUsage());
        line(report, "Median successful-row latency", milliseconds(summary.medianSuccessfulRowLatencyMillis()));
        line(report, "Observed successful-row latency range", latencyRange(summary));

        report.append("\n### Per-Turn Usage\n\n");
        report.append("| Row | Turn | Availability | Prompt | Completion | Total | Latency ms |\n");
        report.append("| ---: | ---: | --- | ---: | ---: | ---: | ---: |\n");
        for (ToolCompatibilityAnalysis.ProviderTurnUsage turn : summary.providerTurnUsage()) {
            report.append("| ").append(turn.rowSequence())
                    .append(" | ").append(turn.providerTurnSequence())
                    .append(" | `").append(turn.usage().availability()).append("` | ")
                    .append(value(turn.usage().promptTokens())).append(" | ")
                    .append(value(turn.usage().completionTokens())).append(" | ")
                    .append(value(turn.usage().totalTokens())).append(" | ")
                    .append(turn.latency().toMillis()).append(" |\n");
        }

        report.append("\n### Per-Row Aggregate Usage\n\n");
        report.append("| Row | Availability | Prompt | Completion | Total |\n");
        report.append("| ---: | --- | ---: | ---: | ---: |\n");
        for (ToolCompatibilityAnalysis.RowUsage row : summary.rowAggregates()) {
            report.append("| ").append(row.rowSequence())
                    .append(" | `").append(row.aggregateUsage().availability()).append("` | ")
                    .append(value(row.aggregateUsage().promptTokens())).append(" | ")
                    .append(value(row.aggregateUsage().completionTokens())).append(" | ")
                    .append(value(row.aggregateUsage().totalTokens())).append(" |\n");
        }
    }

    private static void diagnostics(StringBuilder report, ToolCompatibilityAnalysis analysis) {
        report.append("\n## Deterministic Diagnostics\n\n");
        for (String category : ToolCompatibilityDiagnostic.categories()) {
            line(report, category, analysis.diagnosticCounts().get(category));
        }
        report.append("\n### Failed Contract Primary Categories\n\n");
        report.append("| Row | Case | Repetition | Primary category |\n");
        report.append("| ---: | --- | ---: | --- |\n");
        for (ToolCompatibilityAnalysis.RowDiagnostic diagnostic : analysis.failedContractDiagnostics()) {
            report.append("| ").append(diagnostic.rowSequence())
                    .append(" | `").append(diagnostic.caseId()).append("` | ")
                    .append(diagnostic.repetition()).append(" | `")
                    .append(diagnostic.primaryCategory()).append("` |\n");
        }
    }

    private static String latencyRange(ToolCompatibilityAnalysis.UsageLatencySummary summary) {
        if (summary.minimumSuccessfulRowLatencyMillis() == null) {
            return "not available";
        }
        return summary.minimumSuccessfulRowLatencyMillis()
                + "-" + summary.maximumSuccessfulRowLatencyMillis() + " ms";
    }

    private static String failedProviderTurns(ToolCompatibilityAnalysis.InvocationSummary summary) {
        if (summary.failedProviderTurnSequences().isEmpty()) {
            return "none";
        }
        return summary.failedProviderTurnSequences().stream()
                .map(turn -> "row " + turn.rowSequence() + "/turn " + turn.providerTurnSequence())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String milliseconds(Double value) {
        return value == null ? "not available" : String.format(Locale.ROOT, "%.1f ms", value);
    }

    private static String value(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private static void line(StringBuilder report, String label, Object value) {
        report.append("- ").append(label).append(": `").append(value).append("`\n");
    }
}
