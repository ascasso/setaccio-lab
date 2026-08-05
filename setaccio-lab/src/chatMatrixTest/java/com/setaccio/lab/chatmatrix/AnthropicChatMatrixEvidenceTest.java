package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChatMatrixEvidenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesVerifiesAndReanalyzesIgnoredRawEvidenceWithoutCopyingResponsesToSummary() throws Exception {
        Path run = EvidenceRunDirectory.createNamed(temporaryDirectory, "2026-08-05-anthropic-test");
        AnthropicChatMatrixEvidence evidence = new AnthropicChatMatrixEvidence(ChatMatrixTestFixtures.OBJECT_MAPPER);

        evidence.write(run, successfulResult(), new EvidenceCodeBaseline("clean", false));

        assertThat(evidence.verify(run).failures()).isEmpty();
        assertThat(Files.readString(run.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME)))
                .contains("unsupported; not simulated")
                .doesNotContain("private provider response");
        assertThat(Files.readString(run.resolve(AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME)))
                .doesNotContain("private provider response");
        assertThat(AnthropicChatMatrixProtocol.costEstimate(Instant.parse("2026-08-05T12:00:00Z")).estimatedUsd())
                .isEqualByComparingTo("0.00537600");
        Files.writeString(run.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME), "drift", StandardCharsets.UTF_8);
        assertThat(evidence.verify(run).valid()).isFalse();
        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(evidence.verify(run).failures()).isEmpty();
    }

    @Test
    void executorRetainsSixSequentialRowsIncludingFailuresWithoutReplacement() {
        ChatPromptCatalog catalog = ChatMatrixTestFixtures.CATALOG;
        ChatPortabilityRunSettings settings = AnthropicChatMatrixProtocol.settings(catalog);
        AnthropicChatModelIdentity identity = AnthropicChatMatrixProtocol.modelIdentity();
        List<Integer> calls = new ArrayList<>();
        AnthropicChatMatrixExecutor executor = new AnthropicChatMatrixExecutor();

        AnthropicChatMatrixResult result = executor.execute(new AnthropicChatMatrixExecutor.Prepared(
                catalog, settings, identity, AnthropicChatMatrixProtocol.costEstimate(Instant.parse("2026-08-05T12:00:00Z")),
                new BigDecimal("3"), (prompt, model, generation) -> {
                    calls.add(calls.size() + 1);
                    boolean failure = calls.size() == 2;
                    return new com.setaccio.lab.chat.ChatInvocationOutcome(
                            model, AnthropicChatMatrixProtocol.optionSupport(), prompt.id(), !failure,
                            null, null, null, null, null, 4, 1,
                            failure ? ChatInvocationFailureCategory.RATE_LIMIT : ChatInvocationFailureCategory.EMPTY_RESPONSE,
                            failure ? "Anthropic rate limit exceeded" : null);
                }));

        assertThat(calls).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.rows()).hasSize(6);
        assertThat(result.rows().get(1).failureCategory()).isEqualTo(ChatInvocationFailureCategory.RATE_LIMIT);
        assertThat(result.rows().get(2).sequence()).isEqualTo(3);
    }

    @Test
    void stopsImmediatelyWhenObservedUsageExceedsAuthorizedCostCeiling() {
        ChatPromptCatalog catalog = ChatMatrixTestFixtures.CATALOG;
        ChatPortabilityRunSettings settings = AnthropicChatMatrixProtocol.settings(catalog);
        ChatEstimatedCost rates = new ChatEstimatedCost(
                "USD", 0, 0, BigDecimal.ONE, BigDecimal.ONE,
                Instant.parse("2026-08-05T12:00:00Z"), AnthropicChatMatrixProtocol.OFFICIAL_PRICE_SOURCE);
        List<Integer> calls = new ArrayList<>();
        AnthropicChatMatrixExecutor executor = new AnthropicChatMatrixExecutor();

        assertThatThrownBy(() -> executor.execute(new AnthropicChatMatrixExecutor.Prepared(
                catalog, settings, AnthropicChatMatrixProtocol.modelIdentity(), rates,
                new BigDecimal("0.00000099"), (prompt, model, generation) -> {
                    calls.add(calls.size() + 1);
                    return new com.setaccio.lab.chat.ChatInvocationOutcome(
                            model, AnthropicChatMatrixProtocol.optionSupport(), prompt.id(), true,
                            "answer", "msg_" + calls.size(), 1, 0, 1, 4, 1,
                            ChatInvocationFailureCategory.NONE, null);
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Anthropic run stopped because observed usage exceeded the authorized cost ceiling");
        assertThat(calls).containsExactly(1);
    }

    @Test
    void comparesManifestSettingsIndependentOfMapOrderAndNumericNodeWidth() throws Exception {
        com.fasterxml.jackson.databind.JsonNode expected = ChatMatrixTestFixtures.OBJECT_MAPPER.readTree(
                "{\"count\":120000,\"nested\":{\"temperature\":0.0,\"repetitions\":2}}");
        com.fasterxml.jackson.databind.JsonNode actual = ChatMatrixTestFixtures.OBJECT_MAPPER.readTree(
                "{\"nested\":{\"repetitions\":2.0,\"temperature\":0},\"count\":120000.0}");

        assertThat(AnthropicChatMatrixEvidence.sameJsonValue(expected, actual)).isTrue();
    }

    private static AnthropicChatMatrixResult successfulResult() {
        ChatPromptCatalog catalog = ChatMatrixTestFixtures.CATALOG;
        ChatPortabilityRunSettings settings = AnthropicChatMatrixProtocol.settings(catalog);
        AnthropicChatModelIdentity identity = AnthropicChatMatrixProtocol.modelIdentity();
        List<AnthropicChatMatrixRow> rows = new ArrayList<>();
        for (int index = 0; index < AnthropicChatMatrixProtocol.ROW_COUNT; index++) {
            ChatPromptCase prompt = catalog.prompts().get(index % catalog.prompts().size());
            int repetition = index / catalog.prompts().size() + 1;
            ChatGenerationSettings generation = new ChatGenerationSettings(0.0, null, 128,
                    AnthropicChatMatrixProtocol.TIMEOUT, 1);
            rows.add(new AnthropicChatMatrixRow(
                    index + 1, repetition, prompt.id(), prompt.sha256(), generation, identity,
                    AnthropicChatMatrixProtocol.optionSupport(), true, "private provider response " + index,
                    "msg_" + index, 70, 4, 74, 10 + index, 1,
                    ChatInvocationFailureCategory.NONE, null));
        }
        Instant started = Instant.parse("2026-08-05T12:00:00Z");
        return new AnthropicChatMatrixResult(
                1, AnthropicChatMatrixProtocol.SUITE, "anthropic", "remote", started, started.plusSeconds(6),
                "sequential", settings, identity, AnthropicChatMatrixProtocol.costEstimate(started),
                new BigDecimal("3"), rows);
    }
}
