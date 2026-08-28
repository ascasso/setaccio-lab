package com.setaccio.lab.retrieval;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Locked R5 answer-generation protocol metadata. */
final class RetrievalAnswerProtocol {

    static final int VERSION = 1;
    static final String SUITE = "public-safe-retrieval-answer";
    static final String PROVIDER = "ollama";
    static final String ENDPOINT_CATEGORY = "loopback-local";
    static final String EXECUTION_ENGINE = "spring-ai-provider-neutral-chat-invocation";
    static final String EXECUTION_STRATEGY = "sequential-one-answer-per-verified-r3-row";
    static final String PULL_MODEL_STRATEGY = "never";
    static final double TEMPERATURE = 0.0;
    static final int MAX_ATTEMPTS = 1;
    static final String RAW_FILENAME = "retrieval-answer-results.json";

    private RetrievalAnswerProtocol() {}

    static RetrievalAnswerRunSettings settings(int seed, int maxOutputTokens, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return new RetrievalAnswerRunSettings(
                PROVIDER,
                ENDPOINT_CATEGORY,
                TEMPERATURE,
                seed,
                maxOutputTokens,
                Math.toIntExact(timeout.toMillis()),
                MAX_ATTEMPTS,
                PULL_MODEL_STRATEGY);
    }

    static Map<String, Object> manifestSettings(RetrievalAnswerResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("sourceEvidence", result.sourceEvidence());
        settings.put("retrievalProtocolVersion", result.retrievalEvidence().protocolVersion());
        settings.put("retrievalCorpusCatalogSha256", result.retrievalEvidence().corpusCatalogSha256());
        settings.put("retrievalQueryCatalogSha256", result.retrievalEvidence().queryCatalogSha256());
        settings.put("prompt", result.prompt());
        settings.put("modelIdentity", result.modelIdentity());
        settings.put("runSettings", result.runSettings());
        settings.put("rowCount", result.rows().size());
        return Map.copyOf(settings);
    }
}
