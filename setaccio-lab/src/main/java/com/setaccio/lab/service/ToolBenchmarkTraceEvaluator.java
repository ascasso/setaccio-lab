package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import com.setaccio.lab.model.ToolSearchObservation;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ToolBenchmarkTraceEvaluator {

    static final String TOOL_SEARCH_TOOL_NAME = "toolSearchTool";

    private final ObjectMapper objectMapper;

    ToolBenchmarkTraceEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Assessment assess(
            ToolBenchmarkPrompt prompt,
            AdvisorMode advisorMode,
            boolean runSucceeded,
            String outputText,
            List<ToolCallObservation> selectedToolCalls,
            List<ToolExecutionObservation> executedToolResponses) {
        List<ToolSearchObservation> searches = toolSearchObservations(selectedToolCalls, executedToolResponses);
        List<ToolBenchmarkAssertion> assertions = new ArrayList<>();
        assertions.add(assertion(
                "run_completed",
                "model invocation",
                runSucceeded,
                runSucceeded ? "The model invocation completed." : "The model invocation failed."));

        Set<String> executedTools = new LinkedHashSet<>();
        for (ToolExecutionObservation response : executedToolResponses) {
            if (!TOOL_SEARCH_TOOL_NAME.equals(response.name())) {
                executedTools.add(response.name());
            }
        }
        Set<String> discoveredTools = searches.stream()
                .flatMap(search -> search.discoveredTools().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (advisorMode == AdvisorMode.TOOL_SEARCH) {
            boolean completed = searches.stream().anyMatch(ToolSearchObservation::completed);
            assertions.add(assertion(
                    "tool_search_completed",
                    TOOL_SEARCH_TOOL_NAME,
                    completed,
                    completed
                            ? "Tool Search returned at least one discovery response."
                            : "Tool Search did not return a discovery response."));
        }

        ToolBenchmarkExpectation expectation = prompt.expectation();
        for (String tool : expectation.requiredExecutedTools()) {
            if (advisorMode == AdvisorMode.TOOL_SEARCH) {
                boolean discovered = discoveredTools.contains(tool);
                assertions.add(assertion(
                        "required_tool_discovered",
                        tool,
                        discovered,
                        discovered
                                ? "The required tool appeared in Tool Search results."
                                : "The required tool did not appear in Tool Search results."));
            }
            boolean executed = executedTools.contains(tool);
            assertions.add(assertion(
                    "required_tool_executed",
                    tool,
                    executed,
                    executed ? "The required tool executed." : "The required tool did not execute."));
        }
        for (String tool : expectation.forbiddenExecutedTools()) {
            boolean avoided = !executedTools.contains(tool);
            assertions.add(assertion(
                    "forbidden_tool_not_executed",
                    tool,
                    avoided,
                    avoided ? "The forbidden tool was not executed." : "The forbidden tool executed."));
        }
        for (String term : expectation.requiredOutputTerms()) {
            boolean present = containsIgnoreCase(outputText, term);
            assertions.add(assertion(
                    "output_contains",
                    term,
                    present,
                    present ? "The final output contains the required term."
                            : "The final output does not contain the required term."));
        }
        String combinedResponses = executedToolResponses.stream()
                .map(ToolExecutionObservation::responseData)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);
        for (String term : expectation.requiredToolResponseTerms()) {
            boolean present = containsIgnoreCase(combinedResponses, term);
            assertions.add(assertion(
                    "tool_response_contains",
                    term,
                    present,
                    present ? "A tool response contains the required term."
                            : "No tool response contains the required term."));
        }

        List<String> toolErrors = executedToolResponses.stream()
                .filter(response -> FailureBenchmarkTools.FAIL_TOOL_NAME.equals(response.name()))
                .map(ToolExecutionObservation::responseData)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        boolean contractPassed = assertions.stream().allMatch(ToolBenchmarkAssertion::passed);
        return new Assessment(searches, List.copyOf(assertions), toolErrors, contractPassed);
    }

    private List<ToolSearchObservation> toolSearchObservations(
            List<ToolCallObservation> selectedToolCalls,
            List<ToolExecutionObservation> executedToolResponses) {
        Map<String, ToolExecutionObservation> responsesById = new LinkedHashMap<>();
        for (ToolExecutionObservation response : executedToolResponses) {
            if (TOOL_SEARCH_TOOL_NAME.equals(response.name())) {
                responsesById.put(response.id(), response);
            }
        }

        List<ToolSearchObservation> observations = new ArrayList<>();
        Set<String> observedIds = new LinkedHashSet<>();
        for (ToolCallObservation call : selectedToolCalls) {
            if (!TOOL_SEARCH_TOOL_NAME.equals(call.name())) {
                continue;
            }
            ToolExecutionObservation response = responsesById.get(call.id());
            observations.add(new ToolSearchObservation(
                    call.id(),
                    parseQuery(call.arguments()),
                    response != null,
                    response == null ? List.of() : parseDiscoveredTools(response.responseData())
            ));
            observedIds.add(call.id());
        }
        for (ToolExecutionObservation response : responsesById.values()) {
            if (!observedIds.contains(response.id())) {
                observations.add(new ToolSearchObservation(
                        response.id(), null, true, parseDiscoveredTools(response.responseData())));
            }
        }
        return List.copyOf(observations);
    }

    private String parseQuery(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(arguments);
            JsonNode query = root.get("query");
            if (query == null) {
                query = root.get("arg0");
            }
            if (query != null && query.isValueNode()) {
                return query.asText();
            }
            for (Map.Entry<String, JsonNode> field : root.properties()) {
                JsonNode value = field.getValue();
                if (value.isValueNode()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private List<String> parseDiscoveredTools(String responseData) {
        if (responseData == null || responseData.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseData);
            if (root.isArray()) {
                List<String> names = new ArrayList<>();
                for (JsonNode value : root) {
                    if (value.isValueNode() && !value.asText().isBlank()) {
                        names.add(value.asText());
                    }
                }
                return List.copyOf(names);
            }
            if (root.isTextual() && !root.asText().isBlank()) {
                return List.of(root.asText());
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private ToolBenchmarkAssertion assertion(String check, String target, boolean passed, String detail) {
        return new ToolBenchmarkAssertion(check, target, passed, detail);
    }

    private boolean containsIgnoreCase(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    record Assessment(
            List<ToolSearchObservation> toolSearchObservations,
            List<ToolBenchmarkAssertion> assertions,
            List<String> toolErrors,
            boolean contractPassed
    ) {}
}
