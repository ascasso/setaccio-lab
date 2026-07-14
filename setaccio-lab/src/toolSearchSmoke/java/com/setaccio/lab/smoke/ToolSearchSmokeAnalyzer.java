package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import com.setaccio.lab.model.ToolSearchObservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSearchSmokeAnalyzer {

    private static final String TOOL_SEARCH_TOOL_NAME = "toolSearchTool";
    private static final Set<String> OUTPUT_CONTRACT_CHECKS =
            Set.of("output_contains", "tool_response_contains");

    private final ObjectMapper objectMapper;

    ToolSearchSmokeAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        List<ToolCallObservation> calls = safe(row.selectedToolCalls());
        List<ToolExecutionObservation> responses = safe(row.executedToolResponses());
        List<ToolSearchObservation> normalized = safe(row.toolSearchObservations());

        List<ToolCallObservation> searchCalls = calls.stream()
                .filter(call -> call != null && TOOL_SEARCH_TOOL_NAME.equals(call.name()))
                .toList();
        List<ToolExecutionObservation> searchResponses = responses.stream()
                .filter(response -> response != null && TOOL_SEARCH_TOOL_NAME.equals(response.name()))
                .toList();

        if (searchCalls.isEmpty()) {
            summary.add(ToolSearchSmokeSummary.Bucket.NO_TOOL_SEARCH_CALL, caseId);
            if (!searchResponses.isEmpty() || !normalized.isEmpty()) {
                summary.hardFailure(caseId + ": Tool Search response/normalization exists without a selected call.");
            }
            addBehaviorOverlays(row, Set.of(), responses, summary);
            return;
        }

        Map<String, ToolCallObservation> callsById = uniqueCalls(searchCalls, caseId, summary);
        Map<String, ToolExecutionObservation> responsesById = uniqueResponses(searchResponses, caseId, summary);
        Map<String, ToolSearchObservation> normalizedById = uniqueNormalized(normalized, caseId, summary);
        Set<String> discovered = new LinkedHashSet<>();
        boolean mismatch = false;
        boolean malformed = false;

        for (Map.Entry<String, ToolCallObservation> entry : callsById.entrySet()) {
            String id = entry.getKey();
            ToolExecutionObservation response = responsesById.get(id);
            ToolSearchObservation observation = normalizedById.get(id);
            if (response == null || observation == null) {
                summary.hardFailure(caseId + ": missing Tool Search trace linkage for call ID " + id + ".");
                malformed = true;
                continue;
            }
            String rawQuery = parseQuery(entry.getValue().arguments());
            if (rawQuery == null) {
                summary.hardFailure(caseId + ": malformed Tool Search call arguments for ID " + id + ".");
                malformed = true;
            } else if (!rawQuery.equals(observation.query())) {
                summary.hardFailure(caseId + ": raw and normalized Tool Search queries differ for ID " + id + ".");
                malformed = true;
            }
            List<String> rawTools = parseRawDiscoveredTools(response.responseData());
            if (rawTools == null) {
                summary.hardFailure(caseId + ": malformed Tool Search response wrapper for ID " + id + ".");
                malformed = true;
                continue;
            }
            if (!observation.completed()) {
                summary.hardFailure(caseId + ": linked normalized Tool Search observation is not completed for ID " + id + ".");
                malformed = true;
            }
            if (!rawTools.equals(observation.discoveredTools())) {
                mismatch = true;
                summary.hardFailure(caseId + ": discovery mismatch for ID " + id + " (raw="
                        + rawTools + ", normalized=" + observation.discoveredTools() + ").");
            }
            discovered.addAll(rawTools);
        }

        for (String responseId : responsesById.keySet()) {
            if (!callsById.containsKey(responseId)) {
                summary.hardFailure(caseId + ": orphaned Tool Search response ID " + responseId + ".");
                malformed = true;
            }
        }
        for (String observationId : normalizedById.keySet()) {
            if (!callsById.containsKey(observationId)) {
                summary.hardFailure(caseId + ": orphaned normalized Tool Search observation ID "
                        + observationId + ".");
                malformed = true;
            }
        }

        if (mismatch) {
            summary.add(ToolSearchSmokeSummary.Bucket.DISCOVERY_MISMATCH, caseId);
        }
        if (!malformed) {
            summary.add(discovered.isEmpty()
                    ? ToolSearchSmokeSummary.Bucket.ZERO_MATCHES
                    : ToolSearchSmokeSummary.Bucket.NON_EMPTY_DISCOVERY, caseId);
        }
        addBehaviorOverlays(row, discovered, responses, summary);
    }

    private Map<String, ToolCallObservation> uniqueCalls(List<ToolCallObservation> values, String caseId,
                                                          ToolSearchSmokeSummary summary) {
        Map<String, ToolCallObservation> byId = new LinkedHashMap<>();
        for (ToolCallObservation value : values) {
            String id = value.id();
            if (id == null || id.isBlank()) {
                summary.hardFailure(caseId + ": Tool Search call has a blank trace ID.");
            } else if (byId.putIfAbsent(id, value) != null) {
                summary.hardFailure(caseId + ": duplicate Tool Search call ID " + id + ".");
            }
        }
        return byId;
    }

    private Map<String, ToolExecutionObservation> uniqueResponses(List<ToolExecutionObservation> values,
                                                                   String caseId,
                                                                   ToolSearchSmokeSummary summary) {
        Map<String, ToolExecutionObservation> byId = new LinkedHashMap<>();
        for (ToolExecutionObservation value : values) {
            String id = value.id();
            if (id == null || id.isBlank()) {
                summary.hardFailure(caseId + ": Tool Search response has a blank trace ID.");
            } else if (byId.putIfAbsent(id, value) != null) {
                summary.hardFailure(caseId + ": duplicate Tool Search response ID " + id + ".");
            }
        }
        return byId;
    }

    private Map<String, ToolSearchObservation> uniqueNormalized(List<ToolSearchObservation> values,
                                                                 String caseId,
                                                                 ToolSearchSmokeSummary summary) {
        Map<String, ToolSearchObservation> byId = new LinkedHashMap<>();
        for (ToolSearchObservation value : values) {
            if (value == null || value.callId() == null || value.callId().isBlank()) {
                summary.hardFailure(caseId + ": normalized Tool Search observation has a blank trace ID.");
            } else if (byId.putIfAbsent(value.callId(), value) != null) {
                summary.hardFailure(caseId + ": duplicate normalized Tool Search observation ID "
                        + value.callId() + ".");
            }
        }
        return byId;
    }

    private void addBehaviorOverlays(ToolBenchmarkRow row, Set<String> discovered,
                                     List<ToolExecutionObservation> responses,
                                     ToolSearchSmokeSummary summary) {
        Set<String> executed = new HashSet<>();
        for (ToolExecutionObservation response : responses) {
            if (response != null && !TOOL_SEARCH_TOOL_NAME.equals(response.name())) {
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

    private String parseQuery(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(arguments);
            if (!root.isObject()) {
                return null;
            }
            JsonNode query = root.get("query");
            if (query == null) {
                query = root.get("arg0");
            }
            return query != null && query.isTextual() && !query.asText().isBlank()
                    ? query.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseRawDiscoveredTools(String responseData) {
        if (responseData == null || responseData.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseData);
            if (root.isArray()) {
                List<String> names = new ArrayList<>();
                for (JsonNode value : root) {
                    if (!value.isTextual() || value.asText().isBlank()) {
                        return null;
                    }
                    names.add(value.asText());
                }
                return List.copyOf(names);
            }
            if (root.isTextual() && !root.asText().isBlank()) {
                return List.of(root.asText());
            }
            JsonNode references = root.isObject() ? root.get("toolReferences") : null;
            if (references == null || !references.isArray()) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (JsonNode reference : references) {
                JsonNode name = reference.isObject() ? reference.get("toolName") : null;
                if (name == null || !name.isTextual() || name.asText().isBlank()) {
                    return null;
                }
                names.add(name.asText());
            }
            return List.copyOf(names);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeError(String error) {
        return error == null || error.isBlank() ? "no error detail" : error;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
