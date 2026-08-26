package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvaluationBudgetComparisonTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalEvaluationBudgetEvidence evidence = new LocalEvaluationBudgetEvidence(
            LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
            LocalEvaluationBudgetTestFixtures.PROMPT,
            LocalEvaluationBudgetTestFixtures.CATALOG,
            LocalEvaluationBudgetTestFixtures.REVIEW);
    private final LocalEvaluationBudgetComparison comparison = new LocalEvaluationBudgetComparison(
            LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
            LocalEvaluationBudgetTestFixtures.PROMPT,
            LocalEvaluationBudgetTestFixtures.CATALOG,
            LocalEvaluationBudgetTestFixtures.REVIEW);

    @Test
    void rendersAStableAggregateOnlyComparisonAcrossEveryF3Dimension() throws Exception {
        Pair pair = writePair("report", varied64Result(), varied256Result(), cleanBaseline());

        LocalEvaluationBudgetComparison.ComparisonResult first = comparison.compare(
                pair.budget64(),
                pair.budget256());
        LocalEvaluationBudgetComparison.ComparisonResult second = comparison.compare(
                pair.budget64(),
                pair.budget256());

        assertThat(first.budget64())
                .extracting(
                        LocalEvaluationBudgetComparison.ArmMetrics::plannedRows,
                        LocalEvaluationBudgetComparison.ArmMetrics::validVerdicts,
                        LocalEvaluationBudgetComparison.ArmMetrics::emptyResponses,
                        LocalEvaluationBudgetComparison.ArmMetrics::malformedVerdicts,
                        LocalEvaluationBudgetComparison.ArmMetrics::agreementMatches,
                        LocalEvaluationBudgetComparison.ArmMetrics::agreementMismatches)
                .containsExactly(12, 10, 1, 1, 9, 1);
        assertThat(first.budget64().repetitions())
                .isEqualTo(new LocalEvaluationBudgetComparison.RepetitionMetrics(3, 1, 2));
        assertThat(first.budget64().outputLimit())
                .isEqualTo(new LocalEvaluationBudgetComparison.OutputLimitMetrics(1, 11, 0, 0));
        assertThat(first.budget64().completionTokens().distribution())
                .containsExactlyEntriesOf(new java.util.TreeMap<>(java.util.Map.of(2, 10, 63, 1, 64, 1)));
        assertThat(first.budget256().outputLimit())
                .isEqualTo(new LocalEvaluationBudgetComparison.OutputLimitMetrics(1, 11, 0, 0));
        assertThat(first.budget256().completionTokens().distribution())
                .containsExactlyEntriesOf(new java.util.TreeMap<>(java.util.Map.of(1, 1, 2, 10, 256, 1)));

        assertThat(first.report()).isEqualTo(second.report());
        assertThat(first.report())
                .contains("# Offline Fact-Check Output-Budget Comparison")
                .contains("| Valid normalized verdict | 10/12 (83.3%) | 12/12 (100.0%) |")
                .contains("| Empty response | 1/12 (8.3%) | 0/12 (0.0%) |")
                .contains("| Malformed verdict | 1/12 (8.3%) | 0/12 (0.0%) |")
                .contains("| Agreement among valid verdicts | 9/10 (90.0%); mismatch 1 | 12/12 (100.0%); mismatch 0 |")
                .contains("| Consistent normalized verdict | 3/6 (50.0%) | 6/6 (100.0%) |")
                .contains("| At configured maximum | 1/12 (8.3%) | 1/12 (8.3%) |")
                .contains("| 2×10, 63×1, 64×1 | 1×1, 2×10, 256×1 |")
                .contains("Completion-token counts are a proxy for the configured output limit")
                .contains("Agreement is reported only among rows with a valid normalized verdict")
                .doesNotContain(temporaryDirectory.toString(), "response-");
    }

    @Test
    void rejectsDirtyOrIncompleteGitBaselinesEvenWhenF2PairVerificationPasses() throws Exception {
        Pair dirty = writePair(
                "dirty",
                LocalEvaluationBudgetTestFixtures.result(64),
                LocalEvaluationBudgetTestFixtures.result(256),
                new EvidenceCodeBaseline("a".repeat(40), true));
        assertThatThrownBy(() -> comparison.compare(dirty.budget64(), dirty.budget256()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64-token arm records a dirty Git worktree")
                .hasMessageContaining("256-token arm records a dirty Git worktree");

        Pair incomplete = writePair(
                "incomplete",
                LocalEvaluationBudgetTestFixtures.result(64),
                LocalEvaluationBudgetTestFixtures.result(256),
                new EvidenceCodeBaseline("unknown", false));
        assertThatThrownBy(() -> comparison.compare(incomplete.budget64(), incomplete.budget256()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64-token arm does not record a full Git commit")
                .hasMessageContaining("256-token arm does not record a full Git commit");
    }

    @Test
    void rejectsAnyF2ParityFailureBeforeItProducesAnF3Report() throws Exception {
        Path budget64 = Files.createDirectory(temporaryDirectory.resolve("drift-64"));
        Path budget256 = Files.createDirectory(temporaryDirectory.resolve("drift-256"));
        LocalEvaluationEvidence armEvidence = new LocalEvaluationEvidence(
                LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
                LocalEvaluationBudgetTestFixtures.PROMPT,
                LocalEvaluationBudgetTestFixtures.CATALOG,
                LocalEvaluationBudgetTestFixtures.REVIEW);
        armEvidence.write(
                budget64,
                LocalEvaluationBudgetTestFixtures.result(64),
                new EvidenceCodeBaseline("a".repeat(40), false));
        armEvidence.write(
                budget256,
                LocalEvaluationBudgetTestFixtures.result(256),
                new EvidenceCodeBaseline("b".repeat(40), false));

        assertThatThrownBy(() -> comparison.compare(budget64, budget256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F1 budget pair did not verify")
                .hasMessageContaining("Git code baseline differs");
    }

    private Pair writePair(
            String name,
            LocalEvaluationResult result64,
            LocalEvaluationResult result256,
            EvidenceCodeBaseline baseline
    ) throws Exception {
        Path budget64 = Files.createDirectory(temporaryDirectory.resolve(name + "-64"));
        Path budget256 = Files.createDirectory(temporaryDirectory.resolve(name + "-256"));
        evidence.writePair(budget64, result64, budget256, result256, baseline);
        return new Pair(budget64, budget256);
    }

    private static EvidenceCodeBaseline cleanBaseline() {
        return new EvidenceCodeBaseline("a".repeat(40), false);
    }

    private static LocalEvaluationResult varied64Result() {
        LocalEvaluationResult base = LocalEvaluationBudgetTestFixtures.result(64);
        List<LocalEvaluationRow> rows = new ArrayList<>(base.rows());
        rows.set(0, empty(rows.get(0), 64, 20));
        rows.set(1, malformed(rows.get(1), 63, 21));
        rows.set(2, mismatch(rows.get(2), 2, 22));
        return withRows(base, rows);
    }

    private static LocalEvaluationResult varied256Result() {
        LocalEvaluationResult base = LocalEvaluationBudgetTestFixtures.result(256);
        List<LocalEvaluationRow> rows = new ArrayList<>(base.rows());
        rows.set(0, matching(rows.get(0), 256, 30));
        rows.set(1, matching(rows.get(1), 1, 31));
        return withRows(base, rows);
    }

    private static LocalEvaluationRow empty(LocalEvaluationRow row, int completionTokens, long latencyMillis) {
        return row(row, null, false, null, LocalFactCheckDiagnosticCategory.EMPTY_RESPONSE,
                "", completionTokens, latencyMillis);
    }

    private static LocalEvaluationRow malformed(LocalEvaluationRow row, int completionTokens, long latencyMillis) {
        return row(row, null, false, null, LocalFactCheckDiagnosticCategory.MALFORMED_VERDICT,
                "perhaps", completionTokens, latencyMillis);
    }

    private static LocalEvaluationRow mismatch(LocalEvaluationRow row, int completionTokens, long latencyMillis) {
        LocalFactCheckJudgeVerdict verdict = row.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED
                ? LocalFactCheckJudgeVerdict.UNSUPPORTED
                : LocalFactCheckJudgeVerdict.SUPPORTED;
        return row(row, verdict, verdict == LocalFactCheckJudgeVerdict.SUPPORTED, false,
                LocalFactCheckDiagnosticCategory.EXPECTATION_MISMATCH,
                verdict == LocalFactCheckJudgeVerdict.SUPPORTED ? "yes" : "no", completionTokens, latencyMillis);
    }

    private static LocalEvaluationRow matching(LocalEvaluationRow row, int completionTokens, long latencyMillis) {
        LocalFactCheckJudgeVerdict verdict = row.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED
                ? LocalFactCheckJudgeVerdict.SUPPORTED
                : LocalFactCheckJudgeVerdict.UNSUPPORTED;
        return row(row, verdict, verdict == LocalFactCheckJudgeVerdict.SUPPORTED, true,
                LocalFactCheckDiagnosticCategory.NONE,
                verdict == LocalFactCheckJudgeVerdict.SUPPORTED ? "yes" : "no", completionTokens, latencyMillis);
    }

    private static LocalEvaluationRow row(
            LocalEvaluationRow base,
            LocalFactCheckJudgeVerdict verdict,
            Boolean evaluatorPassed,
            Boolean expectedMatched,
            LocalFactCheckDiagnosticCategory category,
            String rawResponse,
            int completionTokens,
            long latencyMillis
    ) {
        return new LocalEvaluationRow(
                base.sequence(),
                base.repetition(),
                base.seed(),
                base.fixtureId(),
                base.pairId(),
                base.documentBlake3(),
                base.claimBlake3(),
                base.expectedVerdict(),
                base.judgeSettings(),
                true,
                evaluatorPassed,
                verdict,
                expectedMatched,
                category,
                rawResponse,
                base.responseMetadata(),
                10,
                completionTokens,
                10 + completionTokens,
                latencyMillis,
                1,
                null);
    }

    private static LocalEvaluationResult withRows(LocalEvaluationResult base, List<LocalEvaluationRow> rows) {
        return new LocalEvaluationResult(
                base.protocolVersion(),
                base.suite(),
                base.provider(),
                base.endpointCategory(),
                base.startedAt(),
                base.finishedAt(),
                base.executionStrategy(),
                base.pullModelStrategy(),
                base.runSettings(),
                base.judgeModelIdentity(),
                base.promptId(),
                base.promptVersion(),
                base.promptSha256(),
                base.fixtureCatalogId(),
                base.fixtureCatalogVersion(),
                base.fixtureCatalogSha256(),
                base.fixtureReviewId(),
                base.fixtureReviewVersion(),
                base.fixtureReviewSha256(),
                base.orderedSchedule(),
                rows);
    }

    private record Pair(Path budget64, Path budget256) {}
}
