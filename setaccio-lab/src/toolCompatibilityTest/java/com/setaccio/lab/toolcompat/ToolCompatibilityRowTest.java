package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityRowTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "a".repeat(64));

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void projectsTheAuthoritativeMultiTurnLifecycleIntoOneCanonicalRow() {
        ToolCompatibilityRow row = successfulMultiStepRow();

        assertThat(row.sequence()).isEqualTo(5);
        assertThat(row.providerTurns()).hasSize(3);
        assertThat(row.toolCalls()).hasSize(2);
        assertThat(row.toolResponses()).hasSize(2);
        assertThat(row.providerTurns())
                .extracting(ToolCompatibilityProviderTurnEvidence::assistantText)
                .containsExactly(null, "", "fixture-invoice-sample appears in the document catalog");
        assertThat(row.toolCalls())
                .extracting(
                        ToolCompatibilityToolCallEvidence::providerTurnSequence,
                        ToolCompatibilityToolCallEvidence::rawArgumentJsonState,
                        ToolCompatibilityToolCallEvidence::declaredSchemaState,
                        ToolCompatibilityToolCallEvidence::expectedArgumentsState,
                        ToolCompatibilityToolCallEvidence::callbackBindingState,
                        ToolCompatibilityToolCallEvidence::callbackExecutionState)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED),
                        org.assertj.core.groups.Tuple.tuple(
                                2,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED,
                                ToolCompatibilityEvidenceState.SUCCEEDED));
        assertThat(row.toolResponses())
                .extracting(ToolCompatibilityToolResponseEvidence::callbackResultState)
                .containsOnly(ToolCompatibilityEvidenceState.SUCCEEDED);
        assertThat(row.rowAttemptCompleted()).isTrue();
        assertThat(row.exactCallSequenceMatched()).isTrue();
        assertThat(row.allExpectedArgumentsMatched()).isTrue();
        assertThat(row.finalResponsePresent()).isTrue();
        assertThat(row.caseContractPassed()).isTrue();
        assertThat(row.finalAssistantOutput())
                .isEqualTo("fixture-invoice-sample appears in the document catalog");
        assertThat(row.anyProviderTurnReachedOutputLimit()).isTrue();
        assertThat(row.aggregateUsage()).isEqualTo(new ToolCompatibilityTokenUsageEvidence(
                ToolCompatibilityUsageAvailability.COMPLETE,
                59,
                524,
                583));
        assertThat(row.failure()).isNull();
        assertThat(row.diagnostic()).isNull();
        assertThat(row.assertions()).allMatch(assertion -> assertion.passed());
    }

    @Test
    void keepsNotReachedUnobservableAndFailedCallbackStagesDistinct() {
        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.CALLBACK_FAILURE,
                List.of(new ToolCompatibilityObservedProviderTurn(
                        1,
                        "",
                        List.of("call-unknown"),
                        "unknown-turn",
                        "fake-model",
                        Map.of("provider", "fake"),
                        "tool_calls",
                        3,
                        2,
                        5,
                        false,
                        10,
                        ToolCompatibilityProviderTurnState.COMPLETED,
                        null)),
                List.of(new ToolCompatibilityObservedToolCall(
                        1,
                        1,
                        "call-unknown",
                        "function",
                        "lab_unknown_tool",
                        "{}",
                        true,
                        null,
                        null,
                        null,
                        false,
                        false,
                        null,
                        false,
                        false,
                        null,
                        ToolCompatibilityCallbackFailureKind.CALLBACK_RESOLUTION_FAILURE,
                        "raw framework error is not canonical evidence")),
                "raw framework error is not canonical evidence",
                true,
                12);

        ToolCompatibilityRow row = new ToolCompatibilityRowAnalyzer().analyze(
                scheduled("no-applicable-domain-tool", 1), MODEL_IDENTITY, trace);

        assertThat(row.rowAttemptCompleted()).isFalse();
        assertThat(row.failureCategory())
                .isEqualTo(ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE);
        assertThat(row.safeErrorMessage()).isEqualTo("Model-selected tool could not be resolved");
        assertThat(row.safeErrorMessage()).doesNotContain("raw framework error");
        assertThat(row.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.rawArgumentJsonState()).isEqualTo(ToolCompatibilityEvidenceState.SUCCEEDED);
            assertThat(call.declaredSchemaState()).isEqualTo(ToolCompatibilityEvidenceState.UNOBSERVABLE);
            assertThat(call.expectedCallAtSequenceState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
            assertThat(call.expectedArgumentsState()).isEqualTo(ToolCompatibilityEvidenceState.NOT_REACHED);
            assertThat(call.callbackBindingState()).isEqualTo(ToolCompatibilityEvidenceState.NOT_REACHED);
            assertThat(call.callbackExecutionState()).isEqualTo(ToolCompatibilityEvidenceState.NOT_REACHED);
        });
        assertThat(row.toolResponses()).singleElement().satisfies(response -> {
            assertThat(response.callbackResultState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
            assertThat(response.failure().category())
                    .isEqualTo(ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE);
            assertThat(response.responseData()).isNull();
        });
    }

    @Test
    void roundTripsStrictJsonWithoutSingularMultiCallShortcuts() throws Exception {
        ToolCompatibilityRow row = successfulMultiStepRow();

        JsonNode json = objectMapper.valueToTree(row);
        ToolCompatibilityRow restored = strictRead(json, ToolCompatibilityRow.class);

        assertThat(restored).isEqualTo(row);
        assertThat(json.has("rawArgumentJson")).isFalse();
        assertThat(json.has("finishReason")).isFalse();
        assertThat(json.has("callbackResponse")).isFalse();
        assertThat(json.has("rawAssistantOutput")).isFalse();
        assertThat(json.has("failure")).isFalse();
        assertThat(json.has("diagnostic")).isFalse();
        assertThat(json.path("toolCalls").get(0).path("rawArgumentJson").asText())
                .isEqualTo("{\"itemId\":\"fixture-invoice-sample\"}");
        assertThat(json.path("toolResponses").get(0).path("responseData").asText())
                .isEqualTo("lookup response");
    }

    @Test
    void strictReadRejectsUnknownFieldsAndContradictoryAggregateState() {
        ObjectNode unknownField = objectMapper.valueToTree(successfulMultiStepRow());
        unknownField.put("unexpected", true);

        assertThatThrownBy(() -> strictRead(unknownField, ToolCompatibilityRow.class))
                .hasMessageContaining("unexpected");

        ObjectNode contradictory = objectMapper.valueToTree(successfulMultiStepRow());
        contradictory.put("exactCallSequenceMatched", false);
        assertThatThrownBy(() -> strictRead(contradictory, ToolCompatibilityRow.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deterministic projections");
    }

    @Test
    void constructorRejectsBrokenTurnCallAndResponseLinkage() {
        ObjectNode brokenTurn = objectMapper.valueToTree(successfulMultiStepRow());
        ((ObjectNode) brokenTurn.path("toolCalls").get(0)).put("providerTurnSequence", 2);
        assertThatThrownBy(() -> strictRead(brokenTurn, ToolCompatibilityRow.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool-call IDs");

        ObjectNode brokenResponse = objectMapper.valueToTree(successfulMultiStepRow());
        ((ObjectNode) brokenResponse.path("toolResponses").get(0)).put("callId", "wrong-call");
        assertThatThrownBy(() -> strictRead(brokenResponse, ToolCompatibilityRow.class))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkage");
    }

    @Test
    void mathematicalNumberScaleDoesNotCreateAnOracleMismatch() {
        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.COMPLETED,
                List.of(
                        turn(1, "", List.of("call-add"), 3, 2, 5, false, 5),
                        turn(2, "22", List.of(), 5, 2, 7, false, 5)),
                List.of(new ToolCompatibilityObservedToolCall(
                        1,
                        1,
                        "call-add",
                        "function",
                        "lab_add_numbers",
                        "{\"left\":17.250,\"right\":4.7500}",
                        true,
                        true,
                        null,
                        1,
                        true,
                        true,
                        true,
                        true,
                        true,
                        "22.0000",
                        null,
                        null)),
                null,
                true,
                12);

        ToolCompatibilityRow row = new ToolCompatibilityRowAnalyzer().analyze(
                scheduled("arithmetic-add", 1), MODEL_IDENTITY, trace);

        assertThat(row.exactCallSequenceMatched()).isTrue();
        assertThat(row.allExpectedArgumentsMatched()).isTrue();
        assertThat(row.caseContractPassed()).isTrue();
    }

    @Test
    void separatesBindingFailureFromAnExecutedCallbackFailure() {
        ToolCompatibilityInvocationTrace bindingTrace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.COMPLETED,
                List.of(
                        turn(1, "", List.of("call-add"), 3, 2, 5, false, 5),
                        turn(2, "The arguments could not be bound.", List.of(), 5, 3, 8, false, 5)),
                List.of(new ToolCompatibilityObservedToolCall(
                        1,
                        1,
                        "call-add",
                        "function",
                        "lab_add_numbers",
                        "{\"left\":\"not-a-number\",\"right\":4.75}",
                        true,
                        false,
                        ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH,
                        1,
                        true,
                        false,
                        false,
                        true,
                        false,
                        "binding failure response",
                        ToolCompatibilityCallbackFailureKind.CALLBACK_BINDING_FAILURE,
                        "raw binding failure")),
                null,
                true,
                12);
        ToolCompatibilityRow bindingRow = new ToolCompatibilityRowAnalyzer().analyze(
                scheduled("arithmetic-add", 1), MODEL_IDENTITY, bindingTrace);

        assertThat(bindingRow.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.callbackBindingState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
            assertThat(call.callbackExecutionState()).isEqualTo(ToolCompatibilityEvidenceState.NOT_REACHED);
        });
        assertThat(bindingRow.toolResponses()).singleElement().satisfies(response ->
                assertThat(response.failure().category())
                        .isEqualTo(ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE));
        assertThat(bindingRow.rowAttemptCompleted()).isTrue();
        assertThat(bindingRow.caseContractPassed()).isFalse();

        ToolCompatibilityInvocationTrace invocationTrace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.COMPLETED,
                List.of(
                        turn(1, "", List.of("call-fail"), 3, 2, 5, false, 5),
                        turn(2, "The fixture tool returned an error.", List.of(), 5, 3, 8, false, 5)),
                List.of(new ToolCompatibilityObservedToolCall(
                        1,
                        1,
                        "call-fail",
                        "function",
                        "lab_fail_fixture",
                        "{}",
                        true,
                        true,
                        null,
                        1,
                        true,
                        true,
                        true,
                        true,
                        false,
                        "fixture-tool-failure",
                        ToolCompatibilityCallbackFailureKind.CALLBACK_INVOCATION_FAILURE,
                        "raw invocation failure")),
                null,
                true,
                12);
        ToolCompatibilityRow invocationRow = new ToolCompatibilityRowAnalyzer().analyze(
                scheduled("deterministic-tool-failure", 1), MODEL_IDENTITY, invocationTrace);

        assertThat(invocationRow.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.callbackBindingState()).isEqualTo(ToolCompatibilityEvidenceState.SUCCEEDED);
            assertThat(call.callbackExecutionState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
        });
        assertThat(invocationRow.toolResponses()).singleElement().satisfies(response ->
                assertThat(response.failure().category())
                        .isEqualTo(ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE));
        assertThat(invocationRow.rowAttemptCompleted()).isTrue();
        assertThat(invocationRow.caseContractPassed()).isTrue();
    }

    private ToolCompatibilityRow strictRead(JsonNode json, Class<ToolCompatibilityRow> type) throws Exception {
        return objectMapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
    }

    private static ToolCompatibilityRow successfulMultiStepRow() {
        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.COMPLETED,
                List.of(
                        turn(1, null, List.of("call-lookup"), 11, 6, 17, false, 10),
                        turn(2, "", List.of("call-list"), 20, 512, 532, true, 20),
                        new ToolCompatibilityObservedProviderTurn(
                                3,
                                "fixture-invoice-sample appears in the document catalog",
                                List.of(),
                                "turn-3",
                                "fake-model",
                                Map.of("provider", "fake"),
                                "stop",
                                28,
                                6,
                                34,
                                false,
                                30,
                                ToolCompatibilityProviderTurnState.COMPLETED,
                                null)),
                List.of(
                        successfulCall(
                                1,
                                1,
                                "call-lookup",
                                "lab_lookup_catalog_item",
                                "{\"itemId\":\"fixture-invoice-sample\"}",
                                "lookup response"),
                        successfulCall(
                                2,
                                2,
                                "call-list",
                                "lab_list_catalog_items",
                                "{\"category\":\"document\"}",
                                "list response")),
                null,
                true,
                75);
        return new ToolCompatibilityRowAnalyzer().analyze(
                scheduled("catalog-multi-step", 1), MODEL_IDENTITY, trace);
    }

    private static ToolCompatibilityObservedProviderTurn turn(
            int sequence,
            String assistantText,
            List<String> callIds,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            boolean outputLimitReached,
            long latencyMillis
    ) {
        return new ToolCompatibilityObservedProviderTurn(
                sequence,
                assistantText,
                callIds,
                "turn-" + sequence,
                "fake-model",
                Map.of("provider", "fake"),
                callIds.isEmpty() ? "stop" : "tool_calls",
                promptTokens,
                completionTokens,
                totalTokens,
                outputLimitReached,
                latencyMillis,
                ToolCompatibilityProviderTurnState.COMPLETED,
                null);
    }

    private static ToolCompatibilityObservedToolCall successfulCall(
            int sequence,
            int providerTurnSequence,
            String callId,
            String toolName,
            String rawArguments,
            String response
    ) {
        return new ToolCompatibilityObservedToolCall(
                sequence,
                providerTurnSequence,
                callId,
                "function",
                toolName,
                rawArguments,
                true,
                true,
                null,
                sequence,
                true,
                true,
                true,
                true,
                true,
                response,
                null,
                null);
    }

    private static ToolCompatibilityCaseSelection.ScheduledCase scheduled(String caseId, int repetition) {
        return ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings())
                .stream()
                .filter(entry -> entry.caseId().equals(caseId) && entry.repetition() == repetition)
                .findFirst()
                .orElseThrow();
    }
}
