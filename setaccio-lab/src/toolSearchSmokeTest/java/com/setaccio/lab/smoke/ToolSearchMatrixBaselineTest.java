package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import com.setaccio.lab.model.ToolSearchObservation;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSearchMatrixBaselineTest {

    private static final String REQUIRED_TOOL = "lab_add_numbers";
    private final ToolSearchMatrixAnalyzer analyzer = new ToolSearchMatrixAnalyzer(new ObjectMapper());

    @Test
    void protocolExactlyMatchesTheJulyTwelveMatrix() {
        assertThat(ToolSearchMatrixBaselineRunner.MODELS)
                .containsExactly("gemma4:e2b", "granite4.1:3b", "qwen3.5:0.8b");
        assertThat(ToolSearchMatrixBaselineRunner.CASE_IDS).containsExactly(
                "arithmetic-add", "catalog-lookup", "catalog-multi-step",
                "no-applicable-domain-tool", "deterministic-tool-failure");
        assertThat(ToolSearchMatrixBaselineRunner.SETTINGS.repetitions()).isEqualTo(2);
        assertThat(ToolSearchMatrixBaselineRunner.SETTINGS.temperature()).isZero();
        assertThat(ToolSearchMatrixBaselineRunner.SETTINGS.baseSeed()).isEqualTo(42);
        assertThat(ToolSearchMatrixBaselineRunner.SETTINGS.seedFor(2)).isEqualTo(43);
        assertThat(ToolSearchMatrixBaselineRunner.SETTINGS.maxTokens()).isNull();
        assertThat(ToolSearchMatrixBaselineRunner.canonicalPrompts())
                .extracting(ToolBenchmarkPrompt::id)
                .containsExactlyElementsOf(ToolSearchMatrixBaselineRunner.CASE_IDS);
        assertThat(ToolSearchMatrixBaselineRunner.canonicalPrompts())
                .filteredOn(prompt -> prompt.id().equals("deterministic-tool-failure"))
                .singleElement()
                .satisfies(prompt -> assertThat(prompt.expectation().requiredToolResponseTerms())
                        .containsExactly(FailureBenchmarkTools.FAILURE_MARKER));
    }

    @Test
    void classifiesAllSixFailureCategoriesWithDeterministicPrecedence() {
        assertThat(analyzer.classify(row(true, false, List.of(), List.of()),
                trace(false, List.of()))).isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.NO_SEARCH_CALL);
        assertThat(analyzer.classify(row(true, false, List.of(), List.of()),
                trace(true, List.of()))).isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.ZERO_DISCOVERY);
        assertThat(analyzer.classify(row(true, false, List.of(), List.of()),
                trace(true, List.of("different"))))
                .isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.INCOMPLETE_DISCOVERY);
        assertThat(analyzer.classify(row(true, false, List.of(), List.of()),
                trace(true, List.of(REQUIRED_TOOL))))
                .isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.DISCOVERED_NOT_EXECUTED);
        assertThat(analyzer.classify(row(false, false, List.of(),
                        List.of(new ToolBenchmarkAssertion("run_completed", "model", false, "failed"))), null))
                .isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.EXECUTION_FAILURE);
        assertThat(analyzer.classify(row(true, true,
                        List.of(new ToolExecutionObservation("tool-1", REQUIRED_TOOL, "22")),
                        List.of(new ToolBenchmarkAssertion("output_contains", "22", false, "missing"))), null))
                .isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.OUTPUT_CONTRACT_FAILURE);
    }

    @Test
    void zeroDiscoveryForAnAbstentionCaseFallsThroughToItsActualOutputFailure() {
        ToolBenchmarkRow row = rowWithExpectation(
                new ToolBenchmarkExpectation(List.of(), List.of(), List.of("BENCHMARK_NO_TOOL"), List.of()),
                true, List.of(),
                List.of(new ToolBenchmarkAssertion("output_contains", "BENCHMARK_NO_TOOL", false, "missing")));

        assertThat(analyzer.classify(row, trace(true, List.of())))
                .isEqualTo(ToolSearchMatrixAnalyzer.FailureCategory.OUTPUT_CONTRACT_FAILURE);
    }

    @Test
    void sharedVerifierRequiresNonEmptyRawAndNormalizedDiscoveriesToMatchExactly() {
        ToolBenchmarkRow row = new ToolBenchmarkRow(
                "ollama", "fixture:model", "case", "prompt",
                new ToolBenchmarkExpectation(List.of(REQUIRED_TOOL), List.of(), List.of(), List.of()),
                AdvisorMode.TOOL_SEARCH, 1, 2, "pair-1", 42, List.of(REQUIRED_TOOL),
                List.of(new ToolCallObservation("search-1", "function", "toolSearchTool", "{\"arg0\":\"add\"}")),
                List.of(new ToolExecutionObservation("search-1", "toolSearchTool", "[\"" + REQUIRED_TOOL + "\"]")),
                List.of(new ToolSearchObservation("search-1", "add", true, List.of("different"))),
                List.of(), List.of(), false, 1L, null, null, "output", true, null);

        ToolSearchTraceVerifier.Verification verification = new ToolSearchTraceVerifier(new ObjectMapper()).verify(row);

        assertThat(verification.discoveryMismatch()).isTrue();
        assertThat(verification.integrityFailures()).anyMatch(value -> value.contains("discovery mismatch"));
    }

    private ToolBenchmarkRow row(boolean success, boolean executed, List<ToolExecutionObservation> responses,
                                 List<ToolBenchmarkAssertion> assertions) {
        List<ToolExecutionObservation> actualResponses = executed && responses.isEmpty()
                ? List.of(new ToolExecutionObservation("tool-1", REQUIRED_TOOL, "22")) : responses;
        return rowWithExpectation(new ToolBenchmarkExpectation(
                List.of(REQUIRED_TOOL), List.of(), List.of(), List.of()), success, actualResponses, assertions);
    }

    private ToolBenchmarkRow rowWithExpectation(ToolBenchmarkExpectation expectation, boolean success,
                                                 List<ToolExecutionObservation> responses,
                                                 List<ToolBenchmarkAssertion> assertions) {
        return new ToolBenchmarkRow(
                "ollama", "fixture:model", "case", "prompt", expectation, AdvisorMode.TOOL_SEARCH,
                1, 2, "pair-1", 42, List.of(REQUIRED_TOOL), List.of(), responses, List.of(), List.of(),
                assertions, false, 1L, null, null, success ? "output" : null, success,
                success ? null : "invocation failed");
    }

    private ToolSearchTraceVerifier.Verification trace(boolean called, List<String> discovered) {
        return new ToolSearchTraceVerifier.Verification(called, called, discovered, false, List.of());
    }
}
