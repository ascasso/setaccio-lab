package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationOption;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Renders an offline architecture-portability report without inspecting raw response text. */
final class ChatPortabilityReport {

    String render(ChatPortabilitySnapshot baseline, ChatPortabilitySnapshot candidate) {
        Comparison comparison = compare(baseline, candidate);
        StringBuilder report = new StringBuilder("# Chat Provider Portability Report\n\n");
        report.append("## Scope\n\n")
                .append("This is an offline evidence and architecture report. It reports each provider's ")
                .append("recorded invocation outcomes without ranking providers or comparing answer quality.\n\n");

        report.append("## Identity and Protocol\n\n");
        appendIdentity(report, "Baseline", baseline);
        appendIdentity(report, "Candidate", candidate);
        line(report, "Prompt inputs match", Boolean.toString(comparison.promptInputsMatch()));
        line(report, "Common invocation settings match", Boolean.toString(comparison.commonSettingsMatch()));
        line(report, "Framework versions match", Boolean.toString(comparison.frameworkVersionsMatch()));
        line(report, "Architecture portability contract", comparison.architectureCompatible() ? "compatible" : "not compatible");
        line(report, "Semantic/performance comparison", "not performed");
        if (!comparison.reasons().isEmpty()) {
            report.append("\n### Comparability limitations\n\n");
            for (String reason : comparison.reasons()) {
                report.append("- ").append(reason).append("\n");
            }
        }

        report.append("\n## Recorded Outcomes\n\n")
                .append("| Provider | Completed | Non-empty structural outputs | Empty responses | Rows with usage | Successful latency range |\n")
                .append("| --- | ---: | ---: | ---: | ---: | --- |\n");
        appendMetrics(report, baseline);
        appendMetrics(report, candidate);

        report.append("\n## Pre-Run Cost Estimates\n\n")
                .append("| Provider | Input ceiling | Output ceiling | Estimated USD | Price source checked |\n")
                .append("| --- | ---: | ---: | ---: | --- |\n");
        appendCost(report, baseline);
        appendCost(report, candidate);

        report.append("\n## Interpretation Boundary\n\n")
                .append("Token usage and estimated cost are reported separately from observed behavior. ")
                .append("Hosted provider identity is limited to the requested/effective provider model IDs; ")
                .append("it is not equivalent to the Ollama model's local immutable digest. ")
                .append("No semantic or performance comparison is generated, and such a comparison is refused ")
                .append("when prompt inputs, common settings, or framework versions differ.\n");
        return report.toString();
    }

    Comparison compare(ChatPortabilitySnapshot baseline, ChatPortabilitySnapshot candidate) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        List<String> reasons = new ArrayList<>();
        boolean promptInputsMatch = samePromptInputs(baseline.settings(), candidate.settings());
        if (!promptInputsMatch) {
            reasons.add("Prompt catalog identity or ordered prompt digests differ; semantic and performance comparison is refused.");
        }
        boolean commonSettingsMatch = sameCommonSettings(baseline.settings(), candidate.settings());
        if (!commonSettingsMatch) {
            reasons.add("Temperature, output-token cap, timeout, call count, repetitions, or attempt policy differ; semantic and performance comparison is refused.");
        }
        boolean frameworkVersionsMatch = baseline.frameworkVersions().equals(candidate.frameworkVersions());
        if (!frameworkVersionsMatch) {
            reasons.add("Spring Boot or Spring AI versions differ; semantic and performance comparison is refused.");
        }
        boolean seededRepetitionsMatch = baseline.settings().seeds().equals(candidate.settings().seeds());
        if (!seededRepetitionsMatch) {
            reasons.add("Repetition seed semantics differ because the candidate records seed as unsupported; repetitions are not protocol-identical.");
        }
        if (!baseline.requestedModelIdentity().providerId().equals(candidate.requestedModelIdentity().providerId())) {
            reasons.add("Provider-specific model identity is intentionally not treated as equivalent: local digest and hosted model IDs have different reproducibility guarantees.");
        }
        boolean architectureCompatible = promptInputsMatch && commonSettingsMatch && frameworkVersionsMatch;
        return new Comparison(
                promptInputsMatch,
                commonSettingsMatch,
                frameworkVersionsMatch,
                architectureCompatible,
                List.copyOf(reasons));
    }

    private static boolean samePromptInputs(ChatPortabilityRunSettings left, ChatPortabilityRunSettings right) {
        return left.promptCatalogId().equals(right.promptCatalogId())
                && left.promptCatalogVersion().equals(right.promptCatalogVersion())
                && left.promptCatalogSha256().equals(right.promptCatalogSha256())
                && left.orderedPromptIdentities().equals(right.orderedPromptIdentities());
    }

    private static boolean sameCommonSettings(ChatPortabilityRunSettings left, ChatPortabilityRunSettings right) {
        return left.repetitions() == right.repetitions()
                && left.plannedCallCount() == right.plannedCallCount()
                && Double.compare(left.temperature(), right.temperature()) == 0
                && left.maxOutputTokens() == right.maxOutputTokens()
                && left.timeoutMillis() == right.timeoutMillis()
                && left.maxAttempts() == right.maxAttempts();
    }

    private static void appendIdentity(StringBuilder report, String label, ChatPortabilitySnapshot snapshot) {
        String prefix = label + " ";
        line(report, prefix + "provider", snapshot.requestedModelIdentity().providerId());
        line(report, prefix + "requested model", snapshot.requestedModelIdentity().requestedModel());
        line(report, prefix + "effective model", snapshot.requestedModelIdentity().effectiveModel());
        line(report, prefix + "identifier semantics", snapshot.requestedModelIdentity().identifierKind().name());
        line(report, prefix + "local digest", snapshot.requestedModelIdentity().localDigest() == null
                ? "not applicable to hosted model" : snapshot.requestedModelIdentity().localDigest());
        line(report, prefix + "temperature", Double.toString(snapshot.settings().temperature()));
        line(report, prefix + "max output tokens", Integer.toString(snapshot.settings().maxOutputTokens()));
        line(report, prefix + "timeout", Long.toString(snapshot.settings().timeoutMillis()) + " ms");
        line(report, prefix + "attempts", Integer.toString(snapshot.settings().maxAttempts()));
        line(report, prefix + "option handling", snapshot.settings().optionSupport().statuses().toString());
        line(report, prefix + "seeds", snapshot.settings().seeds().isEmpty()
                ? "unsupported: " + snapshot.settings().optionSupport().unsupportedReasons().get(ChatGenerationOption.SEED)
                : snapshot.settings().seeds().toString());
    }

    private static void appendMetrics(StringBuilder report, ChatPortabilitySnapshot snapshot) {
        List<ChatPortabilityRow> rows = snapshot.rows();
        int completed = (int) rows.stream().filter(ChatPortabilityRow::invocationSucceeded).count();
        int structural = (int) rows.stream().filter(ChatPortabilityRow::structuralOutputPresent).count();
        int empty = (int) rows.stream().filter(row -> row.failureCategory() == ChatInvocationFailureCategory.EMPTY_RESPONSE).count();
        int usage = (int) rows.stream().filter(row -> row.totalTokens() != null).count();
        List<Long> latencies = rows.stream().filter(ChatPortabilityRow::structuralOutputPresent)
                .map(ChatPortabilityRow::latencyMillis).toList();
        String latency = latencies.isEmpty() ? "not available"
                : latencies.stream().mapToLong(Long::longValue).min().orElseThrow()
                + "-" + latencies.stream().mapToLong(Long::longValue).max().orElseThrow() + " ms";
        report.append("| `").append(snapshot.requestedModelIdentity().providerId()).append("` | ")
                .append(completed).append("/").append(rows.size()).append(" | ")
                .append(structural).append("/").append(rows.size()).append(" | ")
                .append(empty).append("/").append(rows.size()).append(" | ")
                .append(usage).append("/").append(rows.size()).append(" | ")
                .append(latency).append(" |\n");
    }

    private static void appendCost(StringBuilder report, ChatPortabilitySnapshot snapshot) {
        ChatEstimatedCost cost = snapshot.estimatedCost();
        report.append("| `").append(snapshot.requestedModelIdentity().providerId()).append("` | ")
                .append(cost.inputTokenCeiling()).append(" | ")
                .append(cost.outputTokenCeiling()).append(" | $")
                .append(cost.estimatedUsd().setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()).append(" | ")
                .append(cost.priceCheckedAt()).append(" |\n");
    }

    private static void line(StringBuilder report, String label, String value) {
        report.append("- ").append(label).append(": `").append(value).append("`\n");
    }

    record Comparison(
            boolean promptInputsMatch,
            boolean commonSettingsMatch,
            boolean frameworkVersionsMatch,
            boolean architectureCompatible,
            List<String> reasons
    ) {}
}
