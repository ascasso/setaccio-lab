package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import com.setaccio.lab.model.ToolSearchObservation;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSearchSmokeAnalyzerTest {

    private static final String MODEL = "fixture:model";
    private static final String TOOL = "lab_add_numbers";
    private static final ToolBenchmarkRunSettings SETTINGS = new ToolBenchmarkRunSettings(
            1, 0.0, 42, null, ToolBenchmarkComparisonOrder.STANDARD_FIRST);

    private final ToolSearchSmokeAnalyzer analyzer = new ToolSearchSmokeAnalyzer(new ObjectMapper());

    @Test
    void reportsSearchStateAndBehaviorBucketsWithoutFailingOnModelBehavior() {
        ToolBenchmarkPrompt noCall = prompt("no-call", List.of(), List.of());
        ToolBenchmarkPrompt zero = prompt("zero", List.of(), List.of());
        ToolBenchmarkPrompt notExecuted = prompt("not-executed", List.of(TOOL), List.of());
        ToolBenchmarkPrompt outputFailed = prompt("output-failed", List.of(TOOL), List.of("22"));

        ToolBenchmarkRow noCallRow = row(noCall, List.of(), List.of(), List.of(), List.of(), true, null);
        ToolBenchmarkRow zeroRow = row(zero,
                List.of(searchCall("zero-search")),
                List.of(searchResponse("zero-search", List.of())),
                List.of(searchObservation("zero-search", List.of())),
                List.of(), true, null);
        ToolBenchmarkRow notExecutedRow = row(notExecuted,
                List.of(searchCall("not-executed-search")),
                List.of(searchResponse("not-executed-search", List.of(TOOL))),
                List.of(searchObservation("not-executed-search", List.of(TOOL))),
                List.of(new ToolBenchmarkAssertion("required_tool_executed", TOOL, false, "not executed")),
                true, null);
        ToolBenchmarkRow outputFailedRow = row(outputFailed,
                List.of(searchCall("output-failed-search")),
                List.of(
                        searchResponse("output-failed-search", List.of(TOOL)),
                        new ToolExecutionObservation("tool-1", TOOL, "22")),
                List.of(searchObservation("output-failed-search", List.of(TOOL))),
                List.of(new ToolBenchmarkAssertion("output_contains", "22", false, "missing")),
                true, null);
        List<ToolBenchmarkPrompt> prompts = List.of(noCall, zero, notExecuted, outputFailed);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(prompts, List.of(noCallRow, zeroRow, notExecutedRow, outputFailedRow)),
                MODEL, prompts, List.of(TOOL));

        assertThat(summary.hasHardFailures()).isFalse();
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.NO_TOOL_SEARCH_CALL))
                .containsExactly("no-call");
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.ZERO_MATCHES))
                .containsExactly("zero");
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.NON_EMPTY_DISCOVERY))
                .containsExactly("not-executed", "output-failed");
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.REQUIRED_DISCOVERED_NOT_EXECUTED))
                .containsExactly("not-executed");
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.REQUIRED_EXECUTED_OUTPUT_FAILED))
                .containsExactly("output-failed");
    }

    @Test
    void treatsRawVersusNormalizedDiscoveryMismatchAsHardFailure() {
        ToolBenchmarkPrompt prompt = prompt("mismatch", List.of(TOOL), List.of());
        ToolBenchmarkRow row = row(prompt,
                List.of(searchCall("search-1")),
                List.of(searchResponse("search-1", List.of(TOOL))),
                List.of(searchObservation("search-1", List.of("different-tool"))),
                List.of(), true, null);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(prompt), List.of(row)), MODEL, List.of(prompt), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isTrue();
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.DISCOVERY_MISMATCH))
                .containsExactly("mismatch");
        assertThat(summary.hardFailures()).anyMatch(detail -> detail.contains("discovery mismatch"));
    }

    @Test
    void acceptsTheInstalledArrayWrapperRepresentation() {
        ToolBenchmarkPrompt prompt = prompt("array-wrapper", List.of(TOOL), List.of());
        ToolBenchmarkRow row = row(prompt,
                List.of(searchCall("search-1")),
                List.of(
                        new ToolExecutionObservation("search-1", "toolSearchTool", "[\"" + TOOL + "\"]"),
                        new ToolExecutionObservation("tool-1", TOOL, "22")),
                List.of(searchObservation("search-1", List.of(TOOL))),
                List.of(), true, null);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(prompt), List.of(row)), MODEL, List.of(prompt), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isFalse();
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.NON_EMPTY_DISCOVERY))
                .containsExactly("array-wrapper");
    }

    @Test
    void acceptsTheTextualSingletonWrapperRepresentation() {
        ToolBenchmarkPrompt prompt = prompt("text-wrapper", List.of(TOOL), List.of());
        ToolBenchmarkRow row = row(prompt,
                List.of(searchCall("search-1")),
                List.of(
                        new ToolExecutionObservation("search-1", "toolSearchTool", "\"" + TOOL + "\""),
                        new ToolExecutionObservation("tool-1", TOOL, "22")),
                List.of(searchObservation("search-1", List.of(TOOL))),
                List.of(), true, null);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(prompt), List.of(row)), MODEL, List.of(prompt), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isFalse();
        assertThat(summary.cases(ToolSearchSmokeSummary.Bucket.NON_EMPTY_DISCOVERY))
                .containsExactly("text-wrapper");
    }

    @Test
    void treatsMalformedWrappersAndMissingLinkagesAsHardFailures() {
        ToolBenchmarkPrompt malformed = prompt("malformed", List.of(), List.of());
        ToolBenchmarkRow row = row(malformed,
                List.of(searchCall("search-1")),
                List.of(
                        new ToolExecutionObservation("search-1", "toolSearchTool", "{}"),
                        new ToolExecutionObservation("orphan", "toolSearchTool", "[]")),
                List.of(searchObservation("search-1", List.of())), List.of(), true, null);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(malformed), List.of(row)), MODEL, List.of(malformed), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isTrue();
        assertThat(summary.hardFailures())
                .anyMatch(detail -> detail.contains("malformed Tool Search response wrapper"))
                .anyMatch(detail -> detail.contains("orphaned Tool Search response"));
    }

    @Test
    void treatsAMissingResponseLinkAsAHardFailure() {
        ToolBenchmarkPrompt prompt = prompt("missing-link", List.of(), List.of());
        ToolBenchmarkRow row = row(prompt,
                List.of(searchCall("search-1")), List.of(), List.of(), List.of(), true, null);

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(prompt), List.of(row)), MODEL, List.of(prompt), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isTrue();
        assertThat(summary.hardFailures())
                .anyMatch(detail -> detail.contains("missing Tool Search trace linkage"));
    }

    @Test
    void treatsInvocationFailureAsHardFailure() {
        ToolBenchmarkPrompt prompt = prompt("invocation", List.of(), List.of());
        ToolBenchmarkRow row = row(prompt, List.of(), List.of(), List.of(), List.of(), false, "connection refused");

        ToolSearchSmokeSummary summary = analyzer.analyze(
                comparison(List.of(prompt), List.of(row)), MODEL, List.of(prompt), List.of(TOOL));

        assertThat(summary.hasHardFailures()).isTrue();
        assertThat(summary.hardFailures()).anyMatch(detail -> detail.contains("connection refused"));
    }

    @Test
    void printsTheRequiredDiagnosticWarning() {
        ToolSearchSmokeSummary summary = new ToolSearchSmokeSummary();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        summary.printTo(new PrintStream(output));

        assertThat(output.toString()).contains(ToolSearchSmokeSummary.DIAGNOSTIC_WARNING);
    }

    private ToolBenchmarkComparisonResult comparison(List<ToolBenchmarkPrompt> prompts,
                                                       List<ToolBenchmarkRow> toolSearchRows) {
        List<ToolBenchmarkRow> standardRows = prompts.stream()
                .map(prompt -> row(prompt, List.of(), List.of(), List.of(), List.of(), true, null,
                        AdvisorMode.STANDARD))
                .toList();
        return new ToolBenchmarkComparisonResult(
                "tool-calling-comparison", "ollama", "regex", Instant.EPOCH, Instant.EPOCH,
                "host", "http://localhost:11434", SETTINGS, "paired_sequential", List.of(TOOL),
                List.of(TOOL),
                result(AdvisorMode.STANDARD, standardRows),
                result(AdvisorMode.TOOL_SEARCH, toolSearchRows));
    }

    private ToolBenchmarkResult result(AdvisorMode mode, List<ToolBenchmarkRow> rows) {
        return new ToolBenchmarkResult(
                "tool-calling", "ollama", mode, Instant.EPOCH, Instant.EPOCH, "host",
                "http://localhost:11434", SETTINGS, "paired_sequential", List.of(TOOL),
                List.of(TOOL), rows);
    }

    private ToolBenchmarkPrompt prompt(String id, List<String> requiredTools, List<String> requiredOutputTerms) {
        return new ToolBenchmarkPrompt(id, "prompt", new ToolBenchmarkExpectation(
                requiredTools, List.of(), requiredOutputTerms, List.of()));
    }

    private ToolBenchmarkRow row(ToolBenchmarkPrompt prompt, List<ToolCallObservation> calls,
                                 List<ToolExecutionObservation> responses,
                                 List<ToolSearchObservation> observations,
                                 List<ToolBenchmarkAssertion> assertions, boolean success, String error) {
        return row(prompt, calls, responses, observations, assertions, success, error, AdvisorMode.TOOL_SEARCH);
    }

    private ToolBenchmarkRow row(ToolBenchmarkPrompt prompt, List<ToolCallObservation> calls,
                                 List<ToolExecutionObservation> responses,
                                 List<ToolSearchObservation> observations,
                                 List<ToolBenchmarkAssertion> assertions, boolean success, String error,
                                 AdvisorMode mode) {
        return new ToolBenchmarkRow(
                "ollama", MODEL, prompt.id(), prompt.text(), prompt.expectation(), mode,
                1, 1, "pair-1", 42, List.of(TOOL), calls, responses, observations, List.of(),
                assertions, assertions.stream().allMatch(ToolBenchmarkAssertion::passed), 1L,
                null, null, success ? "output" : null, success, error);
    }

    private ToolCallObservation searchCall(String id) {
        return new ToolCallObservation(id, "function", "toolSearchTool", "{\"arg0\":\"add numbers\"}");
    }

    private ToolExecutionObservation searchResponse(String id, List<String> tools) {
        String references = tools.stream()
                .map(tool -> "{\"toolName\":\"" + tool + "\"}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new ToolExecutionObservation(id, "toolSearchTool",
                "{\"toolReferences\":[" + references + "],\"totalMatches\":" + tools.size() + "}");
    }

    private ToolSearchObservation searchObservation(String id, List<String> tools) {
        return new ToolSearchObservation(id, "add numbers", true, tools);
    }
}
