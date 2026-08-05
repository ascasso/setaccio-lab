package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.time.Instant;
import java.util.List;

record ChatMatrixResult(
        int protocolVersion,
        String suite,
        String provider,
        String endpointCategory,
        Instant startedAt,
        Instant finishedAt,
        String executionStrategy,
        String pullModelStrategy,
        ChatMatrixRunSettings runSettings,
        OllamaChatModelIdentity modelIdentity,
        String promptCatalogId,
        String promptCatalogVersion,
        String promptCatalogSha256,
        List<ChatPromptIdentity> orderedPromptIdentities,
        List<ChatMatrixScheduleEntry> orderedSchedule,
        List<ChatMatrixRow> rows
) {
    ChatMatrixResult {
        orderedPromptIdentities = orderedPromptIdentities == null
                ? List.of()
                : List.copyOf(orderedPromptIdentities);
        orderedSchedule = orderedSchedule == null ? List.of() : List.copyOf(orderedSchedule);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
