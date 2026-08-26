package com.setaccio.lab.evaluation;

import java.util.Map;

/** Renders the deterministic aggregate-only F3 report after the pair has passed strict parity. */
final class LocalEvaluationBudgetComparisonReport {

    String render(
            LocalEvaluationBudgetComparison.Arm budget64,
            LocalEvaluationBudgetComparison.Arm budget256,
            LocalEvaluationBudgetComparison.ArmMetrics metrics64,
            LocalEvaluationBudgetComparison.ArmMetrics metrics256
    ) {
        StringBuilder out = new StringBuilder("# Offline Fact-Check Output-Budget Comparison\n\n");
        out.append("- 64-token run: `").append(budget64.manifest().runId()).append("`\n");
        out.append("- 256-token run: `").append(budget256.manifest().runId()).append("`\n");
        out.append("- Shared clean Git commit: `")
                .append(budget64.manifest().codeBaseline().gitCommit()).append("`\n");
        out.append("- Judge digest: `")
                .append(budget64.result().judgeModelIdentity().digest()).append("`\n");
        out.append("- Shared protocol: 6 fixtures × 2 repetitions = 12 sequential rows per arm.\n");
        out.append("- Pair verification: passed before comparison; only maximum output tokens differ.\n\n");
        out.append("This is a deterministic aggregate comparison of verified evidence. It does not "
                + "select a judge, claim general factuality or reliability, or interpret causation.\n");

        out.append("\n## Verdict and agreement yields\n\n");
        out.append("| Metric | 64 tokens | 256 tokens |\n");
        out.append("| --- | ---: | ---: |\n");
        row(out, "Valid normalized verdict", metrics64.validVerdicts(), metrics64.plannedRows(),
                metrics256.validVerdicts(), metrics256.plannedRows());
        row(out, "Empty response", metrics64.emptyResponses(), metrics64.plannedRows(),
                metrics256.emptyResponses(), metrics256.plannedRows());
        row(out, "Malformed verdict", metrics64.malformedVerdicts(), metrics64.plannedRows(),
                metrics256.malformedVerdicts(), metrics256.plannedRows());
        agreementRow(out, metrics64, metrics256);

        out.append("\n## Normalized label yields\n\n");
        out.append("| Label | 64 tokens | 256 tokens |\n");
        out.append("| --- | ---: | ---: |\n");
        row(out, "Supported / yes", metrics64.supportedLabelVerdicts(), metrics64.plannedRows(),
                metrics256.supportedLabelVerdicts(), metrics256.plannedRows());
        row(out, "Unsupported / no", metrics64.unsupportedLabelVerdicts(), metrics64.plannedRows(),
                metrics256.unsupportedLabelVerdicts(), metrics256.plannedRows());

        out.append("\n## Repetition consistency\n\n");
        out.append("| Fixture-pair state | 64 tokens | 256 tokens |\n");
        out.append("| --- | ---: | ---: |\n");
        repetitionRow(out, "Consistent normalized verdict", metrics64.repetitions().consistent(),
                metrics64, metrics256.repetitions().consistent(), metrics256);
        repetitionRow(out, "Disagreeing normalized verdict", metrics64.repetitions().disagreements(),
                metrics64, metrics256.repetitions().disagreements(), metrics256);
        repetitionRow(out, "Incomplete comparison", metrics64.repetitions().incomplete(),
                metrics64, metrics256.repetitions().incomplete(), metrics256);

        out.append("\n## Output-limit finish-state proxy\n\n");
        out.append("Completion-token counts are a proxy for the configured output limit; the saved "
                + "contract does not record a provider finish reason.\n\n");
        out.append("| Completion-token relation | 64 tokens | 256 tokens |\n");
        out.append("| --- | ---: | ---: |\n");
        outputLimitRow(out, "At configured maximum", metrics64.outputLimit().atConfiguredLimit(), metrics64,
                metrics256.outputLimit().atConfiguredLimit(), metrics256);
        outputLimitRow(out, "Below configured maximum", metrics64.outputLimit().belowConfiguredLimit(), metrics64,
                metrics256.outputLimit().belowConfiguredLimit(), metrics256);
        outputLimitRow(out, "Above configured maximum", metrics64.outputLimit().aboveConfiguredLimit(), metrics64,
                metrics256.outputLimit().aboveConfiguredLimit(), metrics256);
        outputLimitRow(out, "Completion tokens unavailable", metrics64.outputLimit().unavailable(), metrics64,
                metrics256.outputLimit().unavailable(), metrics256);

        out.append("\n## Completion-token distribution\n\n");
        out.append("| 64 tokens | 256 tokens |\n");
        out.append("| --- | --- |\n");
        out.append("| ").append(distribution(metrics64.completionTokens())).append(" | ")
                .append(distribution(metrics256.completionTokens())).append(" |\n");

        out.append("\n## Per-row latency\n\n");
        out.append("| 64 tokens | 256 tokens |\n");
        out.append("| --- | --- |\n");
        out.append("| ").append(metrics64.latency().display()).append(" | ")
                .append(metrics256.latency().display()).append(" |\n\n");
        out.append("Latency includes every classified attempt and describes these deployed arms only. "
                + "Agreement is reported only among rows with a valid normalized verdict; invalid rows "
                + "are not included in that denominator.\n");
        return out.toString();
    }

    private static void row(
            StringBuilder out,
            String label,
            int budget64Value,
            int budget64Total,
            int budget256Value,
            int budget256Total
    ) {
        out.append("| ").append(label).append(" | ")
                .append(renderYield(budget64Value, budget64Total)).append(" | ")
                .append(renderYield(budget256Value, budget256Total)).append(" |\n");
    }

    private static void agreementRow(
            StringBuilder out,
            LocalEvaluationBudgetComparison.ArmMetrics metrics64,
            LocalEvaluationBudgetComparison.ArmMetrics metrics256
    ) {
        out.append("| Agreement among valid verdicts | ")
                .append(agreement(metrics64)).append(" | ")
                .append(agreement(metrics256)).append(" |\n");
    }

    private static void repetitionRow(
            StringBuilder out,
            String label,
            int budget64Value,
            LocalEvaluationBudgetComparison.ArmMetrics metrics64,
            int budget256Value,
            LocalEvaluationBudgetComparison.ArmMetrics metrics256
    ) {
        out.append("| ").append(label).append(" | ")
                .append(renderYield(budget64Value, repetitionTotal(metrics64))).append(" | ")
                .append(renderYield(budget256Value, repetitionTotal(metrics256))).append(" |\n");
    }

    private static void outputLimitRow(
            StringBuilder out,
            String label,
            int budget64Value,
            LocalEvaluationBudgetComparison.ArmMetrics metrics64,
            int budget256Value,
            LocalEvaluationBudgetComparison.ArmMetrics metrics256
    ) {
        row(out, label, budget64Value, metrics64.plannedRows(), budget256Value, metrics256.plannedRows());
    }

    private static String agreement(LocalEvaluationBudgetComparison.ArmMetrics metrics) {
        if (metrics.validVerdicts() == 0) {
            return "n/a (0 valid verdicts)";
        }
        return renderYield(metrics.agreementMatches(), metrics.validVerdicts())
                + "; mismatch " + metrics.agreementMismatches();
    }

    private static int repetitionTotal(LocalEvaluationBudgetComparison.ArmMetrics metrics) {
        return metrics.repetitions().consistent()
                + metrics.repetitions().disagreements()
                + metrics.repetitions().incomplete();
    }

    private static String distribution(LocalEvaluationBudgetComparison.CompletionTokenMetrics metrics) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : metrics.distribution().entrySet()) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(entry.getKey()).append("×").append(entry.getValue());
        }
        if (metrics.unavailable() > 0) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append("unavailable×").append(metrics.unavailable());
        }
        return out.isEmpty() ? "unavailable" : out.toString();
    }

    private static String renderYield(int value, int total) {
        if (total == 0) {
            return "n/a";
        }
        return value + "/" + total + " (" + String.format(java.util.Locale.ROOT, "%.1f", value * 100.0 / total)
                + "%)";
    }
}
