package com.setaccio.lab.vision;

import com.setaccio.lab.model.VisionErrorCategory;
import java.util.Locale;

final class VisionMatrixReport {

    String render(
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis,
            String rawFile,
            String rawSha256) {
        StringBuilder out = new StringBuilder();
        VisionMatrixRunSettings settings = result.runSettings();
        out.append("# Sequential Vision Matrix\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Protocol: ").append(settings.models().size()).append(" model(s) × ")
                .append(result.inputs().size()).append(" case(s) × ")
                .append(settings.repetitions()).append(" repetitions = ")
                .append(result.rows().size()).append(" sequential rows.\n");
        out.append("- Temperature: ").append(settings.temperature()).append("\n");
        out.append("- Effective seeds: ").append(settings.seedFor(1)).append(", ")
                .append(settings.seedFor(2)).append("\n");
        out.append("- Token policy: ")
                .append(settings.maxTokens() == null ? "no explicit limit" : settings.maxTokens())
                .append("\n");
        out.append("- Prompt: `").append(result.promptId()).append("` version `")
                .append(result.promptVersion()).append("` (`")
                .append(result.promptSha256()).append("`)\n");
        out.append("- Pull strategy: `").append(result.pullModelStrategy()).append("`\n\n");

        renderDeterministicOutcomes(out, result, analysis);
        renderHumanReviewBoundary(out);
        renderRepetitionConsistency(out, result, analysis);
        renderTokenAvailability(out, result, analysis);
        renderLatency(out, result, analysis);
        renderFailures(out, result, analysis);
        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }

    private void renderDeterministicOutcomes(
            StringBuilder out,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        out.append("## Invocation and structural outcomes\n\n");
        out.append("| Model | Case | Invocation success | Structural completion |\n");
        out.append("| --- | --- | ---: | ---: |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.invocationSuccesses()).append('/').append(VisionMatrixProtocol.REPETITIONS)
                .append(" | ")
                .append(group.structuralCompletions()).append('/').append(VisionMatrixProtocol.REPETITIONS)
                .append(" |\n"));
    }

    private void renderHumanReviewBoundary(StringBuilder out) {
        out.append("\n## Expected-observation review\n\n");
        out.append("Not performed. Expected concepts remain in the ignored local `cases.json` and are not copied into saved evidence. Human review is required before assigning semantic outcomes.\n\n");
        out.append("## Unsupported-detail review\n\n");
        out.append("Not performed. Unsupported-detail and hallucination judgments remain a separate human-review step; structural completion is not evidence of image understanding.\n");
    }

    private void renderRepetitionConsistency(
            StringBuilder out,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        out.append("\n## Repetition consistency\n\n");
        out.append("| Model | Case | Review readiness | Structural agreement | Exact output match |\n");
        out.append("| --- | --- | --- | --- | --- |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.repetitionStatus()).append(" | ")
                .append(yesNo(group.structuralAgreement())).append(" | ")
                .append(yesNo(group.exactOutputMatch())).append(" |\n"));
        out.append("\nExact output matching is a reproducibility diagnostic, not a semantic consistency score.\n");
    }

    private void renderTokenAvailability(
            StringBuilder out,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        out.append("\n## Token availability\n\n");
        out.append("| Model | Case | Input tokens available | Input total | Output tokens available | Output total |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.tokensInAvailable()).append('/').append(VisionMatrixProtocol.REPETITIONS)
                .append(" | ").append(tokenTotal(group.tokensInAvailable(), group.tokensInTotal()))
                .append(" | ").append(group.tokensOutAvailable()).append('/')
                .append(VisionMatrixProtocol.REPETITIONS)
                .append(" | ").append(tokenTotal(group.tokensOutAvailable(), group.tokensOutTotal()))
                .append(" |\n"));
    }

    private void renderLatency(
            StringBuilder out,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        out.append("\n## Latency\n\n");
        out.append("| Model | Case | Successful samples | Median ms | Observed range ms |\n");
        out.append("| --- | --- | ---: | ---: | ---: |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.latencySamples()).append(" | ")
                .append(latencyMedian(group)).append(" | ")
                .append(latencyRange(group))
                .append(" |\n"));
        out.append("\nLatency includes successful invocations only. With two repetitions, the report uses the median and observed range; it does not calculate percentiles.\n");
    }

    private void renderFailures(
            StringBuilder out,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        out.append("\n## Infrastructure failures\n\n");
        out.append("| Model | Case | Model unavailable | Provider failure |\n");
        out.append("| --- | --- | ---: | ---: |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.failures().getOrDefault(VisionErrorCategory.MODEL_UNAVAILABLE, 0))
                .append(" | ")
                .append(group.failures().getOrDefault(VisionErrorCategory.PROVIDER_FAILURE, 0))
                .append(" |\n"));

        out.append("\n## Other invocation failures\n\n");
        out.append("| Model | Case | Empty response | Invalid input |\n");
        out.append("| --- | --- | ---: | ---: |\n");
        forEachGroup(result, analysis, (model, input, group) -> out
                .append("| `").append(model).append("` | `").append(input.caseId()).append("` | ")
                .append(group.failures().getOrDefault(VisionErrorCategory.EMPTY_RESPONSE, 0))
                .append(" | ")
                .append(group.failures().getOrDefault(VisionErrorCategory.INVALID_INPUT, 0))
                .append(" |\n"));
    }

    private void forEachGroup(
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis,
            GroupConsumer consumer) {
        for (String model : result.runSettings().models()) {
            for (VisionMatrixInput input : result.inputs()) {
                VisionMatrixAnalyzer.GroupAnalysis group = analysis.groups()
                        .get(new VisionMatrixAnalyzer.GroupKey(model, input.caseId()));
                if (group != null) {
                    consumer.accept(model, input, group);
                }
            }
        }
    }

    private static String tokenTotal(int available, long total) {
        return available == 0 ? "n/a" : Long.toString(total);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String latencyMedian(VisionMatrixAnalyzer.GroupAnalysis group) {
        return group.latencySamples() == 0
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f", group.medianLatencyMs());
    }

    private static String latencyRange(VisionMatrixAnalyzer.GroupAnalysis group) {
        return group.latencySamples() == 0
                ? "n/a"
                : group.minimumLatencyMs() + "–" + group.maximumLatencyMs();
    }

    @FunctionalInterface
    private interface GroupConsumer {

        void accept(
                String model,
                VisionMatrixInput input,
                VisionMatrixAnalyzer.GroupAnalysis group);
    }
}
