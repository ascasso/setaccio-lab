package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Strictly compares the two verified F1 arms before rendering the bounded Phase 4 metrics.
 *
 * <p>The comparison intentionally reports descriptive aggregates only. It does not interpret
 * the experiment or promote a completion-token observation to a provider finish reason.</p>
 */
final class LocalEvaluationBudgetComparison {

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final LocalEvaluationBudgetEvidence evidence;
    private final LocalEvaluationBudgetComparisonReport report;

    LocalEvaluationBudgetComparison(
            ObjectMapper objectMapper,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidence = new LocalEvaluationBudgetEvidence(objectMapper, prompt, catalog, review);
        report = new LocalEvaluationBudgetComparisonReport();
    }

    ComparisonResult compare(Path budget64Directory, Path budget256Directory) {
        LocalEvaluationBudgetEvidence.OfflinePairResult verification = evidence.verifyPair(
                budget64Directory,
                budget256Directory);
        if (!verification.valid()) {
            throw new IllegalArgumentException(
                    "F1 budget pair did not verify: " + String.join("; ", verification.failures()));
        }

        Arm budget64 = loadArm(budget64Directory, "64-token arm");
        Arm budget256 = loadArm(budget256Directory, "256-token arm");
        requireCleanSharedBaseline(budget64, budget256);

        ArmMetrics metrics64 = ArmMetrics.from(budget64.result());
        ArmMetrics metrics256 = ArmMetrics.from(budget256.result());
        String renderedReport = report.render(budget64, budget256, metrics64, metrics256);
        return new ComparisonResult(
                budget64.manifest().runId(),
                budget256.manifest().runId(),
                budget64.manifest().codeBaseline().gitCommit(),
                metrics64,
                metrics256,
                renderedReport);
    }

    private Arm loadArm(Path directory, String label) {
        try {
            Path root = directory.toAbsolutePath().normalize();
            EvidenceManifest manifest = manifestStore.read(root);
            LocalEvaluationResult result = objectMapper.readerFor(LocalEvaluationResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(root.resolve(LocalEvaluationProtocol.RAW_FILENAME).toFile());
            return new Arm(manifest, result);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    label + " could not be loaded after verification: " + safeMessage(exception),
                    exception);
        }
    }

    private static void requireCleanSharedBaseline(Arm budget64, Arm budget256) {
        EvidenceCodeBaseline baseline64 = budget64.manifest().codeBaseline();
        EvidenceCodeBaseline baseline256 = budget256.manifest().codeBaseline();
        List<String> failures = new ArrayList<>();
        requireCleanFullCommit("64-token arm", baseline64, failures);
        requireCleanFullCommit("256-token arm", baseline256, failures);
        if (!Objects.equals(baseline64, baseline256)) {
            failures.add("F1 budget arms do not share the same Git code baseline.");
        }
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "F1 budget pair is not eligible for F3 comparison: " + String.join(" ", failures));
        }
    }

    private static void requireCleanFullCommit(
            String label,
            EvidenceCodeBaseline baseline,
            List<String> failures
    ) {
        if (baseline == null || baseline.gitCommit() == null || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            failures.add(label + " does not record a full Git commit.");
        } else if (baseline.workingTreeDirty()) {
            failures.add(label + " records a dirty Git worktree.");
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    record ComparisonResult(
            String budget64RunId,
            String budget256RunId,
            String sharedGitCommit,
            ArmMetrics budget64,
            ArmMetrics budget256,
            String report
    ) {

        ComparisonResult {
            if (budget64RunId == null || budget256RunId == null || sharedGitCommit == null
                    || budget64 == null || budget256 == null || report == null || report.isBlank()) {
                throw new IllegalArgumentException("complete F3 comparison output is required");
            }
        }
    }

    record ArmMetrics(
            int plannedRows,
            int validVerdicts,
            int emptyResponses,
            int malformedVerdicts,
            int agreementMatches,
            int agreementMismatches,
            int supportedLabelVerdicts,
            int unsupportedLabelVerdicts,
            RepetitionMetrics repetitions,
            OutputLimitMetrics outputLimit,
            CompletionTokenMetrics completionTokens,
            LatencyMetrics latency
    ) {

        static ArmMetrics from(LocalEvaluationResult result) {
            if (result == null || result.runSettings() == null) {
                throw new IllegalArgumentException("verified F1 arm must include result settings");
            }
            List<LocalEvaluationRow> rows = result.rows().stream()
                    .filter(Objects::nonNull)
                    .toList();
            int validVerdicts = (int) rows.stream()
                    .filter(row -> row.normalizedJudgeVerdict() != null)
                    .count();
            int agreementMatches = (int) rows.stream()
                    .filter(row -> row.normalizedJudgeVerdict() != null)
                    .filter(row -> Boolean.TRUE.equals(row.expectedVerdictMatched()))
                    .count();
            int agreementMismatches = (int) rows.stream()
                    .filter(row -> row.normalizedJudgeVerdict() != null)
                    .filter(row -> Boolean.FALSE.equals(row.expectedVerdictMatched()))
                    .count();
            return new ArmMetrics(
                    rows.size(),
                    validVerdicts,
                    countDiagnostic(rows, LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE),
                    countDiagnostic(rows, LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT),
                    agreementMatches,
                    agreementMismatches,
                    countVerdict(rows, LocalFactCheckJudgeVerdict.SUPPORTED),
                    countVerdict(rows, LocalFactCheckJudgeVerdict.UNSUPPORTED),
                    RepetitionMetrics.from(rows),
                    OutputLimitMetrics.from(rows, result.runSettings().maxTokens()),
                    CompletionTokenMetrics.from(rows),
                    LatencyMetrics.from(rows));
        }

        private static int countDiagnostic(
                List<LocalEvaluationRow> rows,
                LocalFactCheckDiagnosticCategory category
        ) {
            return (int) rows.stream().filter(row -> row.diagnosticCategory() == category).count();
        }

        private static int countVerdict(
                List<LocalEvaluationRow> rows,
                LocalFactCheckJudgeVerdict verdict
        ) {
            return (int) rows.stream().filter(row -> row.normalizedJudgeVerdict() == verdict).count();
        }
    }

    record RepetitionMetrics(int consistent, int disagreements, int incomplete) {

        static RepetitionMetrics from(List<LocalEvaluationRow> rows) {
            Map<String, List<LocalEvaluationRow>> byFixture = new LinkedHashMap<>();
            rows.forEach(row -> byFixture.computeIfAbsent(row.fixtureId(), ignored -> new ArrayList<>()).add(row));
            int consistent = 0;
            int disagreements = 0;
            int incomplete = 0;
            for (List<LocalEvaluationRow> fixtureRows : byFixture.values()) {
                if (fixtureRows.size() != LocalEvaluationBudgetProtocol.REPETITIONS
                        || fixtureRows.stream().anyMatch(row -> row.normalizedJudgeVerdict() == null)) {
                    incomplete++;
                } else if (fixtureRows.stream()
                        .map(LocalEvaluationRow::normalizedJudgeVerdict)
                        .distinct()
                        .count() == 1) {
                    consistent++;
                } else {
                    disagreements++;
                }
            }
            return new RepetitionMetrics(consistent, disagreements, incomplete);
        }
    }

    record OutputLimitMetrics(int atConfiguredLimit, int belowConfiguredLimit, int aboveConfiguredLimit, int unavailable) {

        static OutputLimitMetrics from(List<LocalEvaluationRow> rows, int configuredMaximum) {
            int atConfiguredLimit = 0;
            int belowConfiguredLimit = 0;
            int aboveConfiguredLimit = 0;
            int unavailable = 0;
            for (LocalEvaluationRow row : rows) {
                Integer completionTokens = row.completionTokens();
                if (completionTokens == null) {
                    unavailable++;
                } else if (completionTokens == configuredMaximum) {
                    atConfiguredLimit++;
                } else if (completionTokens < configuredMaximum) {
                    belowConfiguredLimit++;
                } else {
                    aboveConfiguredLimit++;
                }
            }
            return new OutputLimitMetrics(
                    atConfiguredLimit,
                    belowConfiguredLimit,
                    aboveConfiguredLimit,
                    unavailable);
        }
    }

    record CompletionTokenMetrics(Map<Integer, Integer> distribution, int unavailable) {

        CompletionTokenMetrics {
            distribution = distribution == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new TreeMap<>(distribution));
        }

        static CompletionTokenMetrics from(List<LocalEvaluationRow> rows) {
            Map<Integer, Integer> distribution = new TreeMap<>();
            int unavailable = 0;
            for (LocalEvaluationRow row : rows) {
                if (row.completionTokens() == null) {
                    unavailable++;
                } else {
                    distribution.merge(row.completionTokens(), 1, Integer::sum);
                }
            }
            return new CompletionTokenMetrics(distribution, unavailable);
        }
    }

    record LatencyMetrics(int samples, double medianMillis, long minimumMillis, long maximumMillis) {

        static LatencyMetrics from(List<LocalEvaluationRow> rows) {
            List<Long> values = rows.stream()
                    .map(LocalEvaluationRow::latencyMillis)
                    .filter(value -> value >= 0)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (values.isEmpty()) {
                return new LatencyMetrics(0, 0.0, 0, 0);
            }
            int middle = values.size() / 2;
            double median = values.size() % 2 == 1
                    ? values.get(middle)
                    : (values.get(middle - 1) + values.get(middle)) / 2.0;
            return new LatencyMetrics(values.size(), median, values.getFirst(), values.getLast());
        }

        String display() {
            if (samples == 0) {
                return "n/a";
            }
            return samples + " samples; median " + String.format(Locale.ROOT, "%.1f", medianMillis)
                    + " ms; range " + minimumMillis + "–" + maximumMillis + " ms";
        }
    }

    record Arm(EvidenceManifest manifest, LocalEvaluationResult result) {

        Arm {
            if (manifest == null || result == null) {
                throw new IllegalArgumentException("verified arm manifest and result are required");
            }
        }
    }
}
