package com.setaccio.lab.toolcompat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityReportTest {

    private final ToolCompatibilityAnalyzer analyzer = new ToolCompatibilityAnalyzer();
    private final ToolCompatibilityReport report = new ToolCompatibilityReport();

    @Test
    void rendersEveryDimensionPerTurnUsageAndOnlyMedianAndRangeLatency() {
        ToolCompatibilityResult result = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        ToolCompatibilityAnalysis analysis = analyzer.analyze(result);

        String rendered = report.render(result, analysis);

        assertThat(rendered)
                .startsWith("# Tool Compatibility Deterministic Summary\n")
                .contains(
                        "## Invocation",
                        "## Tool Selection",
                        "## Tool Arguments",
                        "## Tool Execution",
                        "## Completion",
                        "## Reasoning-Style Output",
                        "## Usage and Latency",
                        "## Deterministic Diagnostics",
                        "### Failed Contract Primary Categories",
                        "### Per-Turn Usage",
                        "### Per-Row Aggregate Usage",
                        "- Median successful-row latency: `85.0 ms`",
                        "- Observed successful-row latency range: `10-160 ms`",
                        "| 1 | 1 | `PARTIAL` | 3 | - | - | 1 |",
                        "| 16 | `COMPLETE` |")
                .doesNotContainIgnoringCase(
                        "percentile",
                        "p50",
                        "p90",
                        "p95",
                        "p99",
                        "chain of thought")
                .doesNotContain("- OTHER:", "- Aggregate score:", "- Model rank:");
        assertThat(report.render(result, analysis)).isEqualTo(rendered);
    }

    @Test
    void rendersFailedProviderTurnSequencesWithoutRecordFormatting() {
        ToolCompatibilityResult baseline = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> rows = new ArrayList<>(baseline.rows());
        ToolCompatibilityCaseSelection.ScheduledCase first =
                ToolCompatibilityAnalysisTestFixtures.schedule().getFirst();
        rows.set(0, ToolCompatibilityAnalysisTestFixtures.providerFailureRow(first, 10));
        ToolCompatibilityResult result = ToolCompatibilityAnalysisTestFixtures.result(rows);

        String rendered = report.render(result, analyzer.analyze(result));

        assertThat(rendered)
                .contains("- Failed provider-turn sequences: `row 1/turn 1`")
                .doesNotContain("ProviderTurnReference[");
    }

    @Test
    void requiresBothCanonicalResultAndDerivedAnalysis() {
        ToolCompatibilityResult result = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        ToolCompatibilityAnalysis analysis = analyzer.analyze(result);

        assertThatThrownBy(() -> report.render(null, analysis))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> report.render(result, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsAnalysisDerivedFromDifferentRows() {
        ToolCompatibilityResult result = ToolCompatibilityAnalysisTestFixtures.successfulResult();
        List<ToolCompatibilityRow> changedRows = new ArrayList<>(result.rows());
        ToolCompatibilityCaseSelection.ScheduledCase arithmetic =
                ToolCompatibilityAnalysisTestFixtures.schedule().getFirst();
        List<ToolCompatibilityAnalysisTestFixtures.CallSpec> calls = ToolCompatibilityProtocol.caseOracle()
                .requireCase(arithmetic.caseId())
                .calls()
                .stream()
                .map(call -> new ToolCompatibilityAnalysisTestFixtures.CallSpec(
                        call.toolName(), call.arguments().toString()))
                .toList();
        changedRows.set(0, ToolCompatibilityAnalysisTestFixtures.row(
                arithmetic, calls, List.of("", ""), 10));
        ToolCompatibilityAnalysis changedAnalysis = analyzer.analyze(
                ToolCompatibilityAnalysisTestFixtures.result(changedRows));

        assertThatThrownBy(() -> report.render(result, changedAnalysis))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("does not match");
    }
}
