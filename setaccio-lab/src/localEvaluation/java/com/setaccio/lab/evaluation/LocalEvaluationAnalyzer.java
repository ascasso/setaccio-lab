package com.setaccio.lab.evaluation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class LocalEvaluationAnalyzer {

    private final LocalFactCheckPromptDefinition prompt;
    private final LocalFactCheckFixtureCatalog catalog;
    private final LocalFactCheckFixtureReview review;
    private final List<LocalEvaluationScheduleEntry> expectedSchedule;

    LocalEvaluationAnalyzer(
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.review = Objects.requireNonNull(review, "review must not be null");
        expectedSchedule = LocalEvaluationProtocol.schedule(catalog);
    }

    MatrixAnalysis analyze(LocalEvaluationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Local evaluation result must not be null");
        }
        List<String> failures = new ArrayList<>();
        validateTopLevel(result, failures);

        List<LocalEvaluationRow> rows = safe(result.rows());
        int comparableRows = Math.min(expectedSchedule.size(), rows.size());
        for (int index = 0; index < comparableRows; index++) {
            validateRow(rows.get(index), expectedSchedule.get(index), result, failures);
        }

        return metrics(rows, List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateTopLevel(LocalEvaluationResult result, List<String> failures) {
        if (result.protocolVersion() != LocalEvaluationProtocol.VERSION) {
            failures.add("Raw local evaluation protocol version drifted from version 1.");
        }
        if (!LocalEvaluationProtocol.SUITE.equals(result.suite())) {
            failures.add("Raw local evaluation suite is not local-fact-check-evaluation.");
        }
        if (!LocalEvaluationProtocol.PROVIDER.equals(result.provider())) {
            failures.add("Raw local evaluation provider is not ollama.");
        }
        if (!LocalEvaluationProtocol.ENDPOINT_CATEGORY.equals(result.endpointCategory())) {
            failures.add("Raw local evaluation endpoint category is not the neutral local value.");
        }
        if (result.startedAt() == null
                || result.finishedAt() == null
                || result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Raw local evaluation timestamps are missing or invalid.");
        }
        if (!LocalEvaluationProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Raw local evaluation execution strategy is not sequential.");
        }
        if (!LocalEvaluationProtocol.PULL_MODEL_STRATEGY.equals(result.pullModelStrategy())) {
            failures.add("Raw local evaluation pull strategy is not never.");
        }
        validateTrackedIdentities(result, failures);
        validateRunSettings(result.runSettings(), failures);
        validateModelIdentity(result.runSettings(), result.judgeModelIdentity(), failures);
        if (!expectedSchedule.equals(safe(result.orderedSchedule()))) {
            failures.add("Raw local evaluation ordered schedule drifted from the locked protocol.");
        }
        if (safe(result.rows()).size() != LocalEvaluationProtocol.ROW_COUNT) {
            failures.add("Raw local evaluation must contain exactly twelve rows.");
        }
    }

    private void validateTrackedIdentities(LocalEvaluationResult result, List<String> failures) {
        if (!prompt.id().equals(result.promptId())
                || !prompt.version().equals(result.promptVersion())
                || !prompt.sha256().equals(result.promptSha256())) {
            failures.add("Raw local evaluation prompt identity drifted from the tracked contract.");
        }
        if (!catalog.id().equals(result.fixtureCatalogId())
                || !catalog.version().equals(result.fixtureCatalogVersion())
                || !catalog.sha256().equals(result.fixtureCatalogSha256())) {
            failures.add("Raw local evaluation fixture catalog identity drifted from the tracked contract.");
        }
        if (!review.id().equals(result.fixtureReviewId())
                || !review.version().equals(result.fixtureReviewVersion())
                || !review.sha256().equals(result.fixtureReviewSha256())) {
            failures.add("Raw local evaluation fixture review identity drifted from the tracked contract.");
        }
    }

    private void validateRunSettings(
            LocalEvaluationRunSettings settings,
            List<String> failures
    ) {
        if (settings == null
                || settings.repetitions() != LocalEvaluationProtocol.REPETITIONS
                || Double.compare(settings.temperature(), LocalEvaluationProtocol.TEMPERATURE) != 0
                || !settings.seeds().equals(LocalEvaluationProtocol.SEEDS)
                || settings.maxTokens() < 1
                || settings.timeoutMillis() < 1
                || settings.maxAttempts() != LocalEvaluationProtocol.MAX_ATTEMPTS) {
            failures.add("Raw local evaluation settings drifted from the locked protocol.");
        }
    }

    private void validateModelIdentity(
            LocalEvaluationRunSettings settings,
            LocalEvaluationModelIdentity identity,
            List<String> failures
    ) {
        if (settings == null
                || identity == null
                || !settings.requestedModel().equals(identity.requestedModel())
                || !LocalEvaluationProtocol.normalizeModelTag(settings.requestedModel())
                        .equals(identity.normalizedInstalledName())
                || !identity.digest().matches("[0-9a-f]{64}")) {
            failures.add("Raw local evaluation judge model identity drifted from the locked protocol.");
        }
    }

    private void validateRow(
            LocalEvaluationRow row,
            LocalEvaluationScheduleEntry expected,
            LocalEvaluationResult result,
            List<String> failures
    ) {
        String key = expected.fixtureId() + "/" + expected.repetition();
        if (row == null) {
            failures.add("Raw local evaluation row is null at sequence " + expected.sequence() + ".");
            return;
        }
        if (row.sequence() != expected.sequence()
                || row.repetition() != expected.repetition()
                || row.seed() != expected.seed()
                || !expected.fixtureId().equals(row.fixtureId())
                || !expected.pairId().equals(row.pairId())
                || !expected.documentBlake3().equals(row.documentBlake3())
                || !expected.claimBlake3().equals(row.claimBlake3())
                || expected.expectedVerdict() != row.expectedVerdict()) {
            failures.add("Raw local evaluation row order or fixture identity drifted at " + key + ".");
        }

        LocalFactCheckJudgeSettings expectedSettings = result.runSettings() == null
                ? null
                : result.runSettings().judgeSettingsFor(expected.repetition());
        if (!Objects.equals(expectedSettings, row.judgeSettings())) {
            failures.add("Raw local evaluation judge settings drifted at " + key + ".");
        }
        if (row.latencyMillis() < 0 || row.attemptCount() != LocalEvaluationProtocol.MAX_ATTEMPTS) {
            failures.add("Raw local evaluation latency or attempt policy is invalid at " + key + ".");
        }
        validateUsage(row, key, failures);
        validateOutcome(row, expected, key, failures);
        validateResponseMetadata(row.responseMetadata(), result.judgeModelIdentity(), key, failures);
        validatePublicSafety(row, key, failures);
    }

    private void validateUsage(LocalEvaluationRow row, String key, List<String> failures) {
        boolean none = row.promptTokens() == null
                && row.completionTokens() == null
                && row.totalTokens() == null;
        boolean complete = row.promptTokens() != null
                && row.completionTokens() != null
                && row.totalTokens() != null;
        if ((!none && !complete)
                || negative(row.promptTokens())
                || negative(row.completionTokens())
                || negative(row.totalTokens())
                || (complete && row.totalTokens().longValue()
                        != (long) row.promptTokens() + row.completionTokens())) {
            failures.add("Raw local evaluation token usage is invalid at " + key + ".");
        }
    }

    private void validateOutcome(
            LocalEvaluationRow row,
            LocalEvaluationScheduleEntry expected,
            String key,
            List<String> failures
    ) {
        LocalFactCheckDiagnosticCategory category = row.diagnosticCategory();
        if (category == null) {
            failures.add("Raw local evaluation row has an unclassified outcome at " + key + ".");
            return;
        }
        if (row.invocationSucceeded()) {
            validateCompletedOutcome(row, expected, key, failures);
        } else {
            boolean infrastructureFailure = category == LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE
                    || category == LocalFactCheckDiagnosticCategory.TIMEOUT
                    || category == LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE;
            if (!infrastructureFailure
                    || row.springEvaluatorPassed() != null
                    || row.normalizedJudgeVerdict() != null
                    || row.expectedVerdictMatched() != null
                    || row.rawResponse() != null
                    || row.responseMetadata() != null
                    || row.promptTokens() != null
                    || row.completionTokens() != null
                    || row.totalTokens() != null
                    || !Objects.equals(LocalEvaluationRow.safeError(category), row.error())) {
                failures.add("Failed local evaluation row has incoherent classified outcome at " + key + ".");
            }
        }
    }

    private void validateCompletedOutcome(
            LocalEvaluationRow row,
            LocalEvaluationScheduleEntry expected,
            String key,
            List<String> failures
    ) {
        LocalFactCheckJudgeVerdict normalized = LocalFactCheckJudgeVerdict.normalize(row.rawResponse());
        boolean evaluatorPassed = normalized == LocalFactCheckJudgeVerdict.SUPPORTED;
        Boolean matched = normalized == null
                ? null
                : matches(expected.expectedVerdict(), normalized);
        LocalFactCheckDiagnosticCategory expectedCategory;
        if (row.rawResponse() == null || row.rawResponse().isBlank()) {
            expectedCategory = LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE;
        } else if (normalized == null) {
            expectedCategory = LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT;
        } else if (Boolean.FALSE.equals(matched)) {
            expectedCategory = LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH;
        } else {
            expectedCategory = LocalFactCheckDiagnosticCategory.NONE;
        }
        if (!Objects.equals(evaluatorPassed, row.springEvaluatorPassed())
                || normalized != row.normalizedJudgeVerdict()
                || !Objects.equals(matched, row.expectedVerdictMatched())
                || expectedCategory != row.diagnosticCategory()
                || row.error() != null) {
            failures.add("Completed local evaluation row has incoherent evaluator or verdict signals at " + key + ".");
        }
    }

    private void validateResponseMetadata(
            LocalFactCheckJudgeResponseMetadata metadata,
            LocalEvaluationModelIdentity identity,
            String key,
            List<String> failures
    ) {
        if (metadata == null) {
            return;
        }
        if (identity == null
                || (!metadata.responseModel().isBlank()
                        && !identity.normalizedInstalledName().equals(
                                LocalEvaluationProtocol.normalizeModelTag(metadata.responseModel())))) {
            failures.add("Raw local evaluation response model metadata drifted at " + key + ".");
        }
        if (containsUnsafeText(metadata.responseId())
                || containsUnsafeText(metadata.responseModel())
                || containsUnsafeMetadata(metadata.attributes())) {
            failures.add("Raw local evaluation response metadata is not public-safe at " + key + ".");
        }
    }

    private void validatePublicSafety(LocalEvaluationRow row, String key, List<String> failures) {
        if (containsUnsafeText(row.error())) {
            failures.add("Raw local evaluation row contains endpoint, credential, or environment data at " + key + ".");
        }
    }

    private MatrixAnalysis metrics(List<LocalEvaluationRow> rows, List<String> failures) {
        ExpectedVerdictAnalysis supported = expectedVerdictMetrics(
                rows,
                LocalFactCheckExpectedVerdict.SUPPORTED);
        ExpectedVerdictAnalysis unsupported = expectedVerdictMetrics(
                rows,
                LocalFactCheckExpectedVerdict.UNSUPPORTED);

        Map<String, List<LocalEvaluationRow>> byFixture = new LinkedHashMap<>();
        rows.stream().filter(Objects::nonNull).forEach(row -> byFixture
                .computeIfAbsent(row.fixtureId(), ignored -> new ArrayList<>())
                .add(row));
        int repetitionConsistent = 0;
        int repetitionDisagreements = 0;
        int repetitionIncomplete = 0;
        for (String fixtureId : catalog.fixtures().stream().map(LocalFactCheckFixture::id).toList()) {
            List<LocalEvaluationRow> fixtureRows = byFixture.getOrDefault(fixtureId, List.of());
            if (fixtureRows.size() != LocalEvaluationProtocol.REPETITIONS
                    || fixtureRows.stream().anyMatch(row -> row.normalizedJudgeVerdict() == null)) {
                repetitionIncomplete++;
            } else if (fixtureRows.stream()
                    .map(LocalEvaluationRow::normalizedJudgeVerdict)
                    .distinct()
                    .count() == 1) {
                repetitionConsistent++;
            } else {
                repetitionDisagreements++;
            }
        }

        int supportedVerdicts = countVerdict(rows, LocalFactCheckJudgeVerdict.SUPPORTED);
        int unsupportedVerdicts = countVerdict(rows, LocalFactCheckJudgeVerdict.UNSUPPORTED);
        int promptUsageAvailable = countPresent(rows, UsageField.PROMPT);
        int completionUsageAvailable = countPresent(rows, UsageField.COMPLETION);
        int totalUsageAvailable = countPresent(rows, UsageField.TOTAL);
        List<Long> latencies = rows.stream()
                .filter(Objects::nonNull)
                .map(LocalEvaluationRow::latencyMillis)
                .filter(value -> value >= 0)
                .sorted()
                .toList();
        Map<LocalFactCheckDiagnosticCategory, Integer> diagnostics = new EnumMap<>(
                LocalFactCheckDiagnosticCategory.class);
        rows.stream()
                .filter(Objects::nonNull)
                .map(LocalEvaluationRow::diagnosticCategory)
                .filter(Objects::nonNull)
                .forEach(category -> diagnostics.merge(category, 1, Integer::sum));

        return new MatrixAnalysis(
                supported,
                unsupported,
                repetitionConsistent,
                repetitionDisagreements,
                repetitionIncomplete,
                supportedVerdicts,
                unsupportedVerdicts,
                supportedVerdicts == LocalEvaluationProtocol.ROW_COUNT,
                unsupportedVerdicts == LocalEvaluationProtocol.ROW_COUNT,
                promptUsageAvailable,
                completionUsageAvailable,
                totalUsageAvailable,
                latencies.size(),
                median(latencies),
                latencies.isEmpty() ? 0 : latencies.getFirst(),
                latencies.isEmpty() ? 0 : latencies.getLast(),
                rows.stream().filter(Objects::nonNull).mapToInt(LocalEvaluationRow::attemptCount).sum(),
                Map.copyOf(diagnostics),
                failures);
    }

    private static ExpectedVerdictAnalysis expectedVerdictMetrics(
            List<LocalEvaluationRow> rows,
            LocalFactCheckExpectedVerdict verdict
    ) {
        List<LocalEvaluationRow> selected = rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.expectedVerdict() == verdict)
                .toList();
        return new ExpectedVerdictAnalysis(
                selected.size(),
                (int) selected.stream().filter(LocalEvaluationRow::invocationSucceeded).count(),
                (int) selected.stream().filter(row -> row.normalizedJudgeVerdict() != null).count(),
                (int) selected.stream().filter(row -> Boolean.TRUE.equals(row.expectedVerdictMatched())).count(),
                (int) selected.stream().filter(row -> Boolean.FALSE.equals(row.expectedVerdictMatched())).count());
    }

    private static int countVerdict(
            List<LocalEvaluationRow> rows,
            LocalFactCheckJudgeVerdict verdict
    ) {
        return (int) rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.normalizedJudgeVerdict() == verdict)
                .count();
    }

    private static int countPresent(List<LocalEvaluationRow> rows, UsageField field) {
        return (int) rows.stream().filter(Objects::nonNull).filter(row -> switch (field) {
            case PROMPT -> row.promptTokens() != null;
            case COMPLETION -> row.completionTokens() != null;
            case TOTAL -> row.totalTokens() != null;
        }).count();
    }

    private static boolean matches(
            LocalFactCheckExpectedVerdict expected,
            LocalFactCheckJudgeVerdict actual
    ) {
        return expected == LocalFactCheckExpectedVerdict.SUPPORTED
                ? actual == LocalFactCheckJudgeVerdict.SUPPORTED
                : actual == LocalFactCheckJudgeVerdict.UNSUPPORTED;
    }

    private static boolean containsUnsafeMetadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (key.matches(
                        ".*(authorization|credential|password|secret|api[-_]?key|endpoint|base[-_]?url|hostname|environment).*")) {
                    return true;
                }
                if (key.equals("host") || key.equals("env") || key.endsWith("_host")) {
                    return true;
                }
                if (containsUnsafeMetadata(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsUnsafeMetadata(item)) {
                    return true;
                }
            }
        } else if (value instanceof String text) {
            return containsUnsafeText(text);
        }
        return false;
    }

    private static boolean containsUnsafeText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("://")
                || lower.contains("authorization:")
                || lower.contains("bearer ")
                || lower.matches("(?s).*(api[_-]?key|password|secret|credential|environment)\\s*=.*")
                || lower.contains("/users/");
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }

    private static double median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private enum UsageField {
        PROMPT,
        COMPLETION,
        TOTAL
    }

    record ExpectedVerdictAnalysis(
            int planned,
            int invocationSuccesses,
            int normalizedVerdicts,
            int expectationMatches,
            int expectationMismatches
    ) {}

    record MatrixAnalysis(
            ExpectedVerdictAnalysis supported,
            ExpectedVerdictAnalysis unsupported,
            int repetitionConsistent,
            int repetitionDisagreements,
            int repetitionIncomplete,
            int supportedVerdicts,
            int unsupportedVerdicts,
            boolean alwaysYes,
            boolean alwaysNo,
            int promptUsageAvailable,
            int completionUsageAvailable,
            int totalUsageAvailable,
            int latencySamples,
            double medianLatencyMillis,
            long minimumLatencyMillis,
            long maximumLatencyMillis,
            int totalAttempts,
            Map<LocalFactCheckDiagnosticCategory, Integer> diagnostics,
            List<String> integrityFailures
    ) {

        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }
}
