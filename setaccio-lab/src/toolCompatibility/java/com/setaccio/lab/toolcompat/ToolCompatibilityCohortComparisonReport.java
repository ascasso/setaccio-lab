package com.setaccio.lab.toolcompat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders the bounded, descriptive T3.5 peer/reference report. */
final class ToolCompatibilityCohortComparisonReport {

    String render(ToolCompatibilityCohortComparison.ComparisonData data) {
        if (data == null) {
            throw new IllegalArgumentException("cohort comparison data is required");
        }
        ToolCompatibilityCohortModelIdentity reference = data.reference();
        StringBuilder out = new StringBuilder(
                "# Offline Tool Compatibility Reference Comparison\n\n");
        out.append("## Verified protocol\n\n");
        out.append("- Run: `").append(data.runId()).append("`\n");
        out.append("- Git commit: `")
                .append(data.codeBaseline().gitCommit())
                .append("`\n");
        out.append("- Working tree dirty at execution: `")
                .append(data.codeBaseline().workingTreeDirty())
                .append("`\n");
        out.append("- Ollama runtime version: `")
                .append(data.ollamaRuntimeVersion())
                .append("`\n");
        out.append("- Locked rows per model: `")
                .append(data.orderedCaseIds().size())
                .append("` cases × `")
                .append(data.runSettings().repetitions())
                .append("` repetitions = `")
                .append(Math.multiplyExact(
                        data.orderedCaseIds().size(), data.runSettings().repetitions()))
                .append("`\n");
        out.append("- Peer models: `").append(data.peers().size()).append("`\n");
        appendIdentity(out, "Reference", reference);

        out.append("\nThis report compares each peer only with the separately labelled reference. ")
                .append("A reference pass is not semantic ground truth. Latency and token ")
                .append("differences describe the exact deployed tags, digests, artifact/runtime ")
                .append("formats, and Ollama version above; they are not backend-normalized or ")
                .append("attributed solely to model weights.\n");

        for (int index = 0; index < data.peers().size(); index++) {
            appendPeer(out, index + 1, data.peers().get(index), reference);
        }

        out.append("\n## Interpretation boundary\n\n")
                .append("This deterministic report produces no aggregate score, winner, model ")
                .append("selection, general capability claim, semantic-correctness inference, or ")
                .append("production recommendation. Two repetitions remain descriptive evidence ")
                .append("under this one locked protocol.\n");
        return out.toString();
    }

    private static void appendPeer(
            StringBuilder out,
            int position,
            ToolCompatibilityCohortComparison.PeerComparison comparison,
            ToolCompatibilityCohortModelIdentity reference
    ) {
        ToolCompatibilityCohortModelIdentity peer = comparison.peer();
        out.append("\n## ").append(position).append(". Peer `")
                .append(peer.effectiveInstalledTag()).append("`\n\n");
        appendIdentity(out, "Peer", peer);
        out.append("- Compared reference tag: `")
                .append(reference.effectiveInstalledTag()).append("`\n");
        out.append("- Compared reference artifact/runtime format: `")
                .append(metadata(reference.metadata().artifactRuntimeFormat()))
                .append("`\n");

        out.append("\n### Locked case/repetition overlap\n\n");
        appendOutcome(out, "Passed by both", comparison,
                ToolCompatibilityCohortComparison.Outcome.BOTH_PASS);
        appendOutcome(out, "Passed only by the reference", comparison,
                ToolCompatibilityCohortComparison.Outcome.REFERENCE_ONLY);
        appendOutcome(out, "Passed only by the peer", comparison,
                ToolCompatibilityCohortComparison.Outcome.PEER_ONLY);
        appendOutcome(out, "Passed by neither", comparison,
                ToolCompatibilityCohortComparison.Outcome.NEITHER);

        out.append("\n### Compatibility failures unique to one side\n\n");
        appendDiagnostics(
                out,
                "Peer-only failure diagnostics where the reference passed",
                comparison.rows().stream()
                        .filter(row -> row.outcome()
                                == ToolCompatibilityCohortComparison.Outcome.REFERENCE_ONLY)
                        .map(ToolCompatibilityCohortComparison.RowComparison::peerDiagnostic)
                        .toList());
        appendDiagnostics(
                out,
                "Reference-only failure diagnostics where the peer passed",
                comparison.rows().stream()
                        .filter(row -> row.outcome()
                                == ToolCompatibilityCohortComparison.Outcome.PEER_ONLY)
                        .map(ToolCompatibilityCohortComparison.RowComparison::referenceDiagnostic)
                        .toList());

        out.append("\n### Paired descriptive observations\n\n");
        out.append("Reference-minus-peer deltas are shown only as arithmetic observations. ")
                .append("Latency includes failed or incomplete rows when their paired outcome says ")
                .append("so; `unavailable` token deltas are not imputed.\n\n");
        out.append("| Case | Rep | Outcome | Peer diagnostic | Reference diagnostic | ")
                .append("Output limit (peer / reference) | Peer latency | Reference latency | ")
                .append("Latency delta | Peer total tokens | Reference total tokens | Token delta |\n");
        out.append("| --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | ---: |\n");
        for (ToolCompatibilityCohortComparison.RowComparison row : comparison.rows()) {
            out.append("| `").append(row.caseId()).append("` | ")
                    .append(row.repetition()).append(" | ")
                    .append(outcome(row.outcome())).append(" | `")
                    .append(row.peerDiagnostic()).append("` | `")
                    .append(row.referenceDiagnostic()).append("` | ")
                    .append(outputLimit(row.peerOutputLimit()))
                    .append(" / ")
                    .append(outputLimit(row.referenceOutputLimit()))
                    .append(" | ")
                    .append(row.peerLatencyMillis()).append(" ms | ")
                    .append(row.referenceLatencyMillis()).append(" ms | ")
                    .append(signed(row.latencyDeltaMillis())).append(" ms | ")
                    .append(tokens(row.peerTokens())).append(" | ")
                    .append(tokens(row.referenceTokens())).append(" | ")
                    .append(tokenDelta(row.totalTokenDelta())).append(" |\n");
        }
    }

    private static void appendIdentity(
            StringBuilder out,
            String label,
            ToolCompatibilityCohortModelIdentity identity
    ) {
        out.append("- ").append(label).append(" tag: `")
                .append(identity.effectiveInstalledTag()).append("`\n");
        out.append("- ").append(label).append(" digest: `")
                .append(identity.digest()).append("`\n");
        out.append("- ").append(label).append(" artifact/runtime format: `")
                .append(metadata(identity.metadata().artifactRuntimeFormat())).append("`\n");
        out.append("- ").append(label).append(" quantization/precision: `")
                .append(metadata(identity.metadata().quantizationOrPrecision())).append("`\n");
    }

    private static void appendOutcome(
            StringBuilder out,
            String label,
            ToolCompatibilityCohortComparison.PeerComparison comparison,
            ToolCompatibilityCohortComparison.Outcome outcome
    ) {
        List<String> rows = comparison.rows().stream()
                .filter(row -> row.outcome() == outcome)
                .map(ToolCompatibilityCohortComparisonReport::rowLabel)
                .toList();
        out.append("- ").append(label).append(": `")
                .append(comparison.count(outcome)).append("` — ")
                .append(rows.isEmpty() ? "none" : String.join(", ", rows))
                .append("\n");
    }

    private static void appendDiagnostics(
            StringBuilder out,
            String label,
            List<String> diagnostics
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        diagnostics.forEach(diagnostic -> counts.merge(diagnostic, 1, Integer::sum));
        out.append("- ").append(label).append(": ");
        if (counts.isEmpty()) {
            out.append("none\n");
            return;
        }
        out.append(counts.entrySet().stream()
                        .map(entry -> "`" + entry.getKey() + "` × " + entry.getValue())
                        .reduce((left, right) -> left + ", " + right)
                        .orElseThrow())
                .append("\n");
    }

    private static String rowLabel(ToolCompatibilityCohortComparison.RowComparison row) {
        return "`" + row.caseId() + "` r" + row.repetition();
    }

    private static String outcome(ToolCompatibilityCohortComparison.Outcome outcome) {
        return switch (outcome) {
            case BOTH_PASS -> "both pass";
            case REFERENCE_ONLY -> "reference only";
            case PEER_ONLY -> "peer only";
            case NEITHER -> "neither";
        };
    }

    private static String tokens(ToolCompatibilityCohortComparison.TokenObservation tokens) {
        String availability = tokens.availability().name().toLowerCase(Locale.ROOT);
        return tokens.totalTokens() == null
                ? "unavailable (`" + availability + "`)"
                : "`" + tokens.totalTokens() + "` (`" + availability + "`)";
    }

    private static String tokenDelta(Integer delta) {
        return delta == null ? "unavailable" : signed(delta);
    }

    private static String outputLimit(ToolCompatibilityOutputLimitState state) {
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String signed(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }

    private static String metadata(ToolCompatibilityMetadataField field) {
        return field.availability() == ToolCompatibilityMetadataField.Availability.AVAILABLE
                ? field.value()
                : "unavailable";
    }
}
