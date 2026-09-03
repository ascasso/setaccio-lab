package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import com.setaccio.lab.chat.ChatGenerationOption;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.evidence.EvidenceSuiteRoot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

final class AnthropicChatMatrixProtocol {

    static final int VERSION = 1;
    static final String SUITE = "anthropic-chat-matrix";
    static final EvidenceSuiteRoot EVIDENCE_ROOT = EvidenceSuiteRoot.of("anthropic-chat-matrix");
    static final String PROVIDER = "anthropic";
    static final String ENDPOINT_CATEGORY = "remote";
    static final String EXECUTION_STRATEGY = "sequential";
    static final String MODEL = "claude-haiku-4-5-20251001";
    static final int REPETITIONS = 2;
    static final int ROW_COUNT = 6;
    static final int MAX_OUTPUT_TOKENS = 128;
    static final Duration TIMEOUT = Duration.ofMinutes(2);
    static final int MAX_ATTEMPTS = 1;
    static final long INPUT_TOKEN_CEILING_PER_CALL = 256;
    static final String RAW_FILENAME = "anthropic-chat-matrix-results.json";
    static final String SNAPSHOT_FILENAME = "portability-snapshot.json";
    static final String SUMMARY_FILENAME = "SUMMARY.md";
    static final String OFFICIAL_PRICE_SOURCE = "https://platform.claude.com/docs/en/about-claude/pricing";
    static final BigDecimal INPUT_USD_PER_MILLION = BigDecimal.ONE;
    static final BigDecimal OUTPUT_USD_PER_MILLION = new BigDecimal("5");

    private AnthropicChatMatrixProtocol() {}

    static ChatProviderOptionSupport optionSupport() {
        return new ChatProviderOptionSupport(
                EnumSet.complementOf(EnumSet.of(ChatGenerationOption.SEED)),
                Map.of(ChatGenerationOption.SEED,
                        "Anthropic Messages API and Spring AI AnthropicChatOptions do not expose a seed option"));
    }

    static ChatPortabilityRunSettings settings(ChatPromptCatalog catalog) {
        return new ChatPortabilityRunSettings(
                catalog.id(), catalog.version(), catalog.sha256(), catalog.identities(), REPETITIONS, ROW_COUNT,
                0.0, MAX_OUTPUT_TOKENS, TIMEOUT.toMillis(), MAX_ATTEMPTS, List.of(), optionSupport());
    }

    static AnthropicChatModelIdentity modelIdentity() {
        return new AnthropicChatModelIdentity(AnthropicChatModelIdentity.ANTHROPIC_PROVIDER_ID, MODEL, MODEL, true);
    }

    static ChatEstimatedCost costEstimate(Instant priceCheckedAt) {
        return new ChatEstimatedCost(
                "USD", ROW_COUNT * INPUT_TOKEN_CEILING_PER_CALL, (long) ROW_COUNT * MAX_OUTPUT_TOKENS,
                INPUT_USD_PER_MILLION, OUTPUT_USD_PER_MILLION, priceCheckedAt, OFFICIAL_PRICE_SOURCE);
    }
}
