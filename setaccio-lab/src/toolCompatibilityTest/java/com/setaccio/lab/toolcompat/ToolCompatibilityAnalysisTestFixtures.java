package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;

final class ToolCompatibilityAnalysisTestFixtures {

    static final ToolCompatibilityModelIdentity MODEL_IDENTITY = new ToolCompatibilityModelIdentity(
            ToolCompatibilityProtocol.INITIAL_MODEL,
            ToolCompatibilityProtocol.INITIAL_MODEL,
            "c".repeat(64));

    private static final Instant STARTED = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-18T11:00:00Z");

    private ToolCompatibilityAnalysisTestFixtures() {}

    static ToolCompatibilityResult successfulResult() {
        List<ToolCompatibilityRow> rows = ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings())
                .stream()
                .map(entry -> successfulRow(entry, entry.sequence() * 10L))
                .toList();
        return result(rows);
    }

    static ToolCompatibilityResult result(List<ToolCompatibilityRow> rows) {
        return ToolCompatibilityResult.create(STARTED, FINISHED, MODEL_IDENTITY, rows);
    }

    static ToolCompatibilityRow successfulRow(
            ToolCompatibilityCaseSelection.ScheduledCase scheduled,
            long latencyMillis
    ) {
        List<CallSpec> calls = ToolCompatibilityProtocol.caseOracle()
                .requireCase(scheduled.caseId())
                .calls()
                .stream()
                .map(call -> new CallSpec(call.toolName(), call.arguments().toString()))
                .toList();
        List<String> assistantTexts = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            assistantTexts.add(scheduled.sequence() == 1 && index == 0
                    ? "Thinking... selecting the required tool"
                    : "");
        }
        String finalText = finalText(scheduled.caseId());
        if (scheduled.sequence() == 2) {
            finalText = "<think>checking</think>\n" + finalText;
        } else if (scheduled.sequence() == 7) {
            finalText = "Here's a thinking process: " + finalText;
        }
        assistantTexts.add(finalText);
        return row(scheduled, calls, assistantTexts, latencyMillis);
    }

    static ToolCompatibilityRow providerFailureRow(
            ToolCompatibilityCaseSelection.ScheduledCase scheduled,
            long latencyMillis
    ) {
        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.PROVIDER_FAILURE,
                List.of(new ToolCompatibilityObservedProviderTurn(
                        1,
                        null,
                        List.of(),
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        1,
                        ToolCompatibilityProviderTurnState.PROVIDER_FAILURE,
                        "synthetic provider detail")),
                List.of(),
                "synthetic provider detail",
                true,
                latencyMillis);
        return new ToolCompatibilityRowAnalyzer().analyze(scheduled, MODEL_IDENTITY, trace);
    }

    static ToolCompatibilityRow timeoutRow(
            ToolCompatibilityCaseSelection.ScheduledCase scheduled,
            long latencyMillis
    ) {
        return new ToolCompatibilityRowAnalyzer().analyze(
                scheduled,
                MODEL_IDENTITY,
                new ToolCompatibilityInvocationTrace(
                        ToolCompatibilityInvocationStatus.ROW_TIMEOUT,
                        List.of(),
                        List.of(),
                        "synthetic timeout detail",
                        true,
                        latencyMillis));
    }

    static ToolCompatibilityRow row(
            ToolCompatibilityCaseSelection.ScheduledCase scheduled,
            List<CallSpec> calls,
            List<String> assistantTexts,
            long latencyMillis
    ) {
        if (assistantTexts.size() != calls.size() + 1) {
            throw new IllegalArgumentException("assistant text fixtures must cover every turn");
        }
        List<ToolCompatibilityObservedProviderTurn> turns = new ArrayList<>();
        List<ToolCompatibilityObservedToolCall> observedCalls = new ArrayList<>();
        ToolCompatibilityCaseOracle.CaseExpectation expectation =
                ToolCompatibilityProtocol.caseOracle().requireCase(scheduled.caseId());
        Map<String, ToolCallback> callbacks = callbacks();
        ToolCompatibilitySchemaValidator validator = new ToolCompatibilitySchemaValidator();
        for (int index = 0; index < calls.size(); index++) {
            int sequence = index + 1;
            String callId = "row-" + scheduled.sequence() + "-call-" + sequence;
            CallSpec call = calls.get(index);
            ToolCallback callback = callbacks.get(call.toolName());
            ToolCompatibilitySchemaValidator.RawArgumentValidation validation = callback == null
                    ? validator.validateJsonOnly(call.rawArguments())
                    : validator.validate(callback.getToolDefinition(), call.rawArguments());
            ToolCompatibilityExpectedCall expected = sequence <= expectation.calls().size()
                    ? expectation.calls().get(sequence - 1)
                    : null;
            boolean deterministicFailure = "lab_fail_fixture".equals(call.toolName());
            String callbackResponse = deterministicFailure
                    ? "fixture-tool-failure"
                    : callbackResponse(scheduled.caseId());
            observedCalls.add(new ToolCompatibilityObservedToolCall(
                    sequence,
                    sequence,
                    callId,
                    "function",
                    call.toolName(),
                    call.rawArguments(),
                    validation.jsonValid(),
                    validation.schemaValid(),
                    validation.issue(),
                    expected == null ? null : sequence,
                    expected != null && expected.toolName().equals(call.toolName()),
                    expected != null
                            && validation.parsedArguments() != null
                            && ToolCompatibilityJsonSemantics.equals(
                                    expected.arguments(), validation.parsedArguments()),
                    true,
                    true,
                    !deterministicFailure,
                    callbackResponse,
                    deterministicFailure
                            ? ToolCompatibilityCallbackFailureKind.CALLBACK_INVOCATION_FAILURE
                            : null,
                    deterministicFailure ? "synthetic deterministic failure" : null));
            turns.add(turn(
                    scheduled.sequence(),
                    sequence,
                    assistantTexts.get(index),
                    List.of(callId)));
        }
        turns.add(turn(
                scheduled.sequence(),
                calls.size() + 1,
                assistantTexts.getLast(),
                List.of()));
        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationTrace(
                ToolCompatibilityInvocationStatus.COMPLETED,
                turns,
                observedCalls,
                null,
                true,
                latencyMillis);
        return new ToolCompatibilityRowAnalyzer().analyze(scheduled, MODEL_IDENTITY, trace);
    }

    static List<ToolCompatibilityCaseSelection.ScheduledCase> schedule() {
        return ToolCompatibilityProtocol.schedule(
                ToolCompatibilityProtocol.caseSelection(),
                ToolCompatibilityProtocol.runSettings());
    }

    static String finalText(String caseId) {
        return switch (caseId) {
            case "arithmetic-add" -> "22";
            case "fixed-utc-time" -> "2026-01-15T12:00:00Z";
            case "fixed-zone-time" -> "America/Los_Angeles";
            case "catalog-lookup" -> "Policy FAQ";
            case "catalog-multi-step" -> "fixture-invoice-sample appears in the document catalog";
            case "catalog-no-match" -> "The deterministic lookup completed.";
            case "no-applicable-domain-tool" -> "BENCHMARK_NO_TOOL";
            case "deterministic-tool-failure" -> "The tool returned an error.";
            default -> throw new IllegalArgumentException("unknown case: " + caseId);
        };
    }

    private static ToolCompatibilityObservedProviderTurn turn(
            int rowSequence,
            int turnSequence,
            String assistantText,
            List<String> callIds
    ) {
        UsageFixture usage = usage(rowSequence, turnSequence);
        return new ToolCompatibilityObservedProviderTurn(
                turnSequence,
                assistantText,
                callIds,
                "row-" + rowSequence + "-turn-" + turnSequence,
                "fake-model",
                Map.of("provider", "fake"),
                callIds.isEmpty() ? "stop" : "tool_calls",
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.outputLimitReached(),
                1,
                ToolCompatibilityProviderTurnState.COMPLETED,
                null);
    }

    private static UsageFixture usage(int rowSequence, int turnSequence) {
        if (rowSequence == 1 && turnSequence == 1) {
            return new UsageFixture(3, null, null, null);
        }
        if (rowSequence == 2 && turnSequence == 1) {
            return new UsageFixture(null, null, null, null);
        }
        if (rowSequence == 5 && turnSequence == 2) {
            return new UsageFixture(8, 512, 520, true);
        }
        return new UsageFixture(4, 2, 6, false);
    }

    private static String callbackResponse(String caseId) {
        return switch (caseId) {
            case "catalog-no-match" -> "No catalog fixture matched";
            case "arithmetic-add" -> "22.00";
            default -> "synthetic callback response";
        };
    }

    private static Map<String, ToolCallback> callbacks() {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        ToolCompatibilityCallbackCatalog.canonicalCallbacks().forEach(callback ->
                callbacks.put(callback.getToolDefinition().name(), callback));
        return callbacks;
    }

    record CallSpec(String toolName, String rawArguments) {

        CallSpec {
            if (toolName == null || rawArguments == null) {
                throw new IllegalArgumentException("call fixture fields are required");
            }
        }
    }

    private record UsageFixture(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Boolean outputLimitReached
    ) {}
}
