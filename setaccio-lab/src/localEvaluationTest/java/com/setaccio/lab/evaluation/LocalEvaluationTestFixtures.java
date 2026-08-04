package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LocalEvaluationTestFixtures {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    static final LocalFactCheckPromptDefinition PROMPT = new LocalFactCheckPromptDefinition();
    static final LocalFactCheckFixtureCatalog CATALOG = new LocalFactCheckFixtureCatalog(OBJECT_MAPPER);
    static final LocalFactCheckFixtureReview REVIEW = new LocalFactCheckFixtureReview(OBJECT_MAPPER, CATALOG);
    static final LocalEvaluationRunSettings SETTINGS = LocalEvaluationProtocol.settings(
            "judge-model",
            64,
            Duration.ofSeconds(30));
    static final LocalEvaluationModelIdentity MODEL_IDENTITY = new LocalEvaluationModelIdentity(
            "judge-model",
            "judge-model:latest",
            "a".repeat(64));

    private LocalEvaluationTestFixtures() {}

    static LocalEvaluationResult successfulResult() {
        List<LocalEvaluationRow> rows = new ArrayList<>();
        for (LocalEvaluationScheduleEntry entry : LocalEvaluationProtocol.schedule(CATALOG)) {
            boolean supported = entry.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED;
            LocalFactCheckJudgeResult result = new LocalFactCheckJudgeResult(
                    entry.fixtureId(),
                    entry.expectedVerdict(),
                    SETTINGS.judgeSettingsFor(entry.repetition()),
                    true,
                    supported,
                    supported
                            ? LocalFactCheckJudgeVerdict.SUPPORTED
                            : LocalFactCheckJudgeVerdict.UNSUPPORTED,
                    true,
                    LocalFactCheckDiagnosticCategory.NONE,
                    supported ? "yes" : "no",
                    new LocalFactCheckJudgeResponseMetadata(
                            "response-" + entry.sequence(),
                            MODEL_IDENTITY.normalizedInstalledName(),
                            Map.of("done", true)),
                    10 + entry.sequence(),
                    1,
                    11 + entry.sequence(),
                    entry.sequence() * 10L,
                    1,
                    null);
            rows.add(LocalEvaluationRow.from(entry, result));
        }
        return resultWithRows(rows);
    }

    static LocalEvaluationResult diagnosticResult() {
        LocalEvaluationResult successful = successfulResult();
        List<LocalEvaluationRow> rows = new ArrayList<>(successful.rows());
        rows.set(0, completed(
                rows.get(0),
                "no",
                LocalFactCheckJudgeVerdict.UNSUPPORTED,
                false,
                LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH));
        rows.set(1, completed(
                rows.get(1),
                " ",
                null,
                null,
                LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE));
        rows.set(2, completed(
                rows.get(2),
                "maybe",
                null,
                null,
                LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT));
        rows.set(3, failed(rows.get(3), LocalFactCheckDiagnosticCategory.TIMEOUT));
        rows.set(4, failed(rows.get(4), LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE));
        rows.set(5, failed(rows.get(5), LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE));
        return resultWithRows(rows);
    }

    static LocalEvaluationResult resultWithRows(List<LocalEvaluationRow> rows) {
        Instant startedAt = Instant.parse("2026-08-03T12:00:00Z");
        return LocalEvaluationProtocol.result(
                startedAt,
                startedAt.plusSeconds(12),
                SETTINGS,
                MODEL_IDENTITY,
                rows,
                PROMPT,
                CATALOG,
                REVIEW);
    }

    static LocalEvaluationResult copy(
            LocalEvaluationResult source,
            LocalEvaluationModelIdentity modelIdentity,
            String promptSha256,
            String catalogSha256,
            String reviewSha256,
            List<LocalEvaluationScheduleEntry> schedule,
            List<LocalEvaluationRow> rows
    ) {
        return new LocalEvaluationResult(
                source.protocolVersion(),
                source.suite(),
                source.provider(),
                source.endpointCategory(),
                source.startedAt(),
                source.finishedAt(),
                source.executionStrategy(),
                source.pullModelStrategy(),
                source.runSettings(),
                modelIdentity,
                source.promptId(),
                source.promptVersion(),
                promptSha256,
                source.fixtureCatalogId(),
                source.fixtureCatalogVersion(),
                catalogSha256,
                source.fixtureReviewId(),
                source.fixtureReviewVersion(),
                reviewSha256,
                schedule,
                rows);
    }

    static LocalEvaluationRow copyRow(
            LocalEvaluationRow source,
            int sequence,
            int attemptCount,
            LocalFactCheckDiagnosticCategory category,
            LocalFactCheckJudgeResponseMetadata metadata,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        return new LocalEvaluationRow(
                sequence,
                source.repetition(),
                source.seed(),
                source.fixtureId(),
                source.pairId(),
                source.documentBlake3(),
                source.claimBlake3(),
                source.expectedVerdict(),
                source.judgeSettings(),
                source.invocationSucceeded(),
                source.springEvaluatorPassed(),
                source.normalizedJudgeVerdict(),
                source.expectedVerdictMatched(),
                category,
                source.rawResponse(),
                metadata,
                promptTokens,
                completionTokens,
                totalTokens,
                source.latencyMillis(),
                attemptCount,
                source.error());
    }

    static LocalEvaluationRow unclassifiedFailure(
            LocalEvaluationRow source,
            int sequence,
            int attemptCount
    ) {
        return new LocalEvaluationRow(
                sequence,
                source.repetition(),
                source.seed(),
                source.fixtureId(),
                source.pairId(),
                source.documentBlake3(),
                source.claimBlake3(),
                source.expectedVerdict(),
                source.judgeSettings(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                source.latencyMillis(),
                attemptCount,
                null);
    }

    private static LocalEvaluationRow completed(
            LocalEvaluationRow source,
            String raw,
            LocalFactCheckJudgeVerdict verdict,
            Boolean matched,
            LocalFactCheckDiagnosticCategory category
    ) {
        return new LocalEvaluationRow(
                source.sequence(),
                source.repetition(),
                source.seed(),
                source.fixtureId(),
                source.pairId(),
                source.documentBlake3(),
                source.claimBlake3(),
                source.expectedVerdict(),
                source.judgeSettings(),
                true,
                verdict == LocalFactCheckJudgeVerdict.SUPPORTED,
                verdict,
                matched,
                category,
                raw,
                source.responseMetadata(),
                source.promptTokens(),
                source.completionTokens(),
                source.totalTokens(),
                source.latencyMillis(),
                1,
                null);
    }

    private static LocalEvaluationRow failed(
            LocalEvaluationRow source,
            LocalFactCheckDiagnosticCategory category
    ) {
        return new LocalEvaluationRow(
                source.sequence(),
                source.repetition(),
                source.seed(),
                source.fixtureId(),
                source.pairId(),
                source.documentBlake3(),
                source.claimBlake3(),
                source.expectedVerdict(),
                source.judgeSettings(),
                false,
                null,
                null,
                null,
                category,
                null,
                null,
                null,
                null,
                null,
                source.latencyMillis(),
                1,
                LocalEvaluationRow.safeError(category));
    }
}
