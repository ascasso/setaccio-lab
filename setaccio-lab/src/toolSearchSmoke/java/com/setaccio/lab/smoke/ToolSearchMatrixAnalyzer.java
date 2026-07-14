package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolExecutionObservation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSearchMatrixAnalyzer {

    enum FailureCategory {
        NO_SEARCH_CALL("no search call"),
        ZERO_DISCOVERY("zero discovery"),
        INCOMPLETE_DISCOVERY("incomplete discovery"),
        DISCOVERED_NOT_EXECUTED("discovered-not-executed"),
        EXECUTION_FAILURE("execution failure"),
        OUTPUT_CONTRACT_FAILURE("output-contract failure");

        private final String label;

        FailureCategory(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final Set<String> EXECUTION_CHECKS = Set.of(
            "run_completed", "required_tool_executed", "forbidden_tool_not_executed");
    private static final Set<String> OUTPUT_CHECKS = Set.of("output_contains", "tool_response_contains");

    private final ToolSearchTraceVerifier traceVerifier;

    ToolSearchMatrixAnalyzer(ObjectMapper objectMapper) {
        this.traceVerifier = new ToolSearchTraceVerifier(objectMapper);
    }

    MatrixAnalysis analyze(ToolBenchmarkComparisonResult result, List<String> models,
                           List<ToolBenchmarkPrompt> prompts, List<String> tools) {
        List<String> integrityFailures = new ArrayList<>();
        validateTopLevel(result, models, prompts, tools, integrityFailures);
        Map<GroupKey, MutableGroup> groups = new LinkedHashMap<>();
        List<ClassifiedFailure> failures = new ArrayList<>();
        analyzeResult(result.standard(), AdvisorMode.STANDARD, models, prompts, groups, failures, integrityFailures);
        analyzeResult(result.toolSearch(), AdvisorMode.TOOL_SEARCH, models, prompts, groups, failures, integrityFailures);

        Map<GroupKey, GroupResult> completed = new LinkedHashMap<>();
        for (Map.Entry<GroupKey, MutableGroup> entry : groups.entrySet()) {
            completed.put(entry.getKey(), entry.getValue().complete());
        }
        return new MatrixAnalysis(Map.copyOf(completed), List.copyOf(failures), List.copyOf(integrityFailures));
    }

    private void validateTopLevel(ToolBenchmarkComparisonResult result, List<String> models,
                                  List<ToolBenchmarkPrompt> prompts, List<String> tools,
                                  List<String> integrityFailures) {
        if (result == null || result.standard() == null || result.toolSearch() == null) {
            throw new IllegalArgumentException("Matrix comparison result is missing an advisor result.");
        }
        if (!"regex".equals(result.toolSearchIndexType())) {
            integrityFailures.add("Expected regex Tool Search index metadata.");
        }
        if (!"paired_sequential".equals(result.executionStrategy())) {
            integrityFailures.add("Expected paired_sequential execution strategy.");
        }
        if (!tools.equals(result.requestedTools()) || result.availableTools() == null
                || !result.availableTools().containsAll(tools)) {
            integrityFailures.add("Requested/available tools do not match ToolBenchmarkCases.toolNames().");
        }
        var settings = result.runSettings();
        if (settings == null || settings.repetitions() != 2 || settings.temperature() != 0.0
                || settings.baseSeed() != 42 || settings.maxTokens() != null
                || settings.comparisonOrder() != ToolBenchmarkComparisonOrder.ALTERNATE) {
            integrityFailures.add("Run settings drifted from the locked July 12 protocol.");
        }
        if (models.size() != 3 || prompts.size() != 5) {
            integrityFailures.add("Locked protocol requires three models and five cases.");
        }
    }

    private void analyzeResult(ToolBenchmarkResult result, AdvisorMode expectedMode, List<String> models,
                               List<ToolBenchmarkPrompt> prompts, Map<GroupKey, MutableGroup> groups,
                               List<ClassifiedFailure> failures, List<String> integrityFailures) {
        int expectedRows = models.size() * prompts.size() * 2;
        if (result.advisorMode() != expectedMode || result.runs() == null || result.runs().size() != expectedRows) {
            integrityFailures.add("Expected " + expectedRows + " " + expectedMode.jsonValue() + " rows.");
            return;
        }
        Map<String, ToolBenchmarkPrompt> promptsById = new LinkedHashMap<>();
        prompts.forEach(prompt -> promptsById.put(prompt.id(), prompt));
        Set<String> expectedModels = new LinkedHashSet<>(models);
        Set<String> rowKeys = new HashSet<>();

        for (ToolBenchmarkRow row : result.runs()) {
            String rowKey = row.model() + "/" + row.promptId() + "/" + row.repetition();
            if (!rowKeys.add(rowKey)) {
                integrityFailures.add("Duplicate " + expectedMode.jsonValue() + " row " + rowKey + ".");
            }
            ToolBenchmarkPrompt prompt = promptsById.get(row.promptId());
            if (!expectedModels.contains(row.model()) || prompt == null || row.advisorMode() != expectedMode
                    || row.repetition() < 1 || row.repetition() > 2) {
                integrityFailures.add("Malformed " + expectedMode.jsonValue() + " row metadata for " + rowKey + ".");
                continue;
            }
            if (!prompt.expectation().equals(row.expectation())) {
                integrityFailures.add("Row expectation differs from canonical Java case for " + rowKey + ".");
            }
            GroupKey groupKey = new GroupKey(row.model(), expectedMode);
            MutableGroup group = groups.computeIfAbsent(groupKey, ignored -> new MutableGroup());
            group.total++;

            ToolSearchTraceVerifier.Verification trace = expectedMode == AdvisorMode.TOOL_SEARCH
                    ? traceVerifier.verify(row) : null;
            if (trace != null) {
                integrityFailures.addAll(trace.integrityFailures());
            }
            if (row.contractPassed()) {
                group.passed++;
                continue;
            }
            FailureCategory category = classify(row, trace);
            if (category == null) {
                integrityFailures.add("Unclassified failed row " + rowKey + ".");
                continue;
            }
            group.failures.merge(category, 1, Integer::sum);
            failures.add(new ClassifiedFailure(row.model(), expectedMode, row.promptId(), row.repetition(), category));
        }
    }

    FailureCategory classify(ToolBenchmarkRow row, ToolSearchTraceVerifier.Verification trace) {
        List<String> required = row.expectation().requiredExecutedTools();
        Set<String> executed = new LinkedHashSet<>();
        for (ToolExecutionObservation response : safe(row.executedToolResponses())) {
            if (response != null && !ToolSearchTraceVerifier.TOOL_SEARCH_TOOL_NAME.equals(response.name())) {
                executed.add(response.name());
            }
        }
        Set<String> failedChecks = new LinkedHashSet<>();
        for (ToolBenchmarkAssertion assertion : safe(row.assertions())) {
            if (assertion != null && !assertion.passed()) {
                failedChecks.add(assertion.check());
            }
        }

        if (trace != null) {
            if (!trace.searchCalled()) {
                return FailureCategory.NO_SEARCH_CALL;
            }
            Set<String> discovered = trace.discoveredToolSet();
            if (!required.isEmpty() && discovered.isEmpty()) {
                return FailureCategory.ZERO_DISCOVERY;
            }
            if (!required.isEmpty() && !discovered.containsAll(required)) {
                return FailureCategory.INCOMPLETE_DISCOVERY;
            }
            if (!required.isEmpty() && !executed.containsAll(required)) {
                return FailureCategory.DISCOVERED_NOT_EXECUTED;
            }
        }
        if (!row.success() || failedChecks.stream().anyMatch(EXECUTION_CHECKS::contains)) {
            return FailureCategory.EXECUTION_FAILURE;
        }
        if (failedChecks.stream().anyMatch(OUTPUT_CHECKS::contains)) {
            return FailureCategory.OUTPUT_CONTRACT_FAILURE;
        }
        return null;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record GroupKey(String model, AdvisorMode advisorMode) {}

    record GroupResult(int passed, int total, Map<FailureCategory, Integer> failures) {}

    record ClassifiedFailure(String model, AdvisorMode advisorMode, String promptId, int repetition,
                             FailureCategory category) {}

    record MatrixAnalysis(Map<GroupKey, GroupResult> groups, List<ClassifiedFailure> failures,
                          List<String> integrityFailures) {
        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }

    private static final class MutableGroup {
        private int passed;
        private int total;
        private final Map<FailureCategory, Integer> failures = new EnumMap<>(FailureCategory.class);

        private GroupResult complete() {
            return new GroupResult(passed, total, Map.copyOf(failures));
        }
    }
}
