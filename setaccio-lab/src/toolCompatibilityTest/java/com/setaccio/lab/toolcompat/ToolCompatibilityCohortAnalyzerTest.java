package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCompatibilityCohortAnalyzerTest {

    private final ToolCompatibilityCohortAnalyzer analyzer =
            new ToolCompatibilityCohortAnalyzer();

    @Test
    void projectsEveryT34DimensionInExactModelOrder() {
        ToolCompatibilityCohortAnalysis analysis =
                analyzer.analyze(ToolCompatibilityCohortTestFixtures.result());

        assertThat(analysis.models())
                .extracting(model -> model.modelIdentity().effectiveInstalledTag())
                .containsExactly("fixture-peer:1b", "fixture-reference:27b-mlx");
        assertThat(analysis.observedArtifactRuntimeFormats()).containsExactly("GGUF", "MLX");
        assertThat(analysis.artifactRuntimeFormatCoverageComplete()).isTrue();
        assertThat(analysis.mixedArtifactRuntimeFormats()).isTrue();

        ToolCompatibilityCohortAnalysis.ModelAnalysis peer = analysis.models().getFirst();
        assertThat(peer.compatibility().invocation().plannedRows()).isEqualTo(16);
        assertThat(peer.compatibility().toolExecution().validToolCalls()).isEqualTo(16);
        assertThat(peer.compatibility().completion().finalContractsPassed()).isEqualTo(16);
        assertThat(peer.discipline())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.Discipline(2, 2, 2, 2));
        assertThat(peer.arguments())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.ArgumentDetails(0, 0, 0));
        assertThat(peer.multiStepBehavior())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.MultiStepBehavior(
                        2, 2, 2, 2, 2, 0, 0));
        assertThat(peer.failureRecovery())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.FailureRecovery(
                        2, 2, 2, 0, 0));
        assertThat(peer.outputBehavior().finalResponsesPresent()).isEqualTo(16);
        assertThat(peer.outputBehavior().rowsWithVisibleReasoningMarkers()).isEqualTo(3);
        assertThat(peer.outputBehavior().rowsReachingOutputLimit()).isOne();
        assertThat(peer.outputBehavior().finalResponsesWithFormatPollutionMarkers()).isZero();
        assertThat(peer.efficiency().medianSuccessfulRowLatencyMillis()).isEqualTo(85.0);
        assertThat(peer.efficiency().minimumSuccessfulRowLatencyMillis()).isEqualTo(10L);
        assertThat(peer.efficiency().maximumSuccessfulRowLatencyMillis()).isEqualTo(160L);
        assertThat(peer.efficiency().totalTokens().observedProviderTurns()).isEqualTo(30);
        assertThat(peer.efficiency().totalTokens().providerTurns()).isEqualTo(32);
        assertThat(peer.efficiency().totalTokensPerPassingRow()).isNull();
    }

    @Test
    void distinguishesArgumentMultiStepFailureAndOutputObservations() {
        List<ToolCompatibilityRow> rows = anomalyRows();

        ToolCompatibilityCohortAnalysis.ModelAnalysis model = analyzer.analyze(
                        ToolCompatibilityCohortTestFixtures.result(rows, rows))
                .models()
                .getFirst();

        assertThat(model.arguments())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.ArgumentDetails(1, 1, 3));
        assertThat(model.multiStepBehavior())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.MultiStepBehavior(
                        2, 2, 0, 0, 1, 1, 1));
        assertThat(model.failureRecovery())
                .isEqualTo(new ToolCompatibilityCohortAnalysis.FailureRecovery(
                        2, 2, 1, 1, 0));
        assertThat(model.outputBehavior().finalResponsesWithFormatPollutionMarkers()).isOne();
        assertThat(model.discipline().noMatchContractsPassed()).isEqualTo(2);
    }

    @Test
    void reportsTokensPerPassingRowOnlyWithCompletePassingRowUsage() {
        ToolCompatibilityCohortAnalysis.ModelAnalysis model = analyzer.analyze(
                        ToolCompatibilityCohortTestFixtures.completeUsageResult())
                .models()
                .getFirst();

        assertThat(model.efficiency().promptTokens().complete()).isTrue();
        assertThat(model.efficiency().completionTokens().complete()).isTrue();
        assertThat(model.efficiency().totalTokens().complete()).isTrue();
        assertThat(model.efficiency().totalTokens().observedTotal()).isEqualTo(192L);
        assertThat(model.efficiency().totalTokensPerPassingRow()).isEqualTo(12.0);
    }

    @Test
    void recordsAnEmptyFinalResponseAfterTheExpectedDeterministicFailure() {
        List<ToolCompatibilityRow> rows = new ArrayList<>(
                ToolCompatibilityAnalysisTestFixtures.successfulResult().rows());
        ToolCompatibilityCaseSelection.ScheduledCase failure =
                ToolCompatibilityAnalysisTestFixtures.schedule().stream()
                        .filter(row -> "deterministic-tool-failure".equals(row.caseId()))
                        .toList()
                        .getLast();
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                failure,
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_fail_fixture", "{}")),
                List.of("", ""),
                160));

        ToolCompatibilityCohortAnalysis.ModelAnalysis model = analyzer.analyze(
                        ToolCompatibilityCohortTestFixtures.result(rows, rows))
                .models()
                .getFirst();

        assertThat(model.failureRecovery().deterministicCallbackFailuresRetained())
                .isEqualTo(2);
        assertThat(model.failureRecovery().errorReportingMarkersPresent()).isOne();
        assertThat(model.failureRecovery().emptyFinalResponses()).isOne();
        assertThat(model.outputBehavior().finalResponsesEmpty()).isOne();
    }

    @Test
    void rendersDeterministicPerModelDimensionsAndMixedFormatBoundary() {
        ToolCompatibilityCohortResult result = ToolCompatibilityCohortTestFixtures.result();

        String report = new ToolCompatibilityCohortReport().render(
                result,
                ToolCompatibilityCohortResult.RAW_FILENAME,
                "d".repeat(64),
                new EvidenceCodeBaseline("a".repeat(40), false));

        assertThat(report)
                .startsWith("# Tool Compatibility Cohort Multidimensional Summary\n")
                .containsSubsequence(
                        "### 1. `fixture-peer:1b` (`peer`)",
                        "#### Compatibility",
                        "#### Discipline",
                        "#### Arguments",
                        "#### Multi-Step Behavior",
                        "#### Failure Recovery",
                        "#### Output Behavior",
                        "#### Efficiency",
                        "#### Incomplete or Unsupported Observations",
                        "### 2. `fixture-reference:27b-mlx` (`reference`)")
                .contains("- Mixed artifact/runtime formats: `true`")
                .contains("mixed-format differences are not attributed solely to model weights")
                .contains("lexical marker observations, not semantic judgments")
                .contains("This report produces no aggregate score")
                .doesNotContain("## Winner", "## Ranking", "## Leaderboard");
        assertThat(EvidenceIntegrity.sha256(report.getBytes(StandardCharsets.UTF_8)))
                .as("golden cohort analysis report digest")
                .isEqualTo("059bf537d41a9c42e9de05065d71e6c950bf16528bd9273d7bc19bb24644ef34");
    }

    private static List<ToolCompatibilityRow> anomalyRows() {
        List<ToolCompatibilityRow> rows = new ArrayList<>(
                ToolCompatibilityAnalysisTestFixtures.successfulResult().rows());
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule =
                ToolCompatibilityAnalysisTestFixtures.schedule();

        List<ToolCompatibilityCaseSelection.ScheduledCase> zoneRows = schedule.stream()
                .filter(row -> "fixed-zone-time".equals(row.caseId()))
                .toList();
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                zoneRows.getFirst(),
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_fixed_time_for_zone", "{}")),
                List.of("", "America/Los_Angeles"),
                30));
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                zoneRows.getLast(),
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_fixed_time_for_zone",
                        "{\"zoneId\":\"UTC\",\"invented\":\"value\"}")),
                List.of("", "America/Los_Angeles"),
                110));

        List<ToolCompatibilityCaseSelection.ScheduledCase> multiRows = schedule.stream()
                .filter(row -> "catalog-multi-step".equals(row.caseId()))
                .toList();
        ToolCompatibilityAnalysisTestFixtures.CallSpec lookup =
                new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_lookup_catalog_item",
                        "{\"itemId\":\"fixture-invoice-sample\"}");
        ToolCompatibilityAnalysisTestFixtures.CallSpec list =
                new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_list_catalog_items", "{\"category\":\"document\"}");
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                multiRows.getFirst(),
                List.of(lookup),
                List.of("", "fixture-invoice-sample appears in the document catalog"),
                50));
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                multiRows.getLast(),
                List.of(lookup, lookup, list),
                List.of("", "", "", "fixture-invoice-sample appears in the document catalog"),
                130));

        ToolCompatibilityCaseSelection.ScheduledCase successClaimFailure = schedule.stream()
                .filter(row -> "deterministic-tool-failure".equals(row.caseId()))
                .toList()
                .getLast();
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                successClaimFailure,
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_fail_fixture", "{}")),
                List.of("", "Completed successfully."),
                160));

        ToolCompatibilityCaseSelection.ScheduledCase pollutedOutput = schedule.stream()
                .filter(row -> "arithmetic-add".equals(row.caseId()))
                .findFirst()
                .orElseThrow();
        replace(rows, ToolCompatibilityAnalysisTestFixtures.row(
                pollutedOutput,
                List.of(new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        "lab_add_numbers", "{\"left\":17.25,\"right\":4.75}")),
                List.of("", "```json\n22\n```"),
                10));
        return List.copyOf(rows);
    }

    private static void replace(List<ToolCompatibilityRow> rows, ToolCompatibilityRow row) {
        rows.set(row.sequence() - 1, row);
    }
}
