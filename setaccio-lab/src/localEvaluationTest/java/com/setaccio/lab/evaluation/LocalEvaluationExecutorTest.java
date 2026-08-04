package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalEvaluationExecutorTest {

    @Test
    void executesExactlyTwelveRowsSequentiallyWithLockedSeedsAndNoRetry() {
        List<String> calls = new ArrayList<>();
        LocalEvaluationPreflight.Prepared prepared = prepared((fixture, settings, prompt) -> {
            calls.add(fixture.id() + ":" + settings.seed());
            boolean supported = fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED;
            return completed(fixture, settings, supported);
        });

        LocalEvaluationResult result = new LocalEvaluationExecutor(Clock.fixed(
                Instant.parse("2026-08-03T12:00:00Z"), ZoneOffset.UTC)).execute(prepared);

        assertThat(calls).hasSize(12).containsExactlyElementsOf(result.orderedSchedule().stream()
                .map(entry -> entry.fixtureId() + ":" + entry.seed())
                .toList());
        assertThat(result.rows()).hasSize(12);
        assertThat(result.rows()).extracting(LocalEvaluationRow::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(result.rows()).extracting(LocalEvaluationRow::seed)
                .containsExactly(42, 42, 42, 42, 42, 42, 43, 43, 43, 43, 43, 43);
        assertThat(result.rows()).extracting(LocalEvaluationRow::attemptCount).containsOnly(1);
        assertThat(result.pullModelStrategy()).isEqualTo("never");
        assertThat(result.executionStrategy()).isEqualTo("sequential");
    }

    @Test
    void retainsAClassifiedFailedAttemptWithoutSelectiveRetryOrReplacement() {
        List<String> calls = new ArrayList<>();
        LocalEvaluationPreflight.Prepared prepared = prepared((fixture, settings, prompt) -> {
            calls.add(fixture.id() + ":" + settings.seed());
            if (calls.size() == 4) {
                return failed(fixture, settings);
            }
            return completed(
                    fixture,
                    settings,
                    fixture.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED);
        });

        LocalEvaluationResult result = new LocalEvaluationExecutor(Clock.systemUTC()).execute(prepared);

        assertThat(calls).hasSize(12);
        assertThat(result.rows()).hasSize(12);
        assertThat(result.rows().get(3).diagnosticCategory())
                .isEqualTo(LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE);
        assertThat(result.rows().get(3).attemptCount()).isOne();
        assertThat(result.rows().get(3).error()).isEqualTo("Judge provider invocation failed");
        assertThat(new LocalEvaluationAnalyzer(
                LocalEvaluationTestFixtures.PROMPT,
                LocalEvaluationTestFixtures.CATALOG,
                LocalEvaluationTestFixtures.REVIEW).analyze(result).valid()).isTrue();
    }

    private static LocalEvaluationPreflight.Prepared prepared(JudgeCall judge) {
        LocalEvaluationContract contract = new LocalEvaluationContract(
                LocalEvaluationTestFixtures.PROMPT,
                LocalEvaluationTestFixtures.CATALOG,
                LocalEvaluationTestFixtures.REVIEW);
        LocalEvaluationPreflight.JudgeSession session = new LocalEvaluationPreflight.JudgeSession() {
            @Override
            public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
                return LocalEvaluationTestFixtures.MODEL_IDENTITY;
            }

            @Override
            public LocalFactCheckJudgeResult evaluate(
                    LocalFactCheckFixture fixture,
                    LocalFactCheckJudgeSettings settings,
                    LocalFactCheckPromptDefinition prompt
            ) {
                return judge.evaluate(fixture, settings, prompt);
            }
        };
        return new LocalEvaluationPreflight.Prepared(
                Path.of("build/evaluation-matrix/2026-08-03-executor-test"),
                LocalEvaluationProtocol.settings("judge-model", 64, Duration.ofSeconds(30)),
                LocalEvaluationTestFixtures.MODEL_IDENTITY,
                contract,
                session);
    }

    private static LocalFactCheckJudgeResult completed(
            LocalFactCheckFixture fixture,
            LocalFactCheckJudgeSettings settings,
            boolean supported
    ) {
        return new LocalFactCheckJudgeResult(
                fixture.id(),
                fixture.expectedVerdict(),
                settings,
                true,
                supported,
                supported ? LocalFactCheckJudgeVerdict.SUPPORTED : LocalFactCheckJudgeVerdict.UNSUPPORTED,
                true,
                LocalFactCheckDiagnosticCategory.NONE,
                supported ? "yes" : "no",
                new LocalFactCheckJudgeResponseMetadata(
                        "response", "judge-model:latest", Map.of("done", true)),
                12,
                1,
                13,
                5,
                1,
                null);
    }

    private static LocalFactCheckJudgeResult failed(
            LocalFactCheckFixture fixture,
            LocalFactCheckJudgeSettings settings
    ) {
        return new LocalFactCheckJudgeResult(
                fixture.id(),
                fixture.expectedVerdict(),
                settings,
                false,
                null,
                null,
                null,
                LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE,
                null,
                null,
                null,
                null,
                null,
                10,
                1,
                "endpoint detail that must not be persisted");
    }

    @FunctionalInterface
    private interface JudgeCall {
        LocalFactCheckJudgeResult evaluate(
                LocalFactCheckFixture fixture,
                LocalFactCheckJudgeSettings settings,
                LocalFactCheckPromptDefinition prompt);
    }
}
