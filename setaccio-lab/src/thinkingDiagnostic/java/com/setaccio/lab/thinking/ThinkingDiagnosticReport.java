package com.setaccio.lab.thinking;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.util.Map;

/**
 * Renders the deterministic public-safe summary.
 *
 * <p>Aggregates only. Recorded assistant content, reasoning text, evaluator output, and per-row
 * payloads stay in the ignored raw artifact and are never written here.
 */
public final class ThinkingDiagnosticReport {

    public String render(
            ThinkingDiagnosticResult result,
            ThinkingDiagnosticAnalyzer.Analysis analysis,
            String rawPath,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# Reasoning and Empty-Content Diagnostic\n\n");
        out.append("- Raw result: `").append(rawPath).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Git commit: `")
                .append(codeBaseline == null ? "unavailable" : codeBaseline.gitCommit())
                .append("`\n");
        out.append("- Evidence status: `")
                .append(codeBaseline != null && !codeBaseline.workingTreeDirty()
                        ? "clean-baseline candidate" : "dirty-worktree diagnostic")
                .append("`\n");
        out.append("- Protocol version: `").append(result.protocolVersion()).append("`\n");
        out.append("- Provider / endpoint category: `").append(result.provider())
                .append("` / `").append(result.endpointCategory()).append("`\n");
        out.append("- Ollama version: `").append(result.ollamaVersion()).append("`\n");
        for (ThinkingDiagnosticModelIdentity identity : result.modelIdentities()) {
            out.append("- Model (").append(identity.role().name().toLowerCase(java.util.Locale.ROOT))
                    .append("): requested `").append(identity.requestedModel())
                    .append("`, installed `").append(identity.normalizedInstalledName())
                    .append("`, digest `").append(identity.digest())
                    .append("`, advertises thinking: `").append(identity.advertisesThinking())
                    .append("`\n");
        }
        out.append("- Prompt: `").append(result.promptId()).append("` version `")
                .append(result.promptVersion()).append("` (`").append(result.promptSha256()).append("`)\n");
        out.append("- Fixture catalog: `").append(result.fixtureCatalogId()).append("` version `")
                .append(result.fixtureCatalogVersion()).append("` (`")
                .append(result.fixtureCatalogSha256()).append("`)\n");
        out.append("- Reasoning policy source: `")
                .append(ThinkingDiagnosticProtocol.reasoningPolicySource(result.arms()))
                .append("`\n");
        if (result.protocolVersion() == ThinkingDiagnosticProtocol.VERSION) {
            out.append("- Prompt delivery: `").append(result.promptDelivery()).append("`\n");
            out.append("- Policy comparison: `").append(result.policyComparison()).append("`\n");
            out.append("- Boundary comparison: `").append(result.boundaryComparison()).append("`\n");
        }
        out.append("- Temperature: `").append(result.temperature()).append("`; seed: `")
                .append(result.seed()).append("`\n");
        out.append("- Attempt policy: exactly ").append(result.maxAttempts())
                .append("; timeout: ").append(result.requestTimeoutMillis()).append(" ms\n");
        out.append("- Pull strategy: `").append(result.pullModelStrategy()).append("`\n");
        out.append("- Retained rows: ").append(result.rows().size()).append("\n");

        out.append("\n## Arms\n\n");
        if (result.protocolVersion() == ThinkingDiagnosticProtocol.LEGACY_VERSION) {
            out.append("| Arm | Model role | Reasoning policy | Budget | Rows | Content | Thinking | At budget | Evaluated tokens |\n");
            out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        } else {
            out.append("| Arm | Execution boundary | Model role | Reasoning policy | Budget | Rows | Content | Thinking | At budget | Evaluated tokens |\n");
            out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        }
        for (ThinkingDiagnosticAnalyzer.ArmSummary arm : analysis.armSummaries()) {
            out.append("| `").append(arm.armId()).append("` | ");
            if (result.protocolVersion() == ThinkingDiagnosticProtocol.VERSION) {
                out.append('`').append(arm.executionBoundary().id()).append("` | ");
            }
            out.append('`').append(arm.modelRole().name()).append("` | `")
                    .append(arm.reasoningPolicy()).append("` | ")
                    .append(arm.maxOutputTokens()).append(" | ")
                    .append(arm.rowCount()).append(" | ")
                    .append(arm.rowsWithContent()).append(" | ")
                    .append(arm.rowsWithThinking()).append(" | ")
                    .append(arm.rowsAtBudget()).append(" | ")
                    .append(range(arm.minEvaluatedOutputTokens(), arm.maxEvaluatedOutputTokens()))
                    .append(" |\n");
        }

        out.append("\n## Outcomes\n\n");
        for (ThinkingDiagnosticAnalyzer.ArmSummary arm : analysis.armSummaries()) {
            out.append("- `").append(arm.armId()).append("`: ")
                    .append(counts(arm.outcomeCounts())).append("\n");
        }

        out.append("\n## Finish reasons\n\n");
        for (ThinkingDiagnosticAnalyzer.ArmSummary arm : analysis.armSummaries()) {
            out.append("- `").append(arm.armId()).append("`: ")
                    .append(textCounts(arm.finishReasonCounts())).append("\n");
        }

        out.append("\n## Retained evidence\n\n");
        if (result.protocolVersion() == ThinkingDiagnosticProtocol.LEGACY_VERSION) {
            out.append("The ignored raw artifact retains, for every row, the assistant content and any"
                    + " reasoning field as two separate values, the finish reason, the evaluated"
                    + " output-token count, the requested reasoning policy, whether the artifact"
                    + " advertises the thinking capability, and the classified outcome. Recorded"
                    + " content, reasoning text, and evaluator output are never copied into tracked"
                    + " files.\n");
        } else {
            out.append("The ignored raw artifact retains, for every row, the assistant content and any"
                    + " reasoning field as two separate values, the finish reason, the evaluated"
                    + " output-token count, the requested reasoning policy, the pre-registered"
                    + " execution boundary, whether the artifact advertises the thinking capability,"
                    + " and the classified outcome. Chat-boundary rows retain the fixture identities"
                    + " and expected verdict as input provenance, but record no normalized judge"
                    + " verdict or expectation match. Recorded content, reasoning text, and evaluator"
                    + " output are never copied into tracked files.\n");
        }

        out.append("\n## Interpretation boundary\n\n");
        out.append("This records the shape of provider responses under one fixed prompt, fixture"
                + " catalog, seed, temperature, and attempt policy, for the exact artifacts and"
                + " budgets named above. It is a new diagnostic protocol, not a rerun, repair,"
                + " replacement, or reanalysis of any earlier suite, and it writes no earlier"
                + " suite's evidence. It is not an answer-correctness, factuality, semantic,"
                + " quality, reliability, ranking, or model-selection claim, and an advertised"
                + " capability describes an artifact manifest rather than runtime behavior.\n");
        return out.toString();
    }

    private static String range(Integer min, Integer max) {
        if (min == null || max == null) {
            return "unavailable";
        }
        return min.equals(max) ? String.valueOf(min) : min + "–" + max;
    }

    private static String counts(Map<ThinkingDiagnosticOutcome, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        counts.forEach((outcome, count) -> {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append('`').append(outcome.name()).append("` ").append(count);
        });
        return out.toString();
    }

    private static String textCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        counts.forEach((reason, count) -> {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append('`').append(reason).append("` ").append(count);
        });
        return out.toString();
    }
}
