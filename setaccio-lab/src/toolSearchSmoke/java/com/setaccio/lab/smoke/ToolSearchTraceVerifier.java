package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import com.setaccio.lab.model.ToolSearchObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSearchTraceVerifier {

    static final String TOOL_SEARCH_TOOL_NAME = "toolSearchTool";

    private final ObjectMapper objectMapper;

    ToolSearchTraceVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Verification verify(ToolBenchmarkRow row) {
        String rowId = row.model() + "/" + row.promptId() + "/r" + row.repetition();
        List<String> failures = new ArrayList<>();
        List<ToolCallObservation> calls = safe(row.selectedToolCalls()).stream()
                .filter(call -> call != null && TOOL_SEARCH_TOOL_NAME.equals(call.name()))
                .toList();
        List<ToolExecutionObservation> responses = safe(row.executedToolResponses()).stream()
                .filter(response -> response != null && TOOL_SEARCH_TOOL_NAME.equals(response.name()))
                .toList();
        List<ToolSearchObservation> normalized = safe(row.toolSearchObservations());

        if (calls.isEmpty()) {
            if (!responses.isEmpty() || !normalized.isEmpty()) {
                failures.add(rowId + ": Tool Search response/normalization exists without a selected call.");
            }
            return new Verification(false, false, List.of(), false, List.copyOf(failures));
        }

        Map<String, ToolCallObservation> callsById = uniqueCalls(calls, rowId, failures);
        Map<String, ToolExecutionObservation> responsesById = uniqueResponses(responses, rowId, failures);
        Map<String, ToolSearchObservation> normalizedById = uniqueNormalized(normalized, rowId, failures);
        List<String> discovered = new ArrayList<>();
        boolean mismatch = false;
        boolean completed = false;

        for (Map.Entry<String, ToolCallObservation> entry : callsById.entrySet()) {
            String id = entry.getKey();
            ToolExecutionObservation response = responsesById.get(id);
            ToolSearchObservation observation = normalizedById.get(id);
            if (response == null || observation == null) {
                failures.add(rowId + ": missing Tool Search trace linkage for call ID " + id + ".");
                continue;
            }
            String rawQuery = parseQuery(entry.getValue().arguments());
            if (rawQuery == null) {
                failures.add(rowId + ": malformed Tool Search call arguments for ID " + id + ".");
            } else if (!rawQuery.equals(observation.query())) {
                failures.add(rowId + ": raw and normalized Tool Search queries differ for ID " + id + ".");
            }
            List<String> rawTools = parseRawDiscoveredTools(response.responseData());
            if (rawTools == null) {
                failures.add(rowId + ": malformed Tool Search response wrapper for ID " + id + ".");
                continue;
            }
            if (!observation.completed()) {
                failures.add(rowId + ": linked normalized Tool Search observation is not completed for ID " + id + ".");
            } else {
                completed = true;
            }
            if (!rawTools.equals(observation.discoveredTools())) {
                mismatch = true;
                failures.add(rowId + ": discovery mismatch for ID " + id + " (raw="
                        + rawTools + ", normalized=" + observation.discoveredTools() + ").");
            }
            discovered.addAll(rawTools);
        }
        for (String id : responsesById.keySet()) {
            if (!callsById.containsKey(id)) {
                failures.add(rowId + ": orphaned Tool Search response ID " + id + ".");
            }
        }
        for (String id : normalizedById.keySet()) {
            if (!callsById.containsKey(id)) {
                failures.add(rowId + ": orphaned normalized Tool Search observation ID " + id + ".");
            }
        }
        return new Verification(true, completed, List.copyOf(discovered), mismatch, List.copyOf(failures));
    }

    private Map<String, ToolCallObservation> uniqueCalls(List<ToolCallObservation> values, String rowId,
                                                          List<String> failures) {
        Map<String, ToolCallObservation> byId = new LinkedHashMap<>();
        for (ToolCallObservation value : values) {
            String id = value.id();
            if (id == null || id.isBlank()) {
                failures.add(rowId + ": Tool Search call has a blank trace ID.");
            } else if (byId.putIfAbsent(id, value) != null) {
                failures.add(rowId + ": duplicate Tool Search call ID " + id + ".");
            }
        }
        return byId;
    }

    private Map<String, ToolExecutionObservation> uniqueResponses(List<ToolExecutionObservation> values,
                                                                   String rowId, List<String> failures) {
        Map<String, ToolExecutionObservation> byId = new LinkedHashMap<>();
        for (ToolExecutionObservation value : values) {
            String id = value.id();
            if (id == null || id.isBlank()) {
                failures.add(rowId + ": Tool Search response has a blank trace ID.");
            } else if (byId.putIfAbsent(id, value) != null) {
                failures.add(rowId + ": duplicate Tool Search response ID " + id + ".");
            }
        }
        return byId;
    }

    private Map<String, ToolSearchObservation> uniqueNormalized(List<ToolSearchObservation> values,
                                                                 String rowId, List<String> failures) {
        Map<String, ToolSearchObservation> byId = new LinkedHashMap<>();
        for (ToolSearchObservation value : values) {
            if (value == null || value.callId() == null || value.callId().isBlank()) {
                failures.add(rowId + ": normalized Tool Search observation has a blank trace ID.");
            } else if (byId.putIfAbsent(value.callId(), value) != null) {
                failures.add(rowId + ": duplicate normalized Tool Search observation ID " + value.callId() + ".");
            }
        }
        return byId;
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
            return query != null && query.isTextual() && !query.asText().isBlank() ? query.asText() : null;
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

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record Verification(boolean searchCalled, boolean searchCompleted, List<String> discoveredTools,
                        boolean discoveryMismatch, List<String> integrityFailures) {
        Set<String> discoveredToolSet() {
            return new LinkedHashSet<>(discoveredTools);
        }
    }
}
