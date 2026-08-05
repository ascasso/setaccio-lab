package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatMatrixProtocol {

    static final int VERSION = 1;
    static final String SUITE = "ollama-chat-matrix";
    static final String PROVIDER = "ollama";
    static final String ENDPOINT_CATEGORY = "local";
    static final String EXECUTION_ENGINE = "spring-ai-chat-invocation";
    static final String EXECUTION_STRATEGY = "sequential";
    static final String PULL_MODEL_STRATEGY = "never";
    static final int REPETITIONS = 2;
    static final double TEMPERATURE = 0.0;
    static final List<Integer> SEEDS = List.of(42, 43);
    static final int MAX_ATTEMPTS = 1;
    static final int ROW_COUNT = 6;
    static final String RAW_FILENAME = "chat-matrix-results.json";

    private ChatMatrixProtocol() {}

    static ChatMatrixRunSettings settings(String requestedModel, int maxOutputTokens, Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        return new ChatMatrixRunSettings(
                requestedModel,
                REPETITIONS,
                TEMPERATURE,
                SEEDS,
                maxOutputTokens,
                timeout.toMillis(),
                MAX_ATTEMPTS);
    }

    static List<ChatMatrixScheduleEntry> schedule(ChatPromptCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        catalog.requireLocked();
        List<ChatMatrixScheduleEntry> schedule = new ArrayList<>(ROW_COUNT);
        int sequence = 1;
        for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
            for (ChatPromptCase prompt : catalog.prompts()) {
                schedule.add(new ChatMatrixScheduleEntry(
                        sequence++,
                        repetition,
                        SEEDS.get(repetition - 1),
                        prompt.id(),
                        prompt.sha256()));
            }
        }
        if (schedule.size() != ROW_COUNT) {
            throw new IllegalStateException("Chat matrix schedule must contain exactly six rows");
        }
        return List.copyOf(schedule);
    }

    static ChatMatrixResult result(
            Instant startedAt,
            Instant finishedAt,
            ChatMatrixRunSettings settings,
            OllamaChatModelIdentity modelIdentity,
            ChatPromptCatalog catalog,
            List<ChatMatrixRow> rows
    ) {
        return new ChatMatrixResult(
                VERSION,
                SUITE,
                PROVIDER,
                ENDPOINT_CATEGORY,
                startedAt,
                finishedAt,
                EXECUTION_STRATEGY,
                PULL_MODEL_STRATEGY,
                settings,
                modelIdentity,
                catalog.id(),
                catalog.version(),
                catalog.sha256(),
                catalog.identities(),
                schedule(catalog),
                rows);
    }

    static String normalizeModelTag(String model) {
        String normalized = model.trim();
        return normalized.contains(":") ? normalized : normalized + ":latest";
    }

    static Map<String, Object> manifestSettings(ChatMatrixResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("endpointCategory", result.endpointCategory());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("modelIdentity", result.modelIdentity());
        settings.put("promptCatalogId", result.promptCatalogId());
        settings.put("promptCatalogVersion", result.promptCatalogVersion());
        settings.put("promptCatalogSha256", result.promptCatalogSha256());
        settings.put("orderedPromptIdentities", result.orderedPromptIdentities());
        settings.put("orderedSchedule", result.orderedSchedule());
        return Collections.unmodifiableMap(settings);
    }
}
