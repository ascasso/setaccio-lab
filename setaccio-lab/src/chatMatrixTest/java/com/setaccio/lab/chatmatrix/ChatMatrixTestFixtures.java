package com.setaccio.lab.chatmatrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ChatMatrixTestFixtures {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    static final ChatPromptCatalog CATALOG = ChatPromptCatalog.load(OBJECT_MAPPER);
    static final ChatMatrixRunSettings SETTINGS = ChatMatrixProtocol.settings(
            "model-a",
            128,
            Duration.ofSeconds(30));
    static final OllamaChatModelIdentity MODEL_IDENTITY = new OllamaChatModelIdentity(
            "ollama",
            "model-a",
            "model-a:latest",
            "a".repeat(64));

    private ChatMatrixTestFixtures() {}

    static ChatMatrixResult successfulResult() {
        List<ChatMatrixRow> rows = new ArrayList<>();
        for (ChatMatrixScheduleEntry entry : ChatMatrixProtocol.schedule(CATALOG)) {
            ChatInvocationOutcome outcome = new ChatInvocationOutcome(
                    MODEL_IDENTITY,
                    ChatProviderOptionSupport.supportsAll(),
                    entry.promptId(),
                    true,
                    "response-" + entry.sequence(),
                    10 + entry.sequence(),
                    2,
                    12 + entry.sequence(),
                    entry.sequence() * 10L,
                    1,
                    ChatInvocationFailureCategory.NONE,
                    null);
            rows.add(ChatMatrixRow.from(
                    entry,
                    SETTINGS.generationSettingsFor(entry.repetition()),
                    MODEL_IDENTITY,
                    outcome));
        }
        return resultWithRows(rows);
    }

    static ChatMatrixResult diagnosticResult() {
        ChatMatrixResult source = successfulResult();
        List<ChatMatrixRow> rows = new ArrayList<>(source.rows());
        ChatMatrixScheduleEntry emptyEntry = source.orderedSchedule().get(0);
        rows.set(0, ChatMatrixRow.from(
                emptyEntry,
                SETTINGS.generationSettingsFor(emptyEntry.repetition()),
                MODEL_IDENTITY,
                new ChatInvocationOutcome(
                        MODEL_IDENTITY,
                        ChatProviderOptionSupport.supportsAll(),
                        emptyEntry.promptId(),
                        true,
                        null,
                        null,
                        null,
                        null,
                        5,
                        1,
                        ChatInvocationFailureCategory.EMPTY_RESPONSE,
                        null)));
        ChatMatrixScheduleEntry timeoutEntry = source.orderedSchedule().get(1);
        rows.set(1, ChatMatrixRow.from(
                timeoutEntry,
                SETTINGS.generationSettingsFor(timeoutEntry.repetition()),
                MODEL_IDENTITY,
                new ChatInvocationOutcome(
                        MODEL_IDENTITY,
                        ChatProviderOptionSupport.supportsAll(),
                        timeoutEntry.promptId(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        30,
                        1,
                        ChatInvocationFailureCategory.TIMEOUT,
                        "socket timed out")));
        return resultWithRows(rows);
    }

    static ChatMatrixResult resultWithRows(List<ChatMatrixRow> rows) {
        Instant started = Instant.parse("2026-08-04T12:00:00Z");
        return ChatMatrixProtocol.result(
                started,
                started.plusSeconds(6),
                SETTINGS,
                MODEL_IDENTITY,
                CATALOG,
                rows);
    }

    static ChatMatrixResult copy(
            ChatMatrixResult source,
            ChatMatrixRunSettings settings,
            OllamaChatModelIdentity modelIdentity,
            String catalogSha256,
            List<ChatMatrixScheduleEntry> schedule,
            List<ChatMatrixRow> rows
    ) {
        return new ChatMatrixResult(
                source.protocolVersion(),
                source.suite(),
                source.provider(),
                source.endpointCategory(),
                source.startedAt(),
                source.finishedAt(),
                source.executionStrategy(),
                source.pullModelStrategy(),
                settings,
                modelIdentity,
                source.promptCatalogId(),
                source.promptCatalogVersion(),
                catalogSha256,
                source.orderedPromptIdentities(),
                schedule,
                rows);
    }
}
