package com.setaccio.lab.evaluation;

import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.core.service.Blake3HashingService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class LocalEvaluationProtocol {

    static final int VERSION = 1;
    static final String SUITE = "local-fact-check-evaluation";
    static final String PROVIDER = "ollama";
    static final String ENDPOINT_CATEGORY = "local";
    static final String EXECUTION_ENGINE = "spring-ai-fact-checking-evaluator";
    static final String EXECUTION_STRATEGY = "sequential";
    static final String PULL_MODEL_STRATEGY = "never";
    static final int REPETITIONS = 2;
    static final double TEMPERATURE = 0.0;
    static final List<Integer> SEEDS = List.of(42, 43);
    static final int MAX_ATTEMPTS = 1;
    static final int ROW_COUNT = 12;
    static final String RAW_FILENAME = "local-evaluation-results.json";

    private static final Blake3HashingService BLAKE3 = new ApacheCommonsBlake3HashingServiceImpl();

    private LocalEvaluationProtocol() {}

    static LocalEvaluationRunSettings settings(
            String requestedModel,
            int maxTokens,
            Duration timeout
    ) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        return new LocalEvaluationRunSettings(
                requestedModel,
                REPETITIONS,
                TEMPERATURE,
                SEEDS,
                maxTokens,
                timeout.toMillis(),
                MAX_ATTEMPTS);
    }

    static List<LocalEvaluationScheduleEntry> schedule(LocalFactCheckFixtureCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        Map<String, List<LocalFactCheckFixture>> pairs = catalog.fixtures().stream()
                .collect(Collectors.groupingBy(
                        LocalFactCheckFixture::pairId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<LocalEvaluationScheduleEntry> schedule = new ArrayList<>(ROW_COUNT);
        int sequence = 1;
        for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
            boolean supportedFirst = repetition == 1;
            for (List<LocalFactCheckFixture> pair : pairs.values()) {
                LocalFactCheckFixture supported = requireVerdict(pair, LocalFactCheckExpectedVerdict.SUPPORTED);
                LocalFactCheckFixture unsupported = requireVerdict(pair, LocalFactCheckExpectedVerdict.UNSUPPORTED);
                List<LocalFactCheckFixture> ordered = supportedFirst
                        ? List.of(supported, unsupported)
                        : List.of(unsupported, supported);
                for (LocalFactCheckFixture fixture : ordered) {
                    schedule.add(new LocalEvaluationScheduleEntry(
                            sequence++,
                            repetition,
                            SEEDS.get(repetition - 1),
                            fixture.id(),
                            fixture.pairId(),
                            BLAKE3.hashString(fixture.document()),
                            BLAKE3.hashString(fixture.claim()),
                            fixture.expectedVerdict()));
                }
            }
        }
        if (schedule.size() != ROW_COUNT) {
            throw new IllegalStateException("Local evaluation schedule must contain exactly twelve rows");
        }
        return List.copyOf(schedule);
    }

    static LocalEvaluationResult result(
            Instant startedAt,
            Instant finishedAt,
            LocalEvaluationRunSettings settings,
            LocalEvaluationModelIdentity modelIdentity,
            List<LocalEvaluationRow> rows,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        return new LocalEvaluationResult(
                VERSION,
                SUITE,
                PROVIDER,
                ENDPOINT_CATEGORY,
                startedAt,
                finishedAt,
                EXECUTION_STRATEGY,
                PULL_MODEL_STRATEGY,
                settings,
                modelIdentity,
                prompt.id(),
                prompt.version(),
                prompt.sha256(),
                catalog.id(),
                catalog.version(),
                catalog.sha256(),
                review.id(),
                review.version(),
                review.sha256(),
                schedule(catalog),
                rows);
    }

    static String normalizeModelTag(String model) {
        String normalized = model.trim();
        return normalized.contains(":") ? normalized : normalized + ":latest";
    }

    static Map<String, Object> manifestSettings(LocalEvaluationResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("endpointCategory", result.endpointCategory());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("judgeModelIdentity", result.judgeModelIdentity());
        settings.put("promptId", result.promptId());
        settings.put("promptVersion", result.promptVersion());
        settings.put("promptSha256", result.promptSha256());
        settings.put("fixtureCatalogId", result.fixtureCatalogId());
        settings.put("fixtureCatalogVersion", result.fixtureCatalogVersion());
        settings.put("fixtureCatalogSha256", result.fixtureCatalogSha256());
        settings.put("fixtureReviewId", result.fixtureReviewId());
        settings.put("fixtureReviewVersion", result.fixtureReviewVersion());
        settings.put("fixtureReviewSha256", result.fixtureReviewSha256());
        settings.put("orderedSchedule", result.orderedSchedule());
        return Collections.unmodifiableMap(settings);
    }

    private static LocalFactCheckFixture requireVerdict(
            List<LocalFactCheckFixture> pair,
            LocalFactCheckExpectedVerdict verdict
    ) {
        return pair.stream()
                .filter(fixture -> fixture.expectedVerdict() == verdict)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fact-check pair does not contain expected verdict " + verdict));
    }
}
