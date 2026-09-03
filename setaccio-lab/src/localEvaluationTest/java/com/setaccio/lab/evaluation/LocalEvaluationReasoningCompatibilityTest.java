package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.ChatThinkingPresence;
import java.time.Duration;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

/**
 * The Phase 4 fact-check row schema is unchanged by the reasoning capture, so its saved evidence
 * still deserializes and its manifest settings still regenerate identically.
 */
class LocalEvaluationReasoningCompatibilityTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Test
    void keepsTheSavedFactCheckRowSchemaFreeOfEveryReasoningField() {
        JsonNode row = MAPPER.valueToTree(row(judgeResult(capture())));

        for (Iterator<String> names = row.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            assertThat(name).isNotEqualTo("capture");
            assertThat(name).isNotEqualTo("thinking");
            assertThat(name).isNotEqualTo("thinkingPresence");
            assertThat(name).isNotEqualTo("finishReason");
        }
        assertThat(row.get("rawResponse").asText()).isEqualTo("yes");
        assertThat(row.get("judgeSettings").has("reasoningPolicy")).isFalse();
    }

    @Test
    void projectsTheSameRowWhetherOrNotAJudgeResultCarriesACapture() {
        assertThat(MAPPER.valueToTree(row(judgeResult(capture()))).toString())
                .isEqualTo(MAPPER.valueToTree(row(judgeResult(null))).toString());
    }

    @Test
    void stillReadsAFactCheckRowSerializedBeforeTheCaptureExisted() throws Exception {
        String legacyJson = MAPPER.writeValueAsString(row(judgeResult(null)));

        LocalEvaluationRow parsed = MAPPER.readerFor(LocalEvaluationRow.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(legacyJson);

        assertThat(parsed.rawResponse()).isEqualTo("yes");
        assertThat(parsed.diagnosticCategory()).isEqualTo(LocalFactCheckDiagnosticCategory.NONE);
    }

    private static LocalEvaluationRow row(LocalFactCheckJudgeResult result) {
        return LocalEvaluationRow.from(
                new LocalEvaluationScheduleEntry(
                        1, 1, 42, "harbor-library-supported", "harbor-library-hours",
                        "a".repeat(64), "b".repeat(64), LocalFactCheckExpectedVerdict.SUPPORTED),
                result);
    }

    private static LocalFactCheckJudgeResult judgeResult(ChatResponseCapture capture) {
        return new LocalFactCheckJudgeResult(
                "harbor-library-supported",
                LocalFactCheckExpectedVerdict.SUPPORTED,
                new LocalFactCheckJudgeSettings("judge:model", 0.0, 42, 64, Duration.ofMinutes(2), 1),
                true,
                true,
                LocalFactCheckJudgeVerdict.SUPPORTED,
                true,
                LocalFactCheckDiagnosticCategory.NONE,
                "yes",
                null,
                11,
                1,
                12,
                5L,
                1,
                null,
                capture);
    }

    private static ChatResponseCapture capture() {
        return new ChatResponseCapture(
                "yes", "a reasoning trace", ChatThinkingPresence.PRESENT, "stop", 1,
                ChatReasoningPolicy.ENABLED, ChatReasoningSupport.APPLIED);
    }
}
