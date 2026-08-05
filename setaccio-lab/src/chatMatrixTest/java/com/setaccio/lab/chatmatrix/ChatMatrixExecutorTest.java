package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMatrixExecutorTest {

    @Test
    void executesExactlySixRowsSequentiallyInLockedOrder() {
        List<String> calls = new ArrayList<>();
        ChatMatrixPreflight.Session session = session((prompt, settings, sequence) -> {
            calls.add(sequence + ":" + prompt.id() + ":" + settings.seed());
            return success(prompt.id(), sequence);
        });

        ChatMatrixResult result = executor().execute(prepared(session));

        assertThat(calls).containsExactly(
                "1:concise-summary:42",
                "2:classification-policy:42",
                "3:json-shape:42",
                "4:concise-summary:43",
                "5:classification-policy:43",
                "6:json-shape:43");
        assertThat(result.rows()).hasSize(6);
        assertThat(result.rows()).extracting(ChatMatrixRow::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.attemptCount()).isEqualTo(1);
            assertThat(row.generationSettings().temperature()).isEqualTo(0.0);
            assertThat(row.generationSettings().maxOutputTokens()).isEqualTo(128);
        });
    }

    @Test
    void retainsAClassifiedFailedRowAndContinuesWithoutRetryOrReplacement() {
        ChatMatrixPreflight.Session session = session((prompt, settings, sequence) -> sequence == 2
                ? new ChatInvocationOutcome(
                        ChatMatrixTestFixtures.MODEL_IDENTITY,
                        ChatProviderOptionSupport.supportsAll(),
                        prompt.id(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        20,
                        1,
                        ChatInvocationFailureCategory.PROVIDER_FAILURE,
                        "provider details")
                : success(prompt.id(), sequence));

        ChatMatrixResult result = executor().execute(prepared(session));

        assertThat(result.rows()).hasSize(6);
        assertThat(result.rows().get(1).failureCategory())
                .isEqualTo(ChatInvocationFailureCategory.PROVIDER_FAILURE);
        assertThat(result.rows().get(1).error()).isEqualTo("Ollama chat provider invocation failed");
        assertThat(result.rows().get(5).sequence()).isEqualTo(6);
    }

    private static ChatMatrixExecutor executor() {
        return new ChatMatrixExecutor(Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ChatMatrixPreflight.Prepared prepared(ChatMatrixPreflight.Session session) {
        return new ChatMatrixPreflight.Prepared(
                Path.of("build/chat-matrix/2026-08-04-test"),
                ChatMatrixTestFixtures.SETTINGS,
                ChatMatrixTestFixtures.MODEL_IDENTITY,
                ChatMatrixTestFixtures.CATALOG,
                session);
    }

    private static ChatMatrixPreflight.Session session(Invocation invocation) {
        AtomicInteger sequence = new AtomicInteger();
        return new ChatMatrixPreflight.Session() {
            @Override
            public OllamaChatModelIdentity requireInstalled(String requestedModel) {
                return ChatMatrixTestFixtures.MODEL_IDENTITY;
            }

            @Override
            public ChatInvocationOutcome invoke(
                    ChatPromptCase prompt,
                    OllamaChatModelIdentity modelIdentity,
                    ChatGenerationSettings settings
            ) {
                return invocation.invoke(prompt, settings, sequence.incrementAndGet());
            }
        };
    }

    private static ChatInvocationOutcome success(String promptId, int sequence) {
        return new ChatInvocationOutcome(
                ChatMatrixTestFixtures.MODEL_IDENTITY,
                ChatProviderOptionSupport.supportsAll(),
                promptId,
                true,
                "response-" + sequence,
                10,
                2,
                12,
                10,
                1,
                ChatInvocationFailureCategory.NONE,
                null);
    }

    @FunctionalInterface
    private interface Invocation {
        ChatInvocationOutcome invoke(ChatPromptCase prompt, ChatGenerationSettings settings, int sequence);
    }
}
