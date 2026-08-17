package com.setaccio.lab.toolcompat;

import java.util.List;
import java.util.Map;

/**
 * Temporary, in-memory output of the T1.3 observability proof. It is deliberately
 * not a final evidence row or serialized result; T1.4 fixes those authoritative types.
 */
record ToolCompatibilityInvocationTrace(
        ToolCompatibilityInvocationStatus status,
        List<ToolCompatibilityObservedProviderTurn> providerTurns,
        List<ToolCompatibilityObservedToolCall> toolCalls,
        String terminalMessage,
        boolean safeForNextSequentialAttempt
) {
    ToolCompatibilityInvocationTrace {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        providerTurns = List.copyOf(providerTurns == null ? List.of() : providerTurns);
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        for (int index = 0; index < providerTurns.size(); index++) {
            if (providerTurns.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException("Provider turns must have contiguous one-based sequences");
            }
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            ToolCompatibilityObservedToolCall toolCall = toolCalls.get(index);
            if (toolCall.globalSequence() != index + 1
                    || toolCall.providerTurnSequence() < 1
                    || toolCall.providerTurnSequence() > providerTurns.size()) {
                throw new IllegalArgumentException("Tool calls must link to a provider turn with contiguous sequences");
            }
        }
    }
}

record ToolCompatibilityObservedProviderTurn(
        int sequence,
        String assistantText,
        List<String> orderedToolCallIds,
        String responseId,
        String responseModel,
        Map<String, Object> responseMetadata,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Boolean outputTokenLimitReached,
        long latencyMillis,
        ToolCompatibilityProviderTurnState state,
        String providerFailure
) {
    ToolCompatibilityObservedProviderTurn {
        if (sequence < 1 || latencyMillis < 0 || state == null) {
            throw new IllegalArgumentException("Provider turn observation has invalid required state");
        }
        orderedToolCallIds = List.copyOf(orderedToolCallIds == null ? List.of() : orderedToolCallIds);
        responseMetadata = Map.copyOf(responseMetadata == null ? Map.of() : responseMetadata);
    }
}

record ToolCompatibilityObservedToolCall(
        int globalSequence,
        int providerTurnSequence,
        String callId,
        String type,
        String toolName,
        String rawArguments,
        boolean rawArgumentJsonValid,
        Boolean rawArgumentSchemaValid,
        ToolCompatibilitySchemaIssue rawArgumentIssue,
        Integer expectedCallSequence,
        Boolean expectedCallAtSequence,
        Boolean expectedArgumentsMatched,
        Boolean callbackBindingSucceeded,
        boolean callbackExecuted,
        Boolean callbackSucceeded,
        String callbackResponse,
        ToolCompatibilityCallbackFailureKind callbackFailureKind,
        String callbackFailure
) {
    ToolCompatibilityObservedToolCall {
        if (globalSequence < 1 || providerTurnSequence < 1) {
            throw new IllegalArgumentException("Tool call observation sequences must be positive");
        }
    }
}
