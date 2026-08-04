package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalEvaluationAnalyzerTest {

    private final LocalEvaluationAnalyzer analyzer = new LocalEvaluationAnalyzer(
            LocalEvaluationTestFixtures.PROMPT,
            LocalEvaluationTestFixtures.CATALOG,
            LocalEvaluationTestFixtures.REVIEW);

    @Test
    void locksTheCounterbalancedScheduleAndKeepsOutcomeDimensionsSeparate() {
        LocalEvaluationResult result = LocalEvaluationTestFixtures.successfulResult();

        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);

        assertThat(result.orderedSchedule())
                .extracting(LocalEvaluationScheduleEntry::fixtureId)
                .containsExactly(
                        "harbor-library-supported",
                        "harbor-library-unsupported",
                        "riverbend-garden-supported",
                        "riverbend-garden-unsupported",
                        "repair-workshop-supported",
                        "repair-workshop-unsupported",
                        "harbor-library-unsupported",
                        "harbor-library-supported",
                        "riverbend-garden-unsupported",
                        "riverbend-garden-supported",
                        "repair-workshop-unsupported",
                        "repair-workshop-supported");
        assertThat(result.orderedSchedule())
                .extracting(LocalEvaluationScheduleEntry::seed)
                .containsExactly(42, 42, 42, 42, 42, 42, 43, 43, 43, 43, 43, 43);
        assertThat(analysis.valid()).isTrue();
        assertThat(analysis.supported())
                .isEqualTo(new LocalEvaluationAnalyzer.ExpectedVerdictAnalysis(6, 6, 6, 6, 0));
        assertThat(analysis.unsupported())
                .isEqualTo(new LocalEvaluationAnalyzer.ExpectedVerdictAnalysis(6, 6, 6, 6, 0));
        assertThat(analysis.repetitionConsistent()).isEqualTo(6);
        assertThat(analysis.repetitionDisagreements()).isZero();
        assertThat(analysis.alwaysYes()).isFalse();
        assertThat(analysis.alwaysNo()).isFalse();
        assertThat(analysis.promptUsageAvailable()).isEqualTo(12);
        assertThat(analysis.medianLatencyMillis()).isEqualTo(65.0);
        assertThat(analysis.minimumLatencyMillis()).isEqualTo(10);
        assertThat(analysis.maximumLatencyMillis()).isEqualTo(120);
        assertThat(analysis.totalAttempts()).isEqualTo(12);
    }

    @Test
    void summarizesEveryVerdictFailureAndRepetitionDimensionWithoutAnOrderClaim() {
        LocalEvaluationResult result = LocalEvaluationTestFixtures.diagnosticResult();
        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);

        assertThat(analysis.valid()).isTrue();
        assertThat(analysis.supported())
                .isEqualTo(new LocalEvaluationAnalyzer.ExpectedVerdictAnalysis(6, 5, 4, 3, 1));
        assertThat(analysis.unsupported())
                .isEqualTo(new LocalEvaluationAnalyzer.ExpectedVerdictAnalysis(6, 4, 3, 3, 0));
        assertThat(analysis.repetitionDisagreements()).isEqualTo(1);
        assertThat(analysis.repetitionIncomplete()).isEqualTo(5);
        assertThat(analysis.diagnostics())
                .containsEntry(LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE, 1)
                .containsEntry(LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT, 1)
                .containsEntry(LocalFactCheckDiagnosticCategory.JUDGE_MODEL_UNAVAILABLE, 1)
                .containsEntry(LocalFactCheckDiagnosticCategory.TIMEOUT, 1)
                .containsEntry(LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE, 1);
        assertThat(analysis.promptUsageAvailable()).isEqualTo(9);

        String report = new LocalEvaluationReport().render(
                result,
                analysis,
                LocalEvaluationProtocol.RAW_FILENAME,
                "b".repeat(64));
        assertThat(report)
                .contains("## Expected-verdict agreement")
                .contains("## Repetition consistency")
                .contains("Repetition disagreement: 1 fixture(s)")
                .contains("## Verdict tendency")
                .contains("Always-yes tendency: no")
                .contains("Always-no tendency: no")
                .contains("Empty response: 1")
                .contains("Malformed verdict: 1")
                .contains("Prompt tokens available: 9/12 rows")
                .contains("Judge model unavailable: 1")
                .contains("Timeout: 1")
                .contains("Provider failure: 1")
                .contains("does not isolate or claim an order effect");
    }

    @Test
    void rejectsPromptCatalogReviewAndModelIdentityDrift() {
        LocalEvaluationResult valid = LocalEvaluationTestFixtures.successfulResult();
        LocalEvaluationResult drifted = LocalEvaluationTestFixtures.copy(
                valid,
                new LocalEvaluationModelIdentity(
                        valid.judgeModelIdentity().requestedModel(),
                        "different-model:latest",
                        "b".repeat(64)),
                "c".repeat(64),
                "d".repeat(64),
                "e".repeat(64),
                valid.orderedSchedule(),
                valid.rows());

        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(drifted);

        assertThat(analysis.valid()).isFalse();
        assertThat(analysis.integrityFailures())
                .anyMatch(failure -> failure.contains("prompt identity"))
                .anyMatch(failure -> failure.contains("fixture catalog identity"))
                .anyMatch(failure -> failure.contains("fixture review identity"))
                .anyMatch(failure -> failure.contains("judge model identity"));
    }

    @Test
    void rejectsWrongRowCountOrderAttemptsAndUnclassifiedFailures() {
        LocalEvaluationResult valid = LocalEvaluationTestFixtures.successfulResult();
        List<LocalEvaluationRow> rows = new ArrayList<>(valid.rows());
        LocalEvaluationRow first = rows.getFirst();
        rows.set(0, LocalEvaluationTestFixtures.unclassifiedFailure(first, 2, 2));
        rows.removeLast();
        List<LocalEvaluationScheduleEntry> schedule = new ArrayList<>(valid.orderedSchedule());
        schedule.set(0, schedule.get(1));
        LocalEvaluationResult invalid = LocalEvaluationTestFixtures.copy(
                valid,
                valid.judgeModelIdentity(),
                valid.promptSha256(),
                valid.fixtureCatalogSha256(),
                valid.fixtureReviewSha256(),
                schedule,
                rows);

        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(invalid);

        assertThat(analysis.valid()).isFalse();
        assertThat(analysis.integrityFailures())
                .anyMatch(failure -> failure.contains("exactly twelve rows"))
                .anyMatch(failure -> failure.contains("ordered schedule"))
                .anyMatch(failure -> failure.contains("order or fixture identity"))
                .anyMatch(failure -> failure.contains("attempt policy"))
                .anyMatch(failure -> failure.contains("unclassified outcome"));
    }

    @Test
    void rejectsPartialUsageAndSensitiveResponseMetadata() {
        LocalEvaluationResult valid = LocalEvaluationTestFixtures.successfulResult();
        List<LocalEvaluationRow> rows = new ArrayList<>(valid.rows());
        LocalEvaluationRow first = rows.getFirst();
        rows.set(0, LocalEvaluationTestFixtures.copyRow(
                first,
                first.sequence(),
                first.attemptCount(),
                first.diagnosticCategory(),
                new LocalFactCheckJudgeResponseMetadata(
                        first.responseMetadata().responseId(),
                        first.responseMetadata().responseModel(),
                        Map.of("endpoint", "http://localhost:11434")),
                first.promptTokens(),
                null,
                first.totalTokens()));
        LocalEvaluationResult invalid = LocalEvaluationTestFixtures.resultWithRows(rows);

        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(invalid);

        assertThat(analysis.valid()).isFalse();
        assertThat(analysis.integrityFailures())
                .anyMatch(failure -> failure.contains("token usage"))
                .anyMatch(failure -> failure.contains("not public-safe"));
    }
}
