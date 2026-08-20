package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ToolCompatibilityProviderTurnEvidence(
        int sequence,
        String assistantText,
        List<String> orderedToolCallIds,
        String responseId,
        String responseModel,
        Map<String, JsonNode> responseMetadata,
        String finishReason,
        ToolCompatibilityTokenUsageEvidence usage,
        ToolCompatibilityOutputLimitState outputLimitState,
        Duration latency,
        ToolCompatibilityEvidenceState invocationState,
        ToolCompatibilityFailure failure
) {

    ToolCompatibilityProviderTurnEvidence {
        if (sequence < 1) {
            throw new IllegalArgumentException("provider turn sequence must be positive");
        }
        orderedToolCallIds = List.copyOf(orderedToolCallIds == null ? List.of() : orderedToolCallIds);
        for (String callId : orderedToolCallIds) {
            requireText(callId, "provider turn tool-call ID");
        }
        if (new HashSet<>(orderedToolCallIds).size() != orderedToolCallIds.size()) {
            throw new IllegalArgumentException("provider turn tool-call IDs must be unique");
        }
        responseMetadata = immutableMetadata(responseMetadata);
        if (usage == null || outputLimitState == null || latency == null || invocationState == null) {
            throw new IllegalArgumentException("provider turn lifecycle evidence must be complete");
        }
        if (latency.isNegative()) {
            throw new IllegalArgumentException("provider turn latency must be non-negative");
        }
        if (invocationState != ToolCompatibilityEvidenceState.SUCCEEDED
                && invocationState != ToolCompatibilityEvidenceState.FAILED) {
            throw new IllegalArgumentException("an observed provider turn must have succeeded or failed");
        }
        if (invocationState == ToolCompatibilityEvidenceState.SUCCEEDED && failure != null) {
            throw new IllegalArgumentException("a successful provider turn must not contain a failure");
        }
        if (invocationState == ToolCompatibilityEvidenceState.FAILED) {
            if (failure == null || !ToolCompatibilityFailure.PROVIDER_FAILURE.equals(failure.category())) {
                throw new IllegalArgumentException("a failed provider turn requires a provider failure");
            }
            if (assistantText != null
                    || !orderedToolCallIds.isEmpty()
                    || responseId != null
                    || responseModel != null
                    || !responseMetadata.isEmpty()
                    || finishReason != null
                    || usage.availability() != ToolCompatibilityUsageAvailability.ABSENT
                    || outputLimitState != ToolCompatibilityOutputLimitState.UNOBSERVABLE) {
                throw new IllegalArgumentException(
                        "a failed provider turn must not invent unavailable response evidence");
            }
        }
        if (outputLimitState == ToolCompatibilityOutputLimitState.REACHED
                && (usage.completionTokens() == null
                        || usage.completionTokens()
                                < ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN)) {
            throw new IllegalArgumentException("limit-ended turn must reach the configured token limit");
        }
        if (outputLimitState == ToolCompatibilityOutputLimitState.NOT_REACHED
                && usage.completionTokens() != null
                && usage.completionTokens()
                        >= ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN) {
            throw new IllegalArgumentException("non-limit-ended turn contradicts its completion token count");
        }
        if (outputLimitState == ToolCompatibilityOutputLimitState.UNOBSERVABLE
                && usage.completionTokens() != null) {
            throw new IllegalArgumentException(
                    "output-limit state is observable when completion tokens are present");
        }
    }

    @Override
    public Map<String, JsonNode> responseMetadata() {
        return immutableMetadata(responseMetadata);
    }

    private static Map<String, JsonNode> immutableMetadata(Map<String, JsonNode> values) {
        Map<String, JsonNode> copy = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                requireText(key, "response metadata key");
                if (value == null) {
                    throw new IllegalArgumentException("response metadata values must not be null");
                }
                copy.put(key, value.deepCopy());
            });
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
