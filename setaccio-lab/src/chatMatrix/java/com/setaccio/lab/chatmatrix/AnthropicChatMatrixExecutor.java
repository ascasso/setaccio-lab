package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AnthropicChatMatrixExecutor {

    private final Clock clock;

    AnthropicChatMatrixExecutor() {
        this(Clock.systemUTC());
    }

    AnthropicChatMatrixExecutor(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    AnthropicChatMatrixResult execute(Prepared prepared) {
        Objects.requireNonNull(prepared, "prepared must not be null");
        List<AnthropicChatMatrixRow> rows = new ArrayList<>(AnthropicChatMatrixProtocol.ROW_COUNT);
        Instant startedAt = clock.instant();
        for (int index = 0; index < AnthropicChatMatrixProtocol.ROW_COUNT; index++) {
            if (observedCost(rows, prepared.costEstimate()).compareTo(prepared.maximumAuthorizedCostUsd()) > 0) {
                throw new IllegalStateException("Anthropic run stopped because observed usage exceeded the authorized cost ceiling");
            }
            int repetition = index / prepared.catalog().prompts().size() + 1;
            ChatPromptCase prompt = prepared.catalog().prompts().get(index % prepared.catalog().prompts().size());
            ChatGenerationSettings settings = new ChatGenerationSettings(
                    prepared.settings().temperature(), null, prepared.settings().maxOutputTokens(),
                    java.time.Duration.ofMillis(prepared.settings().timeoutMillis()), prepared.settings().maxAttempts());
            ChatInvocationOutcome outcome = prepared.session().invoke(prompt, prepared.modelIdentity(), settings);
            rows.add(AnthropicChatMatrixRow.from(index + 1, repetition, prompt, settings, prepared.modelIdentity(), outcome));
            if (observedCost(rows, prepared.costEstimate()).compareTo(prepared.maximumAuthorizedCostUsd()) > 0) {
                throw new IllegalStateException("Anthropic run stopped because observed usage exceeded the authorized cost ceiling");
            }
        }
        return new AnthropicChatMatrixResult(
                AnthropicChatMatrixProtocol.VERSION,
                AnthropicChatMatrixProtocol.SUITE,
                AnthropicChatMatrixProtocol.PROVIDER,
                AnthropicChatMatrixProtocol.ENDPOINT_CATEGORY,
                startedAt,
                clock.instant(),
                AnthropicChatMatrixProtocol.EXECUTION_STRATEGY,
                prepared.settings(),
                prepared.modelIdentity(),
                prepared.costEstimate(),
                prepared.maximumAuthorizedCostUsd(),
                rows);
    }

    static BigDecimal observedCost(List<AnthropicChatMatrixRow> rows, ChatEstimatedCost rates) {
        long inputTokens = 0;
        long outputTokens = 0;
        for (AnthropicChatMatrixRow row : rows) {
            if (row.promptTokens() != null) {
                inputTokens = Math.addExact(inputTokens, row.promptTokens());
                outputTokens = Math.addExact(outputTokens, row.completionTokens());
            }
        }
        return rates.estimateUsd(inputTokens, outputTokens);
    }

    record Prepared(
            ChatPromptCatalog catalog,
            ChatPortabilityRunSettings settings,
            AnthropicChatModelIdentity modelIdentity,
            ChatEstimatedCost costEstimate,
            BigDecimal maximumAuthorizedCostUsd,
            Session session
    ) {
        Prepared {
            Objects.requireNonNull(catalog, "catalog must not be null");
            Objects.requireNonNull(settings, "settings must not be null");
            Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
            Objects.requireNonNull(costEstimate, "costEstimate must not be null");
            Objects.requireNonNull(maximumAuthorizedCostUsd, "maximumAuthorizedCostUsd must not be null");
            Objects.requireNonNull(session, "session must not be null");
            if (maximumAuthorizedCostUsd.signum() <= 0
                    || costEstimate.estimatedUsd().compareTo(maximumAuthorizedCostUsd) > 0) {
                throw new IllegalArgumentException("authorized cost ceiling must cover the locked preflight estimate");
            }
        }
    }

    @FunctionalInterface
    interface Session {
        ChatInvocationOutcome invoke(
                ChatPromptCase prompt,
                AnthropicChatModelIdentity modelIdentity,
                ChatGenerationSettings settings);
    }
}
