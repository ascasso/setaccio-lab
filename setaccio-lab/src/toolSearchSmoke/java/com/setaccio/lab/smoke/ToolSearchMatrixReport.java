package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import java.io.InputStream;

final class ToolSearchMatrixReport {

    private static final String BASELINE_RESOURCE = "/baselines/2026-07-12-tool-search-matrix.json";

    private final JsonNode july12;

    ToolSearchMatrixReport(ObjectMapper objectMapper) {
        try (InputStream input = ToolSearchMatrixReport.class.getResourceAsStream(BASELINE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing July 12 baseline resource");
            }
            july12 = objectMapper.readTree(input);
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Failed to load July 12 baseline resource", e);
        }
    }

    String render(
            ToolSearchMatrixAnalyzer.MatrixAnalysis analysis,
            String rawFile,
            String rawSha256) {
        StringBuilder out = new StringBuilder();
        out.append("# Post-Fix Tool Search Diagnostic Matrix Baseline\n\n");
        out.append("> **Confounder:** Pass-rate deltas are observational. This run constructs requests from canonical Java cases, while the July 12 raw request used an incorrect deterministic-failure marker. Issue #20 also changes object-response discovery normalization. Deltas cannot be attributed solely to model behavior or the fixes. Issue #21 affects chat result correctness and is not a direct tool-matrix scoring change.\n\n");
        out.append("- Raw trace: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Protocol: 3 models × 5 canonical cases × 2 repetitions × 2 advisors = 60 rows; temperature 0.0; seeds 42/43; alternate paired order.\n\n");
        out.append("## Pass-rate comparison\n\n");
        out.append("| Model | Advisor | July 12 recorded | July 12 corrected | Post-fix | Delta vs corrected |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (JsonNode baseline : july12.get("results")) {
            String model = baseline.get("model").asText();
            AdvisorMode mode = AdvisorMode.fromJson(baseline.get("advisor").asText());
            ToolSearchMatrixAnalyzer.GroupResult group = analysis.groups()
                    .get(new ToolSearchMatrixAnalyzer.GroupKey(model, mode));
            int postFix = group == null ? 0 : group.passed();
            int corrected = baseline.get("correctedPassed").asInt();
            int total = baseline.get("total").asInt();
            out.append("| `").append(model).append("` | ").append(mode.jsonValue()).append(" | ")
                    .append(baseline.get("recordedPassed").asInt()).append('/').append(total).append(" | ")
                    .append(corrected).append('/').append(total).append(" | ")
                    .append(postFix).append('/').append(total).append(" | ")
                    .append(String.format("%+d", postFix - corrected)).append(" |\n");
        }
        out.append("\n## Failure classification\n\n");
        out.append("| Model | Advisor");
        for (ToolSearchMatrixAnalyzer.FailureCategory category : ToolSearchMatrixAnalyzer.FailureCategory.values()) {
            out.append(" | ").append(category.label());
        }
        out.append(" |\n| --- | ---");
        for (int i = 0; i < ToolSearchMatrixAnalyzer.FailureCategory.values().length; i++) {
            out.append(" | ---:");
        }
        out.append(" |\n");
        for (String model : ToolSearchMatrixProtocol.MODELS) {
            for (AdvisorMode mode : java.util.List.of(AdvisorMode.STANDARD, AdvisorMode.TOOL_SEARCH)) {
                ToolSearchMatrixAnalyzer.GroupResult group = analysis.groups()
                        .get(new ToolSearchMatrixAnalyzer.GroupKey(model, mode));
                out.append("| `").append(model).append("` | ").append(mode.jsonValue());
                for (ToolSearchMatrixAnalyzer.FailureCategory category
                        : ToolSearchMatrixAnalyzer.FailureCategory.values()) {
                    out.append(" | ").append(group == null ? 0 : group.failures().getOrDefault(category, 0));
                }
                out.append(" |\n");
            }
        }
        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }
}
