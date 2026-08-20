package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityResultTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "b".repeat(64));

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void roundTripsTheCompleteCanonicalResultWithStrictReads() throws Exception {
        ToolCompatibilityResult result = completeTimeoutResult();

        ToolCompatibilityResult restored = strictRead(objectMapper.valueToTree(result));

        assertThat(restored).isEqualTo(result);
        assertThat(restored.rows()).hasSize(16);
        assertThat(restored.orderedSchedule())
                .extracting(ToolCompatibilityCaseSelection.ScheduledCase::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        assertThat(restored.rows()).allMatch(row -> !row.rowAttemptCompleted());
        assertThat(restored.rows()).allMatch(row -> ToolCompatibilityFailure.ROW_TIMEOUT
                .equals(row.failureCategory()));
        assertThat(restored.toolDefinitionsSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void strictResultReadRejectsUnknownFieldsAndIncompleteRows() {
        ObjectNode unknown = objectMapper.valueToTree(completeTimeoutResult());
        unknown.put("endpoint", "must-not-be-recorded");
        assertThatThrownBy(() -> strictRead(unknown)).hasMessageContaining("endpoint");

        ObjectNode incomplete = objectMapper.valueToTree(completeTimeoutResult());
        ArrayNode rows = (ArrayNode) incomplete.path("rows");
        rows.remove(rows.size() - 1);
        assertThatThrownBy(() -> strictRead(incomplete))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete locked ordered schedule");
    }

    private ToolCompatibilityResult strictRead(JsonNode json) throws Exception {
        return objectMapper.readerFor(ToolCompatibilityResult.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
    }

    private static ToolCompatibilityResult completeTimeoutResult() {
        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule = ToolCompatibilityProtocol.schedule(
                ToolCompatibilityProtocol.caseSelection(),
                ToolCompatibilityProtocol.runSettings());
        ToolCompatibilityRowAnalyzer analyzer = new ToolCompatibilityRowAnalyzer();
        List<ToolCompatibilityRow> rows = schedule.stream()
                .map(entry -> analyzer.analyze(
                        entry,
                        MODEL_IDENTITY,
                        new ToolCompatibilityInvocationTrace(
                                ToolCompatibilityInvocationStatus.ROW_TIMEOUT,
                                List.of(),
                                List.of(),
                                "raw timeout detail",
                                true,
                                ToolCompatibilityProtocol.ROW_TIMEOUT.toMillis())))
                .toList();
        return ToolCompatibilityResult.create(
                Instant.parse("2026-08-18T08:00:00Z"),
                Instant.parse("2026-08-18T08:32:00Z"),
                MODEL_IDENTITY,
                rows);
    }
}
