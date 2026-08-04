package com.setaccio.lab.evaluation;

import java.util.Locale;

final class LocalEvaluationReport {

    String render(
            LocalEvaluationResult result,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis,
            String rawFile,
            String rawSha256
    ) {
        StringBuilder out = new StringBuilder();
        LocalEvaluationRunSettings settings = result.runSettings();
        LocalEvaluationModelIdentity model = result.judgeModelIdentity();

        out.append("# Local Fact-Check Evaluation\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Protocol version: `").append(result.protocolVersion()).append("`\n");
        out.append("- Protocol: 6 fixtures × 2 repetitions = 12 sequential rows.\n");
        out.append("- Judge: requested `").append(model.requestedModel())
                .append("`, normalized installed name `").append(model.normalizedInstalledName())
                .append("`, Ollama digest `").append(model.digest()).append("`\n");
        out.append("- Endpoint category: `").append(result.endpointCategory()).append("`\n");
        out.append("- Temperature: ").append(settings.temperature()).append("\n");
        out.append("- Seeds: ").append(settings.seeds().getFirst()).append(", ")
                .append(settings.seeds().getLast()).append("\n");
        out.append("- Maximum output tokens: ").append(settings.maxTokens()).append("\n");
        out.append("- Timeout: ").append(settings.timeoutMillis()).append(" ms\n");
        out.append("- Attempt policy: exactly ").append(settings.maxAttempts()).append(" per row\n");
        out.append("- Pull strategy: `").append(result.pullModelStrategy()).append("`\n");
        out.append("- Prompt: `").append(result.promptId()).append("` version `")
                .append(result.promptVersion()).append("` (`")
                .append(result.promptSha256()).append("`)\n");
        out.append("- Fixture catalog: `").append(result.fixtureCatalogId()).append("` version `")
                .append(result.fixtureCatalogVersion()).append("` (`")
                .append(result.fixtureCatalogSha256()).append("`)\n");
        out.append("- Human confirmation: `").append(result.fixtureReviewId()).append("` version `")
                .append(result.fixtureReviewVersion()).append("` (`")
                .append(result.fixtureReviewSha256()).append("`)\n");

        renderAgreement(out, analysis);
        renderRepetition(out, analysis);
        renderVerdictTendency(out, analysis);
        renderFormatOutcomes(out, analysis);
        renderUsage(out, analysis);
        renderLatencyAndAttempts(out, analysis);
        renderInfrastructureFailures(out, analysis);

        out.append("\n## Protocol interpretation boundary\n\n");
        out.append("Supported/unsupported ordering is reversed between repetitions while the seed and ")
                .append("repetition also change. This report does not isolate or claim an order effect.\n");
        out.append("A normalized judge verdict, Spring evaluator boolean, and agreement with the ")
                .append("human-confirmed expected verdict remain separate signals.\n");

        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }

    private static void renderAgreement(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Expected-verdict agreement\n\n");
        out.append("| Human-confirmed expectation | Planned rows | Invocation success | Valid verdict ")
                .append("| Agreement | Mismatch |\n");
        out.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        agreementRow(out, "supported", analysis.supported());
        agreementRow(out, "unsupported", analysis.unsupported());
    }

    private static void agreementRow(
            StringBuilder out,
            String label,
            LocalEvaluationAnalyzer.ExpectedVerdictAnalysis metrics
    ) {
        out.append("| ").append(label).append(" | ").append(metrics.planned())
                .append(" | ").append(metrics.invocationSuccesses())
                .append(" | ").append(metrics.normalizedVerdicts())
                .append(" | ").append(metrics.expectationMatches())
                .append(" | ").append(metrics.expectationMismatches()).append(" |\n");
    }

    private static void renderRepetition(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Repetition consistency\n\n");
        out.append("- Consistent normalized verdict: ").append(analysis.repetitionConsistent()).append(" fixture(s)\n");
        out.append("- Repetition disagreement: ").append(analysis.repetitionDisagreements()).append(" fixture(s)\n");
        out.append("- Incomplete comparison: ").append(analysis.repetitionIncomplete()).append(" fixture(s)\n");
    }

    private static void renderVerdictTendency(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Verdict tendency\n\n");
        out.append("- Normalized supported/yes verdicts: ").append(analysis.supportedVerdicts()).append("\n");
        out.append("- Normalized unsupported/no verdicts: ").append(analysis.unsupportedVerdicts()).append("\n");
        out.append("- Always-yes tendency: ").append(yesNo(analysis.alwaysYes())).append("\n");
        out.append("- Always-no tendency: ").append(yesNo(analysis.alwaysNo())).append("\n");
    }

    private static void renderFormatOutcomes(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Verdict-format outcomes\n\n");
        out.append("- Empty response: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE)).append("\n");
        out.append("- Malformed verdict: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT)).append("\n");
        out.append("- Expectation mismatch: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH)).append("\n");
    }

    private static void renderUsage(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Token availability\n\n");
        out.append("- Prompt tokens available: ").append(analysis.promptUsageAvailable())
                .append('/').append(LocalEvaluationProtocol.ROW_COUNT).append(" rows\n");
        out.append("- Completion tokens available: ").append(analysis.completionUsageAvailable())
                .append('/').append(LocalEvaluationProtocol.ROW_COUNT).append(" rows\n");
        out.append("- Total tokens available: ").append(analysis.totalUsageAvailable())
                .append('/').append(LocalEvaluationProtocol.ROW_COUNT).append(" rows\n");
    }

    private static void renderLatencyAndAttempts(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Latency and attempts\n\n");
        out.append("- Attempt samples: ").append(analysis.latencySamples()).append("\n");
        out.append("- Median latency: ").append(analysis.latencySamples() == 0
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f ms", analysis.medianLatencyMillis())).append("\n");
        out.append("- Observed latency range: ").append(analysis.latencySamples() == 0
                ? "n/a"
                : analysis.minimumLatencyMillis() + "–" + analysis.maximumLatencyMillis() + " ms").append("\n");
        out.append("- Total attempts: ").append(analysis.totalAttempts()).append("\n");
        out.append("Latency includes every classified judge attempt, including infrastructure failures.\n");
    }

    private static void renderInfrastructureFailures(
            StringBuilder out,
            LocalEvaluationAnalyzer.MatrixAnalysis analysis
    ) {
        out.append("\n## Infrastructure failures\n\n");
        out.append("- Judge model unavailable: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE)).append("\n");
        out.append("- Timeout: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.TIMEOUT)).append("\n");
        out.append("- Provider failure: ")
                .append(count(analysis, LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE)).append("\n");
    }

    private static int count(
            LocalEvaluationAnalyzer.MatrixAnalysis analysis,
            LocalFactCheckDiagnosticCategory category
    ) {
        return analysis.diagnostics().getOrDefault(category, 0);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
