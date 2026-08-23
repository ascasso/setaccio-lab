package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.util.Locale;

/** Deterministic multidimensional T3.4 summary without a model ranking. */
final class ToolCompatibilityCohortReport {

    String render(
            ToolCompatibilityCohortResult result,
            String rawPath,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        if (result == null || codeBaseline == null) {
            throw new IllegalArgumentException("cohort result and code baseline are required");
        }
        if (!ToolCompatibilityCohortResult.RAW_FILENAME.equals(rawPath)
                || rawSha256 == null
                || !rawSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "cohort raw evidence identity must use the locked filename and SHA-256");
        }
        ToolCompatibilityCohortAnalysis cohortAnalysis =
                new ToolCompatibilityCohortAnalyzer().analyze(result);

        StringBuilder out = new StringBuilder(
                "# Tool Compatibility Cohort Multidimensional Summary\n\n");
        out.append("## Protocol\n\n")
                .append("- Suite: `").append(result.suite()).append("`\n")
                .append("- Provider: `").append(result.provider()).append("`\n")
                .append("- Execution engine: `").append(result.executionEngine()).append("`\n")
                .append("- Execution strategy: `").append(result.executionStrategy()).append("`\n")
                .append("- Pull strategy: `").append(result.pullModelStrategy()).append("`\n")
                .append("- Ollama runtime version: `")
                .append(result.ollamaRuntimeVersion()).append("`\n")
                .append("- Prompt condition: `")
                .append(result.promptCondition().wireValue()).append("`\n")
                .append("- Prompt ID/version: `").append(result.systemPromptIdentity().id())
                .append("` / `").append(result.systemPromptIdentity().version()).append("`\n")
                .append("- Cohort schedule: `").append(result.cohortSchedule().sha256())
                .append("`\n")
                .append("- Models: `").append(result.orderedModels().size()).append("`\n")
                .append("- Rows per model: `").append(ToolCompatibilityProtocol.ROW_COUNT)
                .append("`\n")
                .append("- Total retained rows: `")
                .append(result.modelRuns().stream().mapToInt(run -> run.rows().size()).sum())
                .append("`\n")
                .append("- Observed artifact/runtime formats: `")
                .append(cohortAnalysis.observedArtifactRuntimeFormats().isEmpty()
                        ? "unavailable"
                        : String.join(", ", cohortAnalysis.observedArtifactRuntimeFormats()))
                .append("`\n")
                .append("- Artifact/runtime format coverage complete: `")
                .append(cohortAnalysis.artifactRuntimeFormatCoverageComplete()).append("`\n")
                .append("- Mixed artifact/runtime formats: `")
                .append(cohortAnalysis.mixedArtifactRuntimeFormats())
                .append("`\n\n");

        out.append("## Evidence\n\n")
                .append("- Raw result: `").append(rawPath).append("`\n")
                .append("- Raw SHA-256: `").append(rawSha256).append("`\n")
                .append("- Git commit: `").append(codeBaseline.gitCommit()).append("`\n")
                .append("- Working tree dirty: `")
                .append(codeBaseline.workingTreeDirty()).append("`\n\n");

        out.append("## Bound Human Decision\n\n")
                .append("- Decision: `")
                .append(result.humanDecision().decision().name().toLowerCase(java.util.Locale.ROOT))
                .append("`\n")
                .append("- Baseline run: `")
                .append(result.humanDecision().binding().baselineRunId()).append("`\n")
                .append("- Candidate run: `")
                .append(result.humanDecision().binding().candidateRunId()).append("`\n")
                .append("- Prompt catalog digest: `")
                .append(result.humanDecision().binding().promptCatalogDigest()).append("`\n")
                .append("- Comparison report digest: `")
                .append(result.humanDecision().binding().comparisonReportDigest()).append("`\n")
                .append("- Review date: `")
                .append(result.humanDecision().binding().reviewDate()).append("`\n\n");

        out.append("## Ordered Per-Model Analysis\n\n");
        for (ToolCompatibilityCohortAnalysis.ModelAnalysis model : cohortAnalysis.models()) {
            ToolCompatibilityCohortModelIdentity identity = model.modelIdentity();
            ToolCompatibilityAnalysis analysis = model.compatibility();
            out.append("### ").append(identity.cohortPosition()).append(". `")
                    .append(identity.effectiveInstalledTag()).append("` (`")
                    .append(identity.role().name().toLowerCase(Locale.ROOT))
                    .append("`)\n\n")
                    .append("- Requested tag: `").append(identity.requestedTag()).append("`\n")
                    .append("- Digest: `").append(identity.digest()).append("`\n")
                    .append("- Seed semantics: `").append(identity.seedSemantics()).append("`\n")
                    .append("- Model-family provenance: ")
                    .append(metadata(identity.metadata().familyProvenance())).append("\n")
                    .append("- Artifact/runtime format: ")
                    .append(metadata(identity.metadata().artifactRuntimeFormat())).append("\n")
                    .append("- Quantization/precision: ")
                    .append(metadata(identity.metadata().quantizationOrPrecision())).append("\n")
                    .append("- Template fingerprint: ")
                    .append(metadata(identity.metadata().templateFingerprint())).append("\n")
                    .append("- Tool capability metadata: ")
                    .append(metadata(identity.metadata().toolCapability())).append("\n")
                    .append("- Thinking-mode metadata: ")
                    .append(metadata(identity.metadata().thinkingMode())).append("\n\n");
            compatibility(out, analysis);
            discipline(out, analysis, model.discipline());
            arguments(out, analysis, model.arguments());
            multiStep(out, model.multiStepBehavior());
            failureRecovery(out, model.failureRecovery());
            outputBehavior(out, model.outputBehavior());
            efficiency(out, model.efficiency());
            incompleteObservations(out, identity, model.efficiency());
        }

        out.append("## Interpretation Boundary\n\n")
                .append("This is a deterministic per-model compatibility projection. It preserves model ")
                .append("order and keeps compatibility, discipline, arguments, multi-step behavior, ")
                .append("failure recovery, output behavior, and efficiency separate. Response-format, ")
                .append("error-reporting, and success-claim counts are lexical marker observations, not ")
                .append("semantic judgments. Token totals state their provider-turn coverage, and tokens ")
                .append("per passing row is unavailable unless every passing row has complete usage. ")
                .append("Latency and token observations describe each deployed tag, digest, artifact/runtime ")
                .append("format, and the recorded Ollama version; mixed-format differences are not attributed ")
                .append("solely to model weights, architecture, or family. This report produces no aggregate ")
                .append("score, winner, semantic quality judgment, general capability claim, or ")
                .append("backend-normalized performance comparison.\n");
        return out.toString();
    }

    private static void compatibility(
            StringBuilder out,
            ToolCompatibilityAnalysis analysis
    ) {
        ToolCompatibilityAnalysis.InvocationSummary invocation = analysis.invocation();
        ToolCompatibilityAnalysis.ToolExecutionSummary execution = analysis.toolExecution();
        ToolCompatibilityAnalysis.CompletionSummary completion = analysis.completion();
        out.append("#### Compatibility\n\n")
                .append("- Planned rows: `").append(invocation.plannedRows()).append("`\n")
                .append("- Completed logical row attempts: `")
                .append(invocation.completedLogicalRowAttempts()).append("`\n")
                .append("- Observed provider turns: `")
                .append(invocation.observedProviderTurns()).append("`\n")
                .append("- Successful provider turns: `")
                .append(invocation.successfulProviderTurns()).append("`\n")
                .append("- Valid tool calls: `").append(execution.validToolCalls()).append("`\n")
                .append("- Callback executions succeeded / failed: `")
                .append(execution.callbackExecutionSucceeded()).append(" / ")
                .append(execution.callbackExecutionFailed()).append("`\n")
                .append("- Final responses present: `")
                .append(completion.finalResponsesPresent()).append("`\n")
                .append("- Final contracts passed: `")
                .append(completion.finalContractsPassed()).append("`\n\n");
    }

    private static void discipline(
            StringBuilder out,
            ToolCompatibilityAnalysis analysis,
            ToolCompatibilityCohortAnalysis.Discipline discipline
    ) {
        ToolCompatibilityAnalysis.ToolSelectionSummary selection = analysis.toolSelection();
        out.append("#### Discipline\n\n")
                .append("- Required tool selections / missing: `")
                .append(selection.requiredToolSelections()).append(" / ")
                .append(selection.requiredToolsMissing()).append("`\n")
                .append("- Forbidden tool selections: `")
                .append(selection.forbiddenToolSelections()).append("`\n")
                .append("- Unnecessary tool calls: `")
                .append(selection.unnecessaryToolCalls()).append("`\n")
                .append("- Valid abstentions: `").append(selection.validAbstentions()).append("`\n")
                .append("- No-match rows planned: `")
                .append(discipline.noMatchPlannedRows()).append("`\n")
                .append("- No-match call and arguments matched: `")
                .append(discipline.noMatchRowsWithExpectedCallAndArguments()).append("`\n")
                .append("- No-match responses retained: `")
                .append(discipline.noMatchResponsesRetained()).append("`\n")
                .append("- No-match contracts passed: `")
                .append(discipline.noMatchContractsPassed()).append("`\n\n");
    }

    private static void arguments(
            StringBuilder out,
            ToolCompatibilityAnalysis analysis,
            ToolCompatibilityCohortAnalysis.ArgumentDetails details
    ) {
        ToolCompatibilityAnalysis.ToolArgumentSummary arguments = analysis.toolArguments();
        out.append("#### Arguments\n\n")
                .append("- Observed tool calls: `").append(arguments.observedToolCalls()).append("`\n")
                .append("- Raw JSON valid / invalid: `")
                .append(arguments.rawJsonValidCalls()).append(" / ")
                .append(arguments.rawJsonInvalidCalls()).append("`\n")
                .append("- Declared schema valid / invalid / unobservable: `")
                .append(arguments.declaredSchemaValidCalls()).append(" / ")
                .append(arguments.declaredSchemaInvalidCalls()).append(" / ")
                .append(arguments.declaredSchemaUnobservableCalls()).append("`\n")
                .append("- Expected values matched / mismatched / not reached: `")
                .append(arguments.expectedArgumentsMatchedCalls()).append(" / ")
                .append(arguments.expectedArgumentsMismatchedCalls()).append(" / ")
                .append(arguments.expectedArgumentsNotReachedCalls()).append("`\n")
                .append("- Missing required argument calls: `")
                .append(details.missingRequiredArgumentCalls()).append("`\n")
                .append("- Unknown argument calls: `")
                .append(details.unknownArgumentCalls()).append("`\n")
                .append("- Expected-value mismatch calls: `")
                .append(details.expectedValueMismatchCalls()).append("`\n\n");
    }

    private static void multiStep(
            StringBuilder out,
            ToolCompatibilityCohortAnalysis.MultiStepBehavior behavior
    ) {
        out.append("#### Multi-Step Behavior\n\n")
                .append("- Planned rows: `").append(behavior.plannedRows()).append("`\n")
                .append("- First expected call correct: `")
                .append(behavior.firstExpectedCallCorrect()).append("`\n")
                .append("- Second expected call correct: `")
                .append(behavior.secondExpectedCallCorrect()).append("`\n")
                .append("- Dependency order correct: `")
                .append(behavior.dependencyOrderCorrect()).append("`\n")
                .append("- Continued after first callback: `")
                .append(behavior.continuedAfterFirstCallback()).append("`\n")
                .append("- Premature final responses: `")
                .append(behavior.prematureFinalResponses()).append("`\n")
                .append("- Rows with duplicate calls: `")
                .append(behavior.rowsWithDuplicateCalls()).append("`\n\n");
    }

    private static void failureRecovery(
            StringBuilder out,
            ToolCompatibilityCohortAnalysis.FailureRecovery recovery
    ) {
        out.append("#### Failure Recovery\n\n")
                .append("- Planned deterministic-failure rows: `")
                .append(recovery.plannedRows()).append("`\n")
                .append("- Deterministic callback failures retained: `")
                .append(recovery.deterministicCallbackFailuresRetained()).append("`\n")
                .append("- Error-reporting markers present: `")
                .append(recovery.errorReportingMarkersPresent()).append("`\n")
                .append("- Error-reporting lexical markers: `error`, `fail`, `unable`\n")
                .append("- Success-claim markers after failure: `")
                .append(recovery.successClaimMarkersAfterFailure()).append("`\n")
                .append("- Success-claim lexical markers: `success`, `succeeded`, ")
                .append("`completed successfully`\n")
                .append("- Empty final responses: `")
                .append(recovery.emptyFinalResponses()).append("`\n\n");
    }

    private static void outputBehavior(
            StringBuilder out,
            ToolCompatibilityCohortAnalysis.OutputBehavior behavior
    ) {
        out.append("#### Output Behavior\n\n")
                .append("- Final responses present / empty: `")
                .append(behavior.finalResponsesPresent()).append(" / ")
                .append(behavior.finalResponsesEmpty()).append("`\n")
                .append("- Rows with visible reasoning markers: `")
                .append(behavior.rowsWithVisibleReasoningMarkers()).append("`\n")
                .append("- Rows reaching output limit: `")
                .append(behavior.rowsReachingOutputLimit()).append("`\n")
                .append("- Final responses with format-pollution markers: `")
                .append(behavior.finalResponsesWithFormatPollutionMarkers()).append("`\n")
                .append("- Format-pollution lexical markers: code fence, tool-call tag, ")
                .append("or tool-call JSON envelope\n")
                .append("- Median final-response characters (descriptive concision): `")
                .append(decimalOrUnavailable(behavior.medianFinalResponseCharacters())).append("`\n")
                .append("- Observed final-response character range: `")
                .append(range(
                        behavior.minimumFinalResponseCharacters(),
                        behavior.maximumFinalResponseCharacters()))
                .append("`\n\n");
    }

    private static void efficiency(
            StringBuilder out,
            ToolCompatibilityCohortAnalysis.Efficiency efficiency
    ) {
        out.append("#### Efficiency\n\n")
                .append("- Passing rows: `").append(efficiency.passingRows()).append("`\n")
                .append("- Median successful-row latency: `")
                .append(milliseconds(efficiency.medianSuccessfulRowLatencyMillis()))
                .append("`\n")
                .append("- Observed successful-row latency range: `")
                .append(millisecondsRange(
                        efficiency.minimumSuccessfulRowLatencyMillis(),
                        efficiency.maximumSuccessfulRowLatencyMillis()))
                .append("`\n")
                .append("- Observed prompt tokens: `")
                .append(tokens(efficiency.promptTokens())).append("`\n")
                .append("- Observed completion tokens: `")
                .append(tokens(efficiency.completionTokens())).append("`\n")
                .append("- Observed total tokens: `")
                .append(tokens(efficiency.totalTokens())).append("`\n")
                .append("- Total tokens per passing row: `")
                .append(decimalOrUnavailable(efficiency.totalTokensPerPassingRow()))
                .append("`\n\n");
    }

    private static void incompleteObservations(
            StringBuilder out,
            ToolCompatibilityCohortModelIdentity identity,
            ToolCompatibilityCohortAnalysis.Efficiency efficiency
    ) {
        out.append("#### Incomplete or Unsupported Observations\n\n")
                .append("- Seed option: `")
                .append(identity.seedSemantics()
                        == ToolCompatibilityCohortSeedSemantics.SUPPORTED
                                ? "supported and explicit"
                                : "unsupported and omitted")
                .append("`\n")
                .append("- Prompt-token coverage complete: `")
                .append(efficiency.promptTokens().complete()).append("`\n")
                .append("- Completion-token coverage complete: `")
                .append(efficiency.completionTokens().complete()).append("`\n")
                .append("- Total-token coverage complete: `")
                .append(efficiency.totalTokens().complete()).append("`\n")
                .append("- Unavailable identity metadata: `")
                .append(unavailableMetadata(identity.metadata())).append("`\n\n");
    }

    private static String tokens(ToolCompatibilityCohortAnalysis.TokenObservation observation) {
        if (observation.observedTotal() == null) {
            return "unavailable (0/" + observation.providerTurns() + " provider turns)";
        }
        return observation.observedTotal()
                + " (" + observation.observedProviderTurns()
                + "/" + observation.providerTurns() + " provider turns)";
    }

    private static String decimalOrUnavailable(Double value) {
        return value == null ? "unavailable" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String range(Number minimum, Number maximum) {
        return minimum == null || maximum == null
                ? "unavailable"
                : minimum + "-" + maximum;
    }

    private static String milliseconds(Double value) {
        return value == null
                ? "unavailable"
                : String.format(Locale.ROOT, "%.1f ms", value);
    }

    private static String millisecondsRange(Long minimum, Long maximum) {
        return minimum == null || maximum == null
                ? "unavailable"
                : minimum + "-" + maximum + " ms";
    }

    private static String unavailableMetadata(ToolCompatibilityCohortModelMetadata metadata) {
        java.util.List<String> unavailable = new java.util.ArrayList<>();
        addUnavailable(unavailable, "size bytes", metadata.sizeBytes());
        addUnavailable(unavailable, "family provenance", metadata.familyProvenance());
        addUnavailable(unavailable, "artifact/runtime format", metadata.artifactRuntimeFormat());
        addUnavailable(unavailable, "quantization/precision", metadata.quantizationOrPrecision());
        addUnavailable(unavailable, "template fingerprint", metadata.templateFingerprint());
        addUnavailable(
                unavailable,
                "default system-prompt fingerprint",
                metadata.defaultSystemPromptFingerprint());
        addUnavailable(unavailable, "tool capability", metadata.toolCapability());
        addUnavailable(unavailable, "thinking mode", metadata.thinkingMode());
        return unavailable.isEmpty() ? "none" : String.join(", ", unavailable);
    }

    private static void addUnavailable(
            java.util.List<String> unavailable,
            String label,
            ToolCompatibilityMetadataField field
    ) {
        if (field.availability() == ToolCompatibilityMetadataField.Availability.UNAVAILABLE) {
            unavailable.add(label);
        }
    }

    private static String metadata(ToolCompatibilityMetadataField field) {
        return field.availability() == ToolCompatibilityMetadataField.Availability.AVAILABLE
                ? "`" + field.value() + "`"
                : "`unavailable`";
    }
}
