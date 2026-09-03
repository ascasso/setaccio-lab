package com.setaccio.lab.chatmatrix;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.ChatThinkingPresence;
import com.setaccio.lab.chat.ChatProviderOptionSupport;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reasoning capture is an in-memory dimension only. The Phase 2 chat matrix and the Phase 5
 * answer matrix keep their existing row schema, so evidence saved before the capture existed
 * still deserializes and still regenerates the same summary.
 */
class ChatMatrixReasoningCompatibilityTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Test
    void keepsTheSavedChatRowSchemaFreeOfEveryReasoningField() {
        JsonNode row = MAPPER.valueToTree(row(outcomeWithCapture()));

        for (Iterator<String> names = row.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            assertThat(name).isNotEqualTo("capture");
            assertThat(name).isNotEqualTo("thinking");
            assertThat(name).isNotEqualTo("thinkingPresence");
            assertThat(name).isNotEqualTo("finishReason");
            assertThat(name).isNotEqualTo("requestedReasoningPolicy");
        }
        assertThat(row.has("rawResponse")).isTrue();
        assertThat(row.get("rawResponse").asText()).isEqualTo("yes");
    }

    @Test
    void projectsTheSameRowWhetherOrNotAnOutcomeCarriesACapture() {
        JsonNode withCapture = MAPPER.valueToTree(row(outcomeWithCapture()));
        JsonNode withoutCapture = MAPPER.valueToTree(row(outcomeWithoutCapture()));
        assertThat(withCapture.toString()).isEqualTo(withoutCapture.toString());
    }

    @Test
    void stillReadsAChatRowSerializedBeforeTheCaptureExisted() throws Exception {
        String legacyJson = MAPPER.writeValueAsString(row(outcomeWithoutCapture()));

        ChatMatrixRow parsed = MAPPER.readerFor(ChatMatrixRow.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(legacyJson);

        assertThat(parsed.rawResponse()).isEqualTo("yes");
        assertThat(parsed.failureCategory()).isEqualTo(ChatInvocationFailureCategory.NONE);
    }

    @Test
    void leavesTheProviderOptionVocabularyUnchangedSoOldEvidenceStillClassifiesEveryOption() {
        assertThat(List.of(com.setaccio.lab.chat.ChatGenerationOption.values()))
                .containsExactly(
                        com.setaccio.lab.chat.ChatGenerationOption.TEMPERATURE,
                        com.setaccio.lab.chat.ChatGenerationOption.SEED,
                        com.setaccio.lab.chat.ChatGenerationOption.MAX_OUTPUT_TOKENS);
    }

    private static ChatMatrixRow row(ChatInvocationOutcome outcome) {
        return ChatMatrixRow.from(
                new ChatMatrixScheduleEntry(1, 1, 42, "concise-summary", "c".repeat(64)),
                settings(),
                identity(),
                outcome);
    }

    private static ChatInvocationOutcome outcomeWithoutCapture() {
        return new ChatInvocationOutcome(
                identity(), ChatProviderOptionSupport.supportsAll(), "concise-summary", true,
                "yes", null, 11, 1, 12, 5L, 1, ChatInvocationFailureCategory.NONE, null);
    }

    private static ChatInvocationOutcome outcomeWithCapture() {
        return new ChatInvocationOutcome(
                identity(), ChatProviderOptionSupport.supportsAll(), "concise-summary", true,
                "yes", null, 11, 1, 12, 5L, 1, ChatInvocationFailureCategory.NONE, null,
                new ChatResponseCapture("yes", "a reasoning trace", ChatThinkingPresence.PRESENT,
                        "stop", 1, ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED));
    }

    private static ChatGenerationSettings settings() {
        return new ChatGenerationSettings(0.0, 42, 128, Duration.ofMinutes(2), 1);
    }

    private static OllamaChatModelIdentity identity() {
        return new OllamaChatModelIdentity("ollama", "gemma:test", "gemma:test", "d".repeat(64));
    }
}
