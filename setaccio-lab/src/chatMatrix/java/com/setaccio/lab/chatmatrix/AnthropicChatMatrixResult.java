package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Ignored saved result for exactly one authorized Anthropic portability run. */
record AnthropicChatMatrixResult(
        int protocolVersion,
        String suite,
        String provider,
        String endpointCategory,
        Instant startedAt,
        Instant finishedAt,
        String executionStrategy,
        ChatPortabilityRunSettings runSettings,
        AnthropicChatModelIdentity requestedModelIdentity,
        ChatEstimatedCost preflightCostEstimate,
        BigDecimal maximumAuthorizedCostUsd,
        List<AnthropicChatMatrixRow> rows
) {
    AnthropicChatMatrixResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
