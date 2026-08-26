package com.setaccio.lab.evaluation;

import java.util.Map;

/** Stable public-safe aggregate report for verified five-arm breakpoint evidence. */
final class LocalEvaluationBreakpointComparisonReport {

    String render(
            Map<Integer, String> runIds,
            LocalEvaluationBreakpointEvidence.ArmSnapshot canonical,
            Map<Integer, LocalEvaluationBudgetComparison.ArmMetrics> metrics
    ) {
        StringBuilder out = new StringBuilder("# Offline Fact-Check Output-Budget Breakpoint Study\n\n");
        out.append("- Shared clean Git commit: `").append(canonical.manifest().codeBaseline().gitCommit()).append("`\n");
        out.append("- Judge digest: `").append(canonical.result().judgeModelIdentity().digest()).append("`\n");
        out.append("- Shared protocol: 6 fixtures × 2 repetitions = 12 sequential rows per arm; 60 rows total.\n");
        out.append("- Verification: passed before aggregation; token budget is the only permitted material difference.\n\n");
        out.append("This deterministic report describes verified evidence only. It does not select a judge, "
                + "claim general factuality or reliability, or infer a provider finish reason or causal mechanism.\n\n");
        out.append("## Arm identities\n\n| Token budget | Saved run |\n| ---: | --- |\n");
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            out.append("| ").append(tokens).append(" | `").append(runIds.get(tokens)).append("` |\n");
        }
        out.append("\n## Verdict and agreement yields\n\n");
        header(out);
        row(out, "Valid normalized verdict", metrics,
                value -> value.validVerdicts() + "/" + value.plannedRows());
        row(out, "Empty response", metrics, value -> value.emptyResponses() + "/" + value.plannedRows());
        row(out, "Malformed verdict", metrics, value -> value.malformedVerdicts() + "/" + value.plannedRows());
        row(out, "Agreement among valid verdicts", metrics, value -> value.validVerdicts() == 0
                ? "n/a" : value.agreementMatches() + "/" + value.validVerdicts()
                + "; mismatch " + value.agreementMismatches());
        out.append("\n## Normalized label and repetition yields\n\n");
        header(out);
        row(out, "Supported / yes", metrics, value -> value.supportedLabelVerdicts() + "/" + value.plannedRows());
        row(out, "Unsupported / no", metrics, value -> value.unsupportedLabelVerdicts() + "/" + value.plannedRows());
        row(out, "Consistent fixture repetitions", metrics,
                value -> value.repetitions().consistent() + "/6");
        row(out, "Disagreeing fixture repetitions", metrics,
                value -> value.repetitions().disagreements() + "/6");
        row(out, "Incomplete fixture comparisons", metrics,
                value -> value.repetitions().incomplete() + "/6");
        out.append("\n## Output-limit proxy and latency\n\n");
        out.append("Completion-token counts are an output-limit proxy; the saved evidence has no provider finish reason.\n\n");
        header(out);
        row(out, "At configured maximum", metrics,
                value -> value.outputLimit().atConfiguredLimit() + "/" + value.plannedRows());
        row(out, "Below configured maximum", metrics,
                value -> value.outputLimit().belowConfiguredLimit() + "/" + value.plannedRows());
        row(out, "Completion tokens unavailable", metrics,
                value -> value.outputLimit().unavailable() + "/" + value.plannedRows());
        row(out, "Completion-token distribution", metrics,
                value -> value.completionTokens().distribution().toString());
        row(out, "Per-row latency", metrics, value -> value.latency().display());
        out.append("\nAgreement excludes rows without a valid normalized verdict. Latency includes every "
                + "classified attempt and is descriptive of these deployed arms only.\n");
        return out.toString();
    }

    private static void header(StringBuilder out) {
        out.append("| Metric | 64 tokens | 96 tokens | 128 tokens | 192 tokens | 256 tokens |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
    }

    private static void row(
            StringBuilder out,
            String label,
            Map<Integer, LocalEvaluationBudgetComparison.ArmMetrics> metrics,
            java.util.function.Function<LocalEvaluationBudgetComparison.ArmMetrics, String> value
    ) {
        out.append("| ").append(label);
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            out.append(" | ").append(value.apply(metrics.get(tokens)));
        }
        out.append(" |\n");
    }
}
