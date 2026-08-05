package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.time.Duration;

final class ChatMatrixReport {

    String render(
            ChatMatrixResult result,
            ChatMatrixAnalyzer.MatrixAnalysis analysis,
            String rawPath,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder report = new StringBuilder();
        report.append("# Ollama Chat Matrix Summary\n\n");
        report.append("## Protocol\n\n");
        line(report, "Suite", result.suite());
        line(report, "Provider", result.provider());
        line(report, "Endpoint category", result.endpointCategory());
        line(report, "Execution", result.executionStrategy());
        line(report, "Pull strategy", result.pullModelStrategy());
        line(report, "Rows", Integer.toString(ChatMatrixProtocol.ROW_COUNT));
        line(report, "Prompt catalog", result.promptCatalogId() + " v" + result.promptCatalogVersion());
        line(report, "Prompt catalog SHA-256", result.promptCatalogSha256());
        line(report, "Requested model", result.modelIdentity().requestedModel());
        line(report, "Effective installed model", result.modelIdentity().effectiveModel());
        line(report, "Ollama model digest", result.modelIdentity().digest());
        line(report, "Temperature", Double.toString(result.runSettings().temperature()));
        line(report, "Seeds", result.runSettings().seeds().toString());
        line(report, "Max output tokens", Integer.toString(result.runSettings().maxOutputTokens()));
        line(report, "Timeout", Duration.ofMillis(result.runSettings().timeoutMillis()).toString());
        line(report, "Attempts per row", Integer.toString(result.runSettings().maxAttempts()));
        line(report, "Started", result.startedAt().toString());
        line(report, "Finished", result.finishedAt().toString());

        report.append("\n## Evidence\n\n");
        line(report, "Raw result", rawPath);
        line(report, "Raw result SHA-256", rawSha256);
        line(report, "Git commit", codeBaseline.gitCommit());
        line(report, "Working tree dirty", Boolean.toString(codeBaseline.workingTreeDirty()));
        line(report, "Evidence status", codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final"
                : "clean-baseline candidate");

        report.append("\n## Deterministic Results\n\n");
        line(report, "Completed invocations", analysis.completedInvocations() + "/" + analysis.totalRows());
        line(report, "Non-empty successful responses", analysis.successfulResponses() + "/" + analysis.totalRows());
        line(report, "Rows with complete usage", analysis.rowsWithUsage() + "/" + analysis.totalRows());
        line(report, "Successful latency range", latencyRange(analysis));
        for (ChatInvocationFailureCategory category : ChatInvocationFailureCategory.values()) {
            line(report, category.name(), Integer.toString(analysis.categories().getOrDefault(category, 0)));
        }

        report.append("\n## Per-Prompt Completion\n\n");
        report.append("| Prompt | Successful | Empty | Planned |\n");
        report.append("| --- | ---: | ---: | ---: |\n");
        for (ChatPromptIdentity identity : result.orderedPromptIdentities()) {
            ChatMatrixAnalyzer.PromptMetrics metrics = analysis.byPrompt().get(identity.id());
            report.append("| `").append(identity.id()).append("` | ")
                    .append(metrics.successfulResponses()).append(" | ")
                    .append(metrics.emptyResponses()).append(" | ")
                    .append(metrics.totalRows()).append(" |\n");
        }

        report.append("\n## Interpretation Boundary\n\n");
        report.append("This deterministic summary verifies the locked protocol, invocation completion, ")
                .append("usage availability, failure categories, and artifact integrity. It does not judge ")
                .append("semantic answer quality, rank models, or generalize beyond this one model and three ")
                .append("public-safe prompts.\n");
        return report.toString();
    }

    private static String latencyRange(ChatMatrixAnalyzer.MatrixAnalysis analysis) {
        if (analysis.minimumSuccessfulLatencyMillis() == null) {
            return "not available";
        }
        return analysis.minimumSuccessfulLatencyMillis() + "-"
                + analysis.maximumSuccessfulLatencyMillis() + " ms";
    }

    private static void line(StringBuilder report, String label, String value) {
        report.append("- ").append(label).append(": `").append(value).append("`\n");
    }
}
