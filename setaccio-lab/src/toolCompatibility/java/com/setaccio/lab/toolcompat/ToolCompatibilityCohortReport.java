package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.util.List;

/** Deterministic structural summary for one complete cohort without a model ranking. */
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
        ToolCompatibilityAnalyzer analyzer = new ToolCompatibilityAnalyzer();
        List<ToolCompatibilityAnalysis> analyses = result.modelRuns().stream()
                .map(analyzer::analyzeCohortModelRun)
                .toList();

        StringBuilder out = new StringBuilder("# Tool Compatibility Cohort Structural Summary\n\n");
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

        out.append("## Ordered Model Segments\n\n");
        for (int index = 0; index < result.modelRuns().size(); index++) {
            ToolCompatibilityCohortModelRun run = result.modelRuns().get(index);
            ToolCompatibilityCohortModelIdentity identity = run.modelIdentity();
            ToolCompatibilityAnalysis analysis = analyses.get(index);
            out.append("### ").append(identity.cohortPosition()).append(". `")
                    .append(identity.effectiveInstalledTag()).append("` (`")
                    .append(identity.role().name().toLowerCase(java.util.Locale.ROOT))
                    .append("`)\n\n")
                    .append("- Requested tag: `").append(identity.requestedTag()).append("`\n")
                    .append("- Digest: `").append(identity.digest()).append("`\n")
                    .append("- Seed semantics: `").append(identity.seedSemantics()).append("`\n")
                    .append("- Artifact/runtime format: ")
                    .append(metadata(identity.metadata().artifactRuntimeFormat())).append("\n")
                    .append("- Quantization/precision: ")
                    .append(metadata(identity.metadata().quantizationOrPrecision())).append("\n")
                    .append("- Tool capability metadata: ")
                    .append(metadata(identity.metadata().toolCapability())).append("\n")
                    .append("- Thinking-mode metadata: ")
                    .append(metadata(identity.metadata().thinkingMode())).append("\n")
                    .append("- Retained rows: `").append(run.rows().size()).append("`\n")
                    .append("- Completed logical row attempts: `")
                    .append(analysis.invocation().completedLogicalRowAttempts()).append("`\n")
                    .append("- Observed provider turns: `")
                    .append(analysis.invocation().observedProviderTurns()).append("`\n")
                    .append("- Exact call sequences matched: `")
                    .append(analysis.toolSelection().exactExpectedCallSequencesMatched())
                    .append("`\n")
                    .append("- Final responses present: `")
                    .append(analysis.completion().finalResponsesPresent()).append("`\n")
                    .append("- Final contracts passed: `")
                    .append(analysis.completion().finalContractsPassed()).append("`\n\n");
        }

        out.append("## Interpretation Boundary\n\n")
                .append("This is a deterministic structural and per-model compatibility projection. ")
                .append("It preserves model order and keeps observations separate. It does not produce ")
                .append("an aggregate score, winner, semantic quality judgment, general capability ")
                .append("claim, or backend-normalized performance comparison.\n");
        return out.toString();
    }

    private static String metadata(ToolCompatibilityMetadataField field) {
        return field.availability() == ToolCompatibilityMetadataField.Availability.AVAILABLE
                ? "`" + field.value() + "`"
                : "`unavailable`";
    }
}
