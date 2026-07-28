package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceVerifier;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Compares two verified vision-matrix runs without reading a corpus or contacting a provider. */
final class VisionMatrixComparison {

    private static final Set<String> PROMPT_SETTINGS = Set.of(
            "promptId", "promptVersion", "promptSha256");

    private final ObjectMapper objectMapper;

    VisionMatrixComparison(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    ComparisonResult compare(Path baselineDirectory, Path candidateDirectory) {
        ComparableRun baseline = loadVerified(baselineDirectory, "baseline");
        ComparableRun candidate = loadVerified(candidateDirectory, "candidate");
        validateComparable(baseline, candidate);
        return new ComparisonResult(render(baseline, candidate));
    }

    private ComparableRun loadVerified(Path directory, String label) {
        Path runDirectory = directory.toAbsolutePath().normalize();
        try {
            EvidenceManifest manifest = new EvidenceManifestStore(objectMapper).read(runDirectory);
            var artifactVerification = new EvidenceVerifier().verify(runDirectory, manifest);
            if (!artifactVerification.valid()) {
                throw new IllegalArgumentException(
                        "The " + label + " run did not verify: "
                                + String.join(" ", artifactVerification.failures()));
            }
            VisionPromptDefinition prompt = VisionMatrixOfflineRunner.promptDefinitionFor(runDirectory);
            VisionMatrixEvidence.OfflineResult verification = new VisionMatrixEvidence(objectMapper, prompt)
                    .verify(runDirectory);
            if (!verification.valid()) {
                throw new IllegalArgumentException(
                        "The " + label + " run did not verify: " + String.join(" ", verification.failures()));
            }
            VisionMatrixResult result = objectMapper.readerFor(VisionMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(runDirectory.resolve(VisionMatrixProtocol.RAW_FILENAME).toFile());
            return new ComparableRun(manifest, result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load verified " + label + " run", e);
        }
    }

    private static void validateComparable(ComparableRun baseline, ComparableRun candidate) {
        List<String> failures = new ArrayList<>();
        if (!baseline.manifest().executionEngine().equals(candidate.manifest().executionEngine())) {
            failures.add("execution engine differs");
        }
        if (!baseline.manifest().frameworkVersions().equals(candidate.manifest().frameworkVersions())) {
            failures.add("Spring Boot or Spring AI framework versions differ");
        }
        if (!nonPromptSettings(baseline.manifest()).equals(nonPromptSettings(candidate.manifest()))) {
            failures.add("locked protocol settings differ outside prompt identity");
        }
        if (!baseline.result().modelIdentities().equals(candidate.result().modelIdentities())) {
            failures.add("model identities or their order differ");
        }
        if (!baseline.result().inputs().equals(candidate.result().inputs())) {
            failures.add("case IDs or BLAKE3 input identities differ");
        }
        if (!baseline.result().runSettings().equals(candidate.result().runSettings())) {
            failures.add("repetitions, seeds, temperature, token policy, or models differ");
        }
        if (!rowProtocolIdentities(baseline.result()).equals(rowProtocolIdentities(candidate.result()))) {
            failures.add("row order or invocation protocol differs");
        }
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException("Vision runs are not comparable: " + String.join("; ", failures));
        }
    }

    private static Map<String, Object> nonPromptSettings(EvidenceManifest manifest) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>(manifest.settings());
        PROMPT_SETTINGS.forEach(settings::remove);
        return Map.copyOf(settings);
    }

    private static List<RowProtocolIdentity> rowProtocolIdentities(VisionMatrixResult result) {
        return result.rows().stream()
                .map(row -> new RowProtocolIdentity(
                        row.sequence(),
                        row.model(),
                        row.caseId(),
                        row.repetition(),
                        row.invocationSettings(),
                        row.mimeType(),
                        row.inputBlake3()))
                .toList();
    }

    private static String render(ComparableRun baseline, ComparableRun candidate) {
        StringBuilder out = new StringBuilder("# Offline Vision Prompt Comparison\n\n");
        out.append("- Baseline run: `").append(baseline.manifest().runId()).append("`\n");
        out.append("- Candidate run: `").append(candidate.manifest().runId()).append("`\n");
        out.append("- Prompt: `").append(baseline.result().promptId()).append("` version `")
                .append(baseline.result().promptVersion()).append("` (`")
                .append(baseline.result().promptSha256()).append("`) → version `")
                .append(candidate.result().promptVersion()).append("` (`")
                .append(candidate.result().promptSha256()).append("`)\n");
        out.append("- Protocol: ").append(baseline.result().runSettings().models().size())
                .append(" model(s) × ").append(baseline.result().inputs().size())
                .append(" case(s) × ").append(baseline.result().runSettings().repetitions())
                .append(" repetitions.\n\n");
        out.append("This report contains deterministic evidence only. It does not score expected concepts, "
                + "hallucinations, or image quality.\n\n");

        out.append("## Invocation and structural deltas\n\n");
        out.append("| Model | Case | Invocation success | Structural completion |\n");
        out.append("| --- | --- | --- | --- |\n");
        forEachGroup(baseline.result(), candidate.result(), (model, caseId, baselineStats, candidateStats) -> out
                .append("| `").append(model).append("` | `").append(caseId).append("` | ")
                .append(delta(baselineStats.invocationSuccesses(), candidateStats.invocationSuccesses()))
                .append(" | ")
                .append(delta(baselineStats.structuralCompletions(), candidateStats.structuralCompletions()))
                .append(" |\n"));

        out.append("\n## Repetition and token deltas\n\n");
        out.append("| Model | Case | Exact output match | Input tokens | Output tokens |\n");
        out.append("| --- | --- | --- | --- | --- |\n");
        forEachGroup(baseline.result(), candidate.result(), (model, caseId, baselineStats, candidateStats) -> out
                .append("| `").append(model).append("` | `").append(caseId).append("` | ")
                .append(change(baselineStats.exactOutputMatch(), candidateStats.exactOutputMatch()))
                .append(" | ").append(tokens(baselineStats.tokensInAvailable(), baselineStats.tokensInTotal()))
                .append(" → ").append(tokens(candidateStats.tokensInAvailable(), candidateStats.tokensInTotal()))
                .append(" | ").append(tokens(baselineStats.tokensOutAvailable(), baselineStats.tokensOutTotal()))
                .append(" → ").append(tokens(candidateStats.tokensOutAvailable(), candidateStats.tokensOutTotal()))
                .append(" |\n"));

        out.append("\n## Latency and infrastructure deltas\n\n");
        out.append("| Model | Case | Successful samples | Median ms | Observed range ms | Infrastructure failures |\n");
        out.append("| --- | --- | --- | --- | --- | --- |\n");
        forEachGroup(baseline.result(), candidate.result(), (model, caseId, baselineStats, candidateStats) -> out
                .append("| `").append(model).append("` | `").append(caseId).append("` | ")
                .append(delta(baselineStats.latencySamples(), candidateStats.latencySamples()))
                .append(" | ").append(latency(baselineStats)).append(" → ").append(latency(candidateStats))
                .append(" | ").append(range(baselineStats)).append(" → ").append(range(candidateStats))
                .append(" | ").append(delta(
                        baselineStats.infrastructureFailures(), candidateStats.infrastructureFailures()))
                .append(" |\n"));

        out.append("\nLatency includes successful invocations only. Exact output matching is a reproducibility "
                + "diagnostic, not a semantic consistency score.\n");
        return out.toString();
    }

    private static void forEachGroup(
            VisionMatrixResult baseline,
            VisionMatrixResult candidate,
            GroupConsumer consumer) {
        Map<GroupKey, GroupStats> baselineGroups = groupStats(baseline);
        Map<GroupKey, GroupStats> candidateGroups = groupStats(candidate);
        for (String model : baseline.runSettings().models()) {
            for (VisionMatrixInput input : baseline.inputs()) {
                GroupKey key = new GroupKey(model, input.caseId());
                consumer.accept(model, input.caseId(), baselineGroups.get(key), candidateGroups.get(key));
            }
        }
    }

    private static Map<GroupKey, GroupStats> groupStats(VisionMatrixResult result) {
        Map<GroupKey, List<VisionMatrixRow>> grouped = result.rows().stream()
                .collect(Collectors.groupingBy(
                        row -> new GroupKey(row.model(), row.caseId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        LinkedHashMap<GroupKey, GroupStats> statistics = new LinkedHashMap<>();
        grouped.forEach((key, rows) -> statistics.put(key, GroupStats.from(rows)));
        return statistics;
    }

    private static String delta(int baseline, int candidate) {
        return baseline + " → " + candidate + " (" + signed(candidate - baseline) + ")";
    }

    private static String change(boolean baseline, boolean candidate) {
        return (baseline ? "yes" : "no") + " → " + (candidate ? "yes" : "no");
    }

    private static String tokens(int available, long total) {
        return available == 0 ? "0 available" : available + " available, " + total;
    }

    private static String latency(GroupStats stats) {
        return stats.latencySamples() == 0
                ? "n/a"
                : String.format(Locale.ROOT, "%.1f", stats.medianLatencyMs());
    }

    private static String range(GroupStats stats) {
        return stats.latencySamples() == 0
                ? "n/a"
                : stats.minimumLatencyMs() + "–" + stats.maximumLatencyMs();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    record ComparisonResult(String report) {}

    private record ComparableRun(EvidenceManifest manifest, VisionMatrixResult result) {}

    private record RowProtocolIdentity(
            int sequence,
            String model,
            String caseId,
            int repetition,
            Object invocationSettings,
            String mimeType,
            String inputBlake3) {}

    private record GroupKey(String model, String caseId) {}

    private record GroupStats(
            int invocationSuccesses,
            int structuralCompletions,
            boolean exactOutputMatch,
            int tokensInAvailable,
            long tokensInTotal,
            int tokensOutAvailable,
            long tokensOutTotal,
            int latencySamples,
            double medianLatencyMs,
            long minimumLatencyMs,
            long maximumLatencyMs,
            int infrastructureFailures) {

        private static GroupStats from(List<VisionMatrixRow> rows) {
            int successes = (int) rows.stream().filter(VisionMatrixRow::invocationSuccess).count();
            int structural = (int) rows.stream().filter(VisionMatrixRow::structureComplete).count();
            List<VisionMatrixRow> successful = rows.stream().filter(VisionMatrixRow::invocationSuccess).toList();
            List<Long> latencies = successful.stream()
                    .map(VisionMatrixRow::latencyMs)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            int inputAvailable = (int) rows.stream().filter(row -> row.tokensIn() != null).count();
            int outputAvailable = (int) rows.stream().filter(row -> row.tokensOut() != null).count();
            long inputTotal = rows.stream()
                    .map(VisionMatrixRow::tokensIn)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            long outputTotal = rows.stream()
                    .map(VisionMatrixRow::tokensOut)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            int infrastructure = (int) rows.stream()
                    .filter(row -> row.errorCategory() == com.setaccio.lab.model.VisionErrorCategory.MODEL_UNAVAILABLE
                            || row.errorCategory() == com.setaccio.lab.model.VisionErrorCategory.PROVIDER_FAILURE)
                    .count();
            boolean exact = successes == rows.size()
                    && rows.stream().map(VisionMatrixRow::outputText).distinct().count() == 1;
            return new GroupStats(
                    successes,
                    structural,
                    exact,
                    inputAvailable,
                    inputTotal,
                    outputAvailable,
                    outputTotal,
                    latencies.size(),
                    median(latencies),
                    latencies.isEmpty() ? 0 : latencies.getFirst(),
                    latencies.isEmpty() ? 0 : latencies.getLast(),
                    infrastructure);
        }

        private static double median(List<Long> values) {
            if (values.isEmpty()) {
                return 0;
            }
            int middle = values.size() / 2;
            return values.size() % 2 == 0
                    ? (values.get(middle - 1) + values.get(middle)) / 2.0
                    : values.get(middle);
        }
    }

    @FunctionalInterface
    private interface GroupConsumer {

        void accept(String model, String caseId, GroupStats baseline, GroupStats candidate);
    }
}
