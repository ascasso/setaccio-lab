package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolExecutionObservation;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSearchSmokeAnalyzer {

    private static final Set<String> OUTPUT_CONTRACT_CHECKS =
            Set.of("output_contains", "tool_response_contains");

    private final ToolSearchTraceVerifier traceVerifier;

    ToolSearchSmokeAnalyzer(ObjectMapper objectMapper) {
        this.traceVerifier = new ToolSearchTraceVerifier(objectMapper);
    }

    ToolSearchSmokeSummary analyze(ToolBenchmarkComparisonResult result, String model,
                                   List<ToolBenchmarkPrompt> prompts, List<String> expectedTools) {
        ToolSearchSmokeSummary summary = new ToolSearchSmokeSummary();
        if (result == null || result.standard() == null || result.toolSearch() == null) {
            summary.hardFailure("Malformed comparison result: standard or Tool Search result is missing.");
            return summary;
        }
        validateTopLevel(result, expectedTools, summary);
        validateRows(result.standard(), AdvisorMode.STANDARD, model, prompts, summary, false);
        validateRows(result.toolSearch(), AdvisorMode.TOOL_SEARCH, model, prompts, summary, true);
        return summary;
    }

    private void validateTopLevel(ToolBenchmarkComparisonResult result, List<String> expectedTools,
                                  ToolSearchSmokeSummary summary) {
        if (!"regex".equals(result.toolSearchIndexType())) {
            summary.hardFailure("Malformed comparison result: expected regex Tool Search index metadata.");
        }
        if (result.requestedTools() == null || !result.requestedTools().equals(expectedTools)) {
            summary.hardFailure("Malformed comparison result: requested tools do not match ToolBenchmarkCases.toolNames().");
        }
        if (result.availableTools() == null || !result.availableTools().containsAll(expectedTools)) {
            summary.hardFailure("Malformed comparison result: one or more fixture tools are unavailable.");
        }
    }

    private void validateRows(ToolBenchmarkResult result, AdvisorMode expectedMode, String model,
                              List<ToolBenchmarkPrompt> prompts, ToolSearchSmokeSummary summary,
                              boolean analyzeSearchTrace) {
        if (result.advisorMode() != expectedMode || result.runs() == null) {
            summary.hardFailure("Malformed " + expectedMode.jsonValue() + " result metadata.");
            return;
        }
        Map<String, ToolBenchmarkRow> rowsByPrompt = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ToolBenchmarkRow row : result.runs()) {
            if (row == null || row.promptId() == null) {
                summary.hardFailure("Malformed " + expectedMode.jsonValue() + " row without a prompt ID.");
                continue;
            }
            if (rowsByPrompt.putIfAbsent(row.promptId(), row) != null) {
                duplicates.add(row.promptId());
            }
        }
        for (String duplicate : duplicates) {
            summary.hardFailure("Malformed " + expectedMode.jsonValue() + " result: duplicate row for " + duplicate + ".");
        }
        if (result.runs().size() != prompts.size()) {
            summary.hardFailure("Malformed " + expectedMode.jsonValue() + " result: expected "
                    + prompts.size() + " rows but received " + result.runs().size() + ".");
        }
        for (ToolBenchmarkPrompt prompt : prompts) {
            ToolBenchmarkRow row = rowsByPrompt.get(prompt.id());
            if (row == null) {
                summary.hardFailure("Malformed " + expectedMode.jsonValue() + " result: missing row for "
                        + prompt.id() + ".");
                continue;
            }
            if (!model.equals(row.model()) || row.advisorMode() != expectedMode || row.expectation() == null) {
                summary.hardFailure("Malformed " + expectedMode.jsonValue() + " row metadata for "
                        + prompt.id() + ".");
            }
            if (!row.success()) {
                summary.hardFailure("Invocation failed for " + expectedMode.jsonValue() + "/" + prompt.id()
                        + ": " + safeError(row.error()));
            }
            if (analyzeSearchTrace) {
                analyzeSearchRow(row, summary);
            }
        }
    }

    private void analyzeSearchRow(ToolBenchmarkRow row, ToolSearchSmokeSummary summary) {
        String caseId = row.promptId();
        List<ToolExecutionObservation> responses = safe(row.executedToolResponses());
        ToolSearchTraceVerifier.Verification verification = traceVerifier.verify(row);
        verification.integrityFailures().forEach(summary::hardFailure);
        if (!verification.searchCalled()) {
            summary.add(ToolSearchSmokeSummary.Bucket.NO_TOOL_SEARCH_CALL, caseId);
            addBehaviorOverlays(row, Set.of(), responses, summary);
            return;
        }
        if (verification.discoveryMismatch()) {
            summary.add(ToolSearchSmokeSummary.Bucket.DISCOVERY_MISMATCH, caseId);
        }
        Set<String> discovered = verification.discoveredToolSet();
        if (verification.integrityFailures().isEmpty()) {
            summary.add(discovered.isEmpty()
                    ? ToolSearchSmokeSummary.Bucket.ZERO_MATCHES
                    : ToolSearchSmokeSummary.Bucket.NON_EMPTY_DISCOVERY, caseId);
        }
        addBehaviorOverlays(row, discovered, responses, summary);
    }

    private void addBehaviorOverlays(ToolBenchmarkRow row, Set<String> discovered,
                                     List<ToolExecutionObservation> responses,
                                     ToolSearchSmokeSummary summary) {
        Set<String> executed = new HashSet<>();
        for (ToolExecutionObservation response : responses) {
            if (response != null && !ToolSearchTraceVerifier.TOOL_SEARCH_TOOL_NAME.equals(response.name())) {
                executed.add(response.name());
            }
        }
        List<String> required = row.expectation() == null
                ? List.of() : row.expectation().requiredExecutedTools();
        boolean discoveredNotExecuted = required.stream()
                .anyMatch(tool -> discovered.contains(tool) && !executed.contains(tool));
        if (discoveredNotExecuted) {
            summary.add(ToolSearchSmokeSummary.Bucket.REQUIRED_DISCOVERED_NOT_EXECUTED, row.promptId());
        }
        boolean requiredExecuted = required.stream().anyMatch(executed::contains);
        boolean outputContractFailed = safe(row.assertions()).stream()
                .filter(assertion -> assertion != null && OUTPUT_CONTRACT_CHECKS.contains(assertion.check()))
                .anyMatch(assertion -> !assertion.passed());
        if (requiredExecuted && outputContractFailed) {
            summary.add(ToolSearchSmokeSummary.Bucket.REQUIRED_EXECUTED_OUTPUT_FAILED, row.promptId());
        }
    }


    private String safeError(String error) {
        return error == null || error.isBlank() ? "no error detail" : error;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
