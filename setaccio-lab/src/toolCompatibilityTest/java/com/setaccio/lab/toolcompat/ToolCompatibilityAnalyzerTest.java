package com.setaccio.lab.toolcompat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityAnalyzerTest {

    private final ToolCompatibilityAnalyzer analyzer = new ToolCompatibilityAnalyzer();

    @Test
    void summarizesEveryDimensionWithoutCombiningThemIntoAScore() {
        ToolCompatibilityAnalysis analysis = analyzer.analyze(
                ToolCompatibilityAnalysisTestFixtures.successfulResult());

        assertThat(analysis.invocation()).isEqualTo(new ToolCompatibilityAnalysis.InvocationSummary(
                16, 16, 0, 32, 32, 0, List.of(), 0, 0));
        assertThat(analysis.toolSelection()).isEqualTo(new ToolCompatibilityAnalysis.ToolSelectionSummary(
                16, 0, 0, 0, 2, 16, 0, 0, 0, 0));
        assertThat(analysis.toolArguments()).isEqualTo(new ToolCompatibilityAnalysis.ToolArgumentSummary(
                16,
                16,
                0,
                16,
                0,
                0,
                0,
                16,
                0,
                0,
                16,
                0,
                0,
                0));
        assertThat(analysis.toolExecution()).isEqualTo(new ToolCompatibilityAnalysis.ToolExecutionSummary(
                16,
                0,
                16,
                0,
                0,
                0,
                14,
                2,
                0,
                0,
                14,
                2,
                2));
        assertThat(analysis.completion()).isEqualTo(new ToolCompatibilityAnalysis.CompletionSummary(
                16, 0, 16, 0, 1, 1));
        assertThat(analysis.reasoningStyleOutput())
                .isEqualTo(new ToolCompatibilityAnalysis.ReasoningStyleSummary(1, 2, 1, 1, 2));
        assertThat(analysis.usageAndLatency().providerTurnsWithCompleteUsage()).isEqualTo(30);
        assertThat(analysis.usageAndLatency().providerTurnsWithPartialUsage()).isEqualTo(1);
        assertThat(analysis.usageAndLatency().providerTurnsWithAbsentUsage()).isEqualTo(1);
        assertThat(analysis.usageAndLatency().providerTurnUsage()).hasSize(32);
        assertThat(analysis.usageAndLatency().rowAggregates()).hasSize(16);
        assertThat(analysis.usageAndLatency().providerTurnUsage().getFirst())
                .isEqualTo(new ToolCompatibilityAnalysis.ProviderTurnUsage(
                        1,
                        1,
                        new ToolCompatibilityTokenUsageEvidence(
                                ToolCompatibilityUsageAvailability.PARTIAL, 3, null, null),
                        java.time.Duration.ofMillis(1)));
        assertThat(analysis.usageAndLatency().rowAggregates().getFirst())
                .isEqualTo(new ToolCompatibilityAnalysis.RowUsage(
                        1,
                        new ToolCompatibilityTokenUsageEvidence(
                                ToolCompatibilityUsageAvailability.PARTIAL, 7, 2, 6)));
        assertThat(analysis.usageAndLatency().medianSuccessfulRowLatencyMillis()).isEqualTo(85.0);
        assertThat(analysis.usageAndLatency().minimumSuccessfulRowLatencyMillis()).isEqualTo(10);
        assertThat(analysis.usageAndLatency().maximumSuccessfulRowLatencyMillis()).isEqualTo(160);
        assertThat(analysis.diagnosticCounts())
                .containsEntry(ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT, 3)
                .allSatisfy((category, count) -> {
                    if (!ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT.equals(category)) {
                        assertThat(count).isZero();
                    }
                });
        assertThat(analysis.failedContractDiagnostics()).isEmpty();
    }

    @Test
    void distinguishesMissingAdditionalReorderedDuplicateForbiddenAndAbstentionCounts() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule =
                ToolCompatibilityAnalysisTestFixtures.schedule();

        ToolCompatibilityCaseSelection.ScheduledCase missing = schedule.get(3);
        rows.set(3, ToolCompatibilityAnalysisTestFixtures.row(
                missing,
                List.of(),
                List.of(ToolCompatibilityAnalysisTestFixtures.finalText(missing.caseId())),
                40));

        ToolCompatibilityCaseSelection.ScheduledCase reordered = schedule.get(4);
        List<ToolCompatibilityAnalysisTestFixtures.CallSpec> expectedMulti = expectedCalls(reordered);
        rows.set(4, ToolCompatibilityAnalysisTestFixtures.row(
                reordered,
                List.of(expectedMulti.get(1), expectedMulti.get(0)),
                List.of("", "", ToolCompatibilityAnalysisTestFixtures.finalText(reordered.caseId())),
                50));

        ToolCompatibilityCaseSelection.ScheduledCase forbidden = schedule.get(6);
        rows.set(6, ToolCompatibilityAnalysisTestFixtures.row(
                forbidden,
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_add_numbers", "{\"left\":17.25,\"right\":4.75}")),
                List.of("", ToolCompatibilityAnalysisTestFixtures.finalText(forbidden.caseId())),
                70));

        ToolCompatibilityCaseSelection.ScheduledCase duplicate = schedule.get(12);
        List<ToolCompatibilityAnalysisTestFixtures.CallSpec> expectedDuplicate = expectedCalls(duplicate);
        rows.set(12, ToolCompatibilityAnalysisTestFixtures.row(
                duplicate,
                List.of(expectedDuplicate.get(0), expectedDuplicate.get(1), expectedDuplicate.get(1)),
                List.of("", "", "", ToolCompatibilityAnalysisTestFixtures.finalText(duplicate.caseId())),
                130));

        ToolCompatibilityAnalysis.ToolSelectionSummary selection =
                analyzer.analyze(ToolCompatibilityAnalysisTestFixtures.result(rows)).toolSelection();

        assertThat(selection).isEqualTo(new ToolCompatibilityAnalysis.ToolSelectionSummary(
                15, 1, 1, 2, 1, 12, 1, 2, 1, 1));
    }

    @Test
    void excludesFailedContractsFromSuccessfulLatencyAndClassifiesEmptyFinalOutput() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        ToolCompatibilityCaseSelection.ScheduledCase arithmetic =
                ToolCompatibilityAnalysisTestFixtures.schedule().getFirst();
        List<ToolCompatibilityAnalysisTestFixtures.CallSpec> expected = expectedCalls(arithmetic);
        rows.set(0, ToolCompatibilityAnalysisTestFixtures.row(
                arithmetic,
                expected,
                List.of("", ""),
                10));

        ToolCompatibilityAnalysis analysis = analyzer.analyze(
                ToolCompatibilityAnalysisTestFixtures.result(rows));

        assertThat(analysis.completion()).isEqualTo(new ToolCompatibilityAnalysis.CompletionSummary(
                15, 1, 15, 1, 1, 1));
        assertThat(analysis.invocation().emptyProviderTurnsWithoutToolCall()).isEqualTo(1);
        assertThat(analysis.usageAndLatency().medianSuccessfulRowLatencyMillis()).isEqualTo(90.0);
        assertThat(analysis.usageAndLatency().minimumSuccessfulRowLatencyMillis()).isEqualTo(20);
        assertThat(analysis.failedContractDiagnostics()).containsExactly(
                new ToolCompatibilityAnalysis.RowDiagnostic(
                        1,
                        "arithmetic-add",
                        1,
                        ToolCompatibilityDiagnostic.FINAL_RESPONSE_EMPTY));
        assertThat(analysis.diagnosticCounts())
                .containsEntry(ToolCompatibilityDiagnostic.FINAL_RESPONSE_EMPTY, 1);
    }

    @Test
    void calculatesTheMedianAndObservedRangeForExactlyTwoPassingRows() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule =
                ToolCompatibilityAnalysisTestFixtures.schedule();
        for (int index = 2; index < rows.size(); index++) {
            rows.set(index, ToolCompatibilityAnalysisTestFixtures.timeoutRow(
                    schedule.get(index), (index + 1L) * 10L));
        }

        ToolCompatibilityAnalysis.UsageLatencySummary usage = analyzer.analyze(
                        ToolCompatibilityAnalysisTestFixtures.result(rows))
                .usageAndLatency();

        assertThat(usage.medianSuccessfulRowLatencyMillis()).isEqualTo(15.0);
        assertThat(usage.minimumSuccessfulRowLatencyMillis()).isEqualTo(10);
        assertThat(usage.maximumSuccessfulRowLatencyMillis()).isEqualTo(20);
    }

    @Test
    void retainsTimeoutAndFailedProviderTurnSequenceAsSeparateInvocationFindings() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule =
                ToolCompatibilityAnalysisTestFixtures.schedule();
        rows.set(0, ToolCompatibilityAnalysisTestFixtures.providerFailureRow(schedule.get(0), 10));
        rows.set(1, ToolCompatibilityAnalysisTestFixtures.timeoutRow(schedule.get(1), 20));

        ToolCompatibilityAnalysis analysis = analyzer.analyze(
                ToolCompatibilityAnalysisTestFixtures.result(rows));

        assertThat(analysis.invocation()).isEqualTo(new ToolCompatibilityAnalysis.InvocationSummary(
                16,
                14,
                1,
                29,
                28,
                1,
                List.of(new ToolCompatibilityAnalysis.ProviderTurnReference(1, 1)),
                0,
                0));
        assertThat(analysis.failedContractDiagnostics()).containsExactly(
                new ToolCompatibilityAnalysis.RowDiagnostic(
                        1, "arithmetic-add", 1, ToolCompatibilityDiagnostic.PROVIDER_FAILURE),
                new ToolCompatibilityAnalysis.RowDiagnostic(
                        2, "fixed-utc-time", 1, ToolCompatibilityDiagnostic.ROW_TIMEOUT));
        assertThat(analysis.usageAndLatency().medianSuccessfulRowLatencyMillis()).isEqualTo(95.0);
        assertThat(analysis.usageAndLatency().minimumSuccessfulRowLatencyMillis()).isEqualTo(30);
        assertThat(analysis.usageAndLatency().maximumSuccessfulRowLatencyMillis()).isEqualTo(160);
    }

    @Test
    void countsFrameworkCoercionWithoutCallingRawArgumentsSchemaValid() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        ToolCompatibilityCaseSelection.ScheduledCase arithmetic =
                ToolCompatibilityAnalysisTestFixtures.schedule().getFirst();
        rows.set(0, ToolCompatibilityAnalysisTestFixtures.row(
                arithmetic,
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_add_numbers", "{\"left\":\"17.25\",\"right\":4.75}")),
                List.of("", "22"),
                10));

        ToolCompatibilityAnalysis analysis = analyzer.analyze(
                ToolCompatibilityAnalysisTestFixtures.result(rows));

        assertThat(analysis.toolArguments().declaredSchemaInvalidCalls()).isEqualTo(1);
        assertThat(analysis.toolArguments().expectedArgumentsMismatchedCalls()).isEqualTo(1);
        assertThat(analysis.toolArguments().callbackCoercedMismatchCalls()).isEqualTo(1);
        assertThat(analysis.toolArguments().callbackCoercedSchemaMismatchCalls()).isEqualTo(1);
        assertThat(analysis.toolArguments().callbackCoercedSemanticMismatchCalls()).isEqualTo(1);
        assertThat(analysis.toolExecution().callbackBindingSucceeded()).isEqualTo(16);
        assertThat(analysis.failedContractDiagnostics()).containsExactly(
                new ToolCompatibilityAnalysis.RowDiagnostic(
                        1,
                        "arithmetic-add",
                        1,
                        ToolCompatibilityDiagnostic.SCHEMA_TYPE_MISMATCH));
    }

    @Test
    void rejectsNullInsteadOfSummarizingItAsCompatibilityFailure() {
        assertThatThrownBy(() -> analyzer.analyze(null))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("must not be null");
    }

    private static List<ToolCompatibilityAnalysisTestFixtures.CallSpec> expectedCalls(
            ToolCompatibilityCaseSelection.ScheduledCase scheduled
    ) {
        return ToolCompatibilityProtocol.caseOracle()
                .requireCase(scheduled.caseId())
                .calls()
                .stream()
                .map(call -> new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        call.toolName(), call.arguments().toString()))
                .toList();
    }
}
