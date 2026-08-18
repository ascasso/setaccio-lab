package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.model.ToolBenchmarkAssertion;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;

/**
 * Converts the proven T1.3 lifecycle into the canonical row and verifies every
 * row-level projection, including T1.5 visible-reasoning diagnostics.
 * Matrix-wide classification remains in T1.6.
 */
final class ToolCompatibilityRowAnalyzer {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    ToolCompatibilityRow analyze(
            ToolCompatibilityCaseSelection.ScheduledCase scheduledCase,
            ToolCompatibilityModelIdentity modelIdentity,
            ToolCompatibilityInvocationTrace trace
    ) {
        Objects.requireNonNull(scheduledCase, "scheduledCase must not be null");
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
        if (!trace.safeForNextSequentialAttempt()
                || trace.status() == ToolCompatibilityInvocationStatus.TIMEOUT_WORK_NOT_STOPPED) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Unsafe timed-out work cannot be reduced to a canonical result row",
                    trace);
        }

        List<ToolCompatibilityProviderTurnEvidence> providerTurns = trace.providerTurns().stream()
                .map(ToolCompatibilityRowAnalyzer::providerTurnEvidence)
                .toList();
        List<ToolCompatibilityToolCallEvidence> toolCalls = new ArrayList<>();
        List<ToolCompatibilityToolResponseEvidence> toolResponses = new ArrayList<>();
        for (ToolCompatibilityObservedToolCall observed : trace.toolCalls()) {
            Integer responseSequence = null;
            ToolCompatibilityToolResponseEvidence response = null;
            if (observed.callbackSucceeded() != null) {
                responseSequence = toolResponses.size() + 1;
                boolean succeeded = observed.callbackSucceeded();
                response = new ToolCompatibilityToolResponseEvidence(
                        responseSequence,
                        observed.globalSequence(),
                        observed.callId(),
                        observed.toolName(),
                        succeeded
                                ? ToolCompatibilityEvidenceState.SUCCEEDED
                                : ToolCompatibilityEvidenceState.FAILED,
                        observed.callbackResponse(),
                        succeeded ? null : ToolCompatibilityFailure.callback(observed.callbackFailureKind()));
            }
            toolCalls.add(toolCallEvidence(observed, responseSequence));
            if (response != null) {
                toolResponses.add(response);
            }
        }

        ToolCompatibilityFailure failure = rowFailure(trace.status(), toolResponses);
        Projection projection = project(
                scheduledCase.caseId(),
                providerTurns,
                toolCalls,
                toolResponses,
                failure == null ? null : failure.category(),
                failure == null ? null : failure.safeMessage());
        ToolCompatibilitySystemPromptIdentity systemPrompt = ToolCompatibilityProtocol.systemPromptIdentity();
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        ToolCompatibilityVisibleReasoningEvidence visibleReasoning = projection.visibleReasoning();
        return new ToolCompatibilityRow(
                scheduledCase.sequence(),
                scheduledCase.caseId(),
                scheduledCase.repetition(),
                scheduledCase.seed(),
                ToolCompatibilityProtocol.PROVIDER,
                modelIdentity.requestedModel(),
                modelIdentity.effectiveModel(),
                modelIdentity.digest(),
                systemPrompt.id(),
                systemPrompt.version(),
                systemPrompt.sha256(),
                settings.temperature(),
                settings.maxOutputTokensPerProviderTurn(),
                Duration.ofMillis(settings.rowTimeoutMillis()),
                settings.logicalRowAttempts(),
                providerTurns,
                toolCalls,
                toolResponses,
                projection.assertions(),
                projection.rowAttemptCompleted(),
                projection.exactCallSequenceMatched(),
                projection.allExpectedArgumentsMatched(),
                projection.finalResponsePresent(),
                projection.caseContractPassed(),
                projection.finalAssistantOutput(),
                visibleReasoning.thinkTagDetected(),
                visibleReasoning.markerDetectedAnywhere(),
                visibleReasoning.markerDetectedBeforeFirstToolCall(),
                visibleReasoning.markerDetectedAfterToolExecution(),
                visibleReasoning.visibleReasoningTextInFinalOutput(),
                projection.anyProviderTurnReachedOutputLimit(),
                projection.aggregateUsage(),
                Duration.ofMillis(trace.rowLatencyMillis()),
                failure == null ? null : failure.category(),
                projection.diagnosticCategory(),
                failure == null ? null : failure.safeMessage());
    }

    static Projection project(
            String caseId,
            List<ToolCompatibilityProviderTurnEvidence> providerTurns,
            List<ToolCompatibilityToolCallEvidence> toolCalls,
            List<ToolCompatibilityToolResponseEvidence> toolResponses,
            String failureCategory,
            String safeErrorMessage
    ) {
        providerTurns = List.copyOf(providerTurns == null ? List.of() : providerTurns);
        toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        toolResponses = List.copyOf(toolResponses == null ? List.of() : toolResponses);
        validateLinkage(providerTurns, toolCalls, toolResponses);
        validateCallEvidence(caseId, toolCalls);
        validateFailureEvidence(providerTurns, toolResponses, failureCategory, safeErrorMessage);

        boolean rowAttemptCompleted = failureCategory == null;
        ToolCompatibilityCaseOracle.CaseExpectation oracle =
                ToolCompatibilityProtocol.caseOracle().requireCase(caseId);
        boolean exactCallSequenceMatched = exactCallSequenceMatched(oracle, toolCalls);
        boolean allExpectedArgumentsMatched = allExpectedArgumentsMatched(oracle, toolCalls);
        String finalAssistantOutput = finalAssistantOutput(providerTurns);
        boolean finalResponsePresent = finalAssistantOutput != null && !finalAssistantOutput.isBlank();
        boolean anyProviderTurnReachedOutputLimit = providerTurns.stream()
                .anyMatch(turn -> turn.outputLimitState() == ToolCompatibilityOutputLimitState.REACHED);
        ToolCompatibilityVisibleReasoningEvidence visibleReasoning =
                new ToolCompatibilityVisibleReasoningDetector().detect(providerTurns, toolCalls);
        ToolCompatibilityTokenUsageEvidence aggregateUsage = aggregateUsage(providerTurns);
        List<ToolBenchmarkAssertion> assertions = assertions(
                caseId,
                rowAttemptCompleted,
                finalAssistantOutput,
                toolCalls,
                toolResponses);
        boolean caseContractPassed = exactCallSequenceMatched
                && allExpectedArgumentsMatched
                && assertions.stream().allMatch(ToolBenchmarkAssertion::passed);
        String diagnosticCategory = new ToolCompatibilityDiagnosticClassifier().classify(
                new ToolCompatibilityDiagnosticClassifier.ClassificationInput(
                        caseId,
                        caseContractPassed,
                        exactCallSequenceMatched,
                        finalResponsePresent,
                        failureCategory,
                        toolCalls,
                        toolResponses,
                        assertions,
                        visibleReasoning));
        return new Projection(
                assertions,
                rowAttemptCompleted,
                exactCallSequenceMatched,
                allExpectedArgumentsMatched,
                finalResponsePresent,
                caseContractPassed,
                finalAssistantOutput,
                visibleReasoning,
                diagnosticCategory,
                anyProviderTurnReachedOutputLimit,
                aggregateUsage);
    }

    static Duration minimumObservedLatency(List<ToolCompatibilityProviderTurnEvidence> providerTurns) {
        Duration total = Duration.ZERO;
        for (ToolCompatibilityProviderTurnEvidence turn : providerTurns) {
            total = total.plus(turn.latency());
        }
        return total;
    }

    private static ToolCompatibilityProviderTurnEvidence providerTurnEvidence(
            ToolCompatibilityObservedProviderTurn observed
    ) {
        ToolCompatibilityEvidenceState invocationState = switch (observed.state()) {
            case COMPLETED -> ToolCompatibilityEvidenceState.SUCCEEDED;
            case PROVIDER_FAILURE -> ToolCompatibilityEvidenceState.FAILED;
        };
        ToolCompatibilityFailure failure = invocationState == ToolCompatibilityEvidenceState.FAILED
                ? ToolCompatibilityFailure.of(ToolCompatibilityFailure.PROVIDER_FAILURE)
                : null;
        return new ToolCompatibilityProviderTurnEvidence(
                observed.sequence(),
                observed.assistantText(),
                observed.orderedToolCallIds(),
                observed.responseId(),
                observed.responseModel(),
                metadata(observed.responseMetadata()),
                observed.finishReason(),
                ToolCompatibilityTokenUsageEvidence.observed(
                        observed.promptTokens(),
                        observed.completionTokens(),
                        observed.totalTokens()),
                outputLimitState(observed.outputTokenLimitReached()),
                Duration.ofMillis(observed.latencyMillis()),
                invocationState,
                failure);
    }

    private static ToolCompatibilityToolCallEvidence toolCallEvidence(
            ToolCompatibilityObservedToolCall observed,
            Integer responseSequence
    ) {
        ToolCompatibilityEvidenceState rawJsonState = observed.rawArgumentJsonValid()
                ? ToolCompatibilityEvidenceState.SUCCEEDED
                : ToolCompatibilityEvidenceState.FAILED;
        ToolCompatibilityEvidenceState schemaState = observed.rawArgumentSchemaValid() == null
                ? observed.rawArgumentJsonValid()
                        ? ToolCompatibilityEvidenceState.UNOBSERVABLE
                        : ToolCompatibilityEvidenceState.NOT_REACHED
                : state(observed.rawArgumentSchemaValid());
        ToolCompatibilityEvidenceState expectedArgumentsState = observed.expectedCallSequence() == null
                || !observed.rawArgumentJsonValid()
                        ? ToolCompatibilityEvidenceState.NOT_REACHED
                        : state(observed.expectedArgumentsMatched());
        ToolCompatibilityEvidenceState executionState = callbackExecutionState(observed);
        ToolCompatibilityEvidenceState bindingState = observed.callbackBindingSucceeded() == null
                ? observed.callbackExecuted()
                        ? ToolCompatibilityEvidenceState.UNOBSERVABLE
                        : ToolCompatibilityEvidenceState.NOT_REACHED
                : state(observed.callbackBindingSucceeded());
        return new ToolCompatibilityToolCallEvidence(
                observed.globalSequence(),
                observed.providerTurnSequence(),
                observed.callId(),
                observed.type(),
                observed.toolName(),
                observed.rawArguments(),
                rawJsonState,
                schemaState,
                observed.rawArgumentIssue(),
                observed.expectedCallSequence(),
                state(observed.expectedCallAtSequence()),
                expectedArgumentsState,
                bindingState,
                executionState,
                responseSequence);
    }

    private static ToolCompatibilityFailure rowFailure(
            ToolCompatibilityInvocationStatus status,
            List<ToolCompatibilityToolResponseEvidence> responses
    ) {
        return switch (status) {
            case COMPLETED -> null;
            case PROVIDER_FAILURE -> ToolCompatibilityFailure.of(ToolCompatibilityFailure.PROVIDER_FAILURE);
            case CALLBACK_FAILURE -> responses.reversed().stream()
                    .map(ToolCompatibilityToolResponseEvidence::failure)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> ToolCompatibilityFailure.of(ToolCompatibilityFailure.CALLBACK_FAILURE));
            case ROW_TIMEOUT -> ToolCompatibilityFailure.of(ToolCompatibilityFailure.ROW_TIMEOUT);
            case TIMEOUT_WORK_NOT_STOPPED -> throw new ToolCompatibilityProtocolIntegrityException(
                    "Unsafe timeout status cannot become ordinary row evidence");
        };
    }

    private static void validateLinkage(
            List<ToolCompatibilityProviderTurnEvidence> turns,
            List<ToolCompatibilityToolCallEvidence> calls,
            List<ToolCompatibilityToolResponseEvidence> responses
    ) {
        Set<String> callIds = new LinkedHashSet<>();
        for (int index = 0; index < turns.size(); index++) {
            if (turns.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException("provider turns must be contiguous and one-based");
            }
        }
        for (int index = 0; index < calls.size(); index++) {
            ToolCompatibilityToolCallEvidence call = calls.get(index);
            if (call.sequence() != index + 1
                    || call.providerTurnSequence() > turns.size()
                    || !callIds.add(call.callId())) {
                throw new IllegalArgumentException(
                        "tool calls must be contiguous, uniquely identified, and linked to a provider turn");
            }
        }
        for (ToolCompatibilityProviderTurnEvidence turn : turns) {
            List<String> linkedIds = calls.stream()
                    .filter(call -> call.providerTurnSequence() == turn.sequence())
                    .map(ToolCompatibilityToolCallEvidence::callId)
                    .toList();
            if (!linkedIds.equals(turn.orderedToolCallIds())) {
                throw new IllegalArgumentException(
                        "provider-turn tool-call IDs must exactly match linked calls in order");
            }
        }
        for (int index = 0; index + 1 < turns.size(); index++) {
            ToolCompatibilityProviderTurnEvidence turn = turns.get(index);
            if (turn.invocationState() == ToolCompatibilityEvidenceState.SUCCEEDED
                    && turn.orderedToolCallIds().isEmpty()) {
                throw new IllegalArgumentException(
                        "a no-tool assistant completion must be the final provider turn");
            }
        }

        Set<Integer> linkedCallSequences = new LinkedHashSet<>();
        for (int index = 0; index < responses.size(); index++) {
            ToolCompatibilityToolResponseEvidence response = responses.get(index);
            if (response.sequence() != index + 1
                    || response.toolCallSequence() > calls.size()
                    || !linkedCallSequences.add(response.toolCallSequence())) {
                throw new IllegalArgumentException(
                        "tool responses must be contiguous and link one-to-one to a tool call");
            }
            ToolCompatibilityToolCallEvidence call = calls.get(response.toolCallSequence() - 1);
            if (!Objects.equals(call.toolResponseSequence(), response.sequence())
                    || !call.callId().equals(response.callId())
                    || !call.toolName().equals(response.toolName())) {
                throw new IllegalArgumentException("tool response linkage contradicts its tool call");
            }
            validateCallbackLifecycle(call, response);
        }
        for (ToolCompatibilityToolCallEvidence call : calls) {
            boolean hasResponse = call.toolResponseSequence() != null;
            if (hasResponse != linkedCallSequences.contains(call.sequence())) {
                throw new IllegalArgumentException("tool call response linkage is incomplete");
            }
        }
    }

    private static void validateCallbackLifecycle(
            ToolCompatibilityToolCallEvidence call,
            ToolCompatibilityToolResponseEvidence response
    ) {
        if (response.callbackResultState() == ToolCompatibilityEvidenceState.SUCCEEDED) {
            if (call.callbackExecutionState() != ToolCompatibilityEvidenceState.SUCCEEDED
                    || call.callbackBindingState() != ToolCompatibilityEvidenceState.SUCCEEDED) {
                throw new IllegalArgumentException(
                        "successful callback response requires successful execution and binding");
            }
            return;
        }
        String category = response.failure().category();
        switch (category) {
            case ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE -> {
                if (call.callbackExecutionState() != ToolCompatibilityEvidenceState.NOT_REACHED
                        || call.callbackBindingState() != ToolCompatibilityEvidenceState.NOT_REACHED) {
                    throw new IllegalArgumentException(
                            "callback resolution failure must precede execution and binding");
                }
            }
            case ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE -> {
                if (call.callbackExecutionState() != ToolCompatibilityEvidenceState.NOT_REACHED
                        || call.callbackBindingState() != ToolCompatibilityEvidenceState.FAILED) {
                    throw new IllegalArgumentException("callback binding failure lifecycle is contradictory");
                }
            }
            case ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE -> {
                if (call.callbackExecutionState() != ToolCompatibilityEvidenceState.FAILED
                        || call.callbackBindingState() != ToolCompatibilityEvidenceState.SUCCEEDED) {
                    throw new IllegalArgumentException("callback invocation failure lifecycle is contradictory");
                }
            }
            case ToolCompatibilityFailure.CALLBACK_FAILURE -> {
                if (call.callbackExecutionState() != ToolCompatibilityEvidenceState.FAILED
                        || call.callbackBindingState() != ToolCompatibilityEvidenceState.UNOBSERVABLE) {
                    throw new IllegalArgumentException("unclassified callback failure lifecycle is contradictory");
                }
            }
            default -> throw new IllegalArgumentException("tool response contains a non-callback failure");
        }
    }

    private static void validateCallEvidence(
            String caseId,
            List<ToolCompatibilityToolCallEvidence> calls
    ) {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        ToolCompatibilityCallbackCatalog.canonicalCallbacks()
                .forEach(callback -> callbacks.put(callback.getToolDefinition().name(), callback));
        ToolCompatibilitySchemaValidator validator = new ToolCompatibilitySchemaValidator();
        ToolCompatibilityCaseOracle.CaseExpectation expectation =
                ToolCompatibilityProtocol.caseOracle().requireCase(caseId);
        for (ToolCompatibilityToolCallEvidence call : calls) {
            ToolCallback callback = callbacks.get(call.toolName());
            ToolCompatibilitySchemaValidator.RawArgumentValidation validation = callback == null
                    ? validator.validateJsonOnly(call.rawArgumentJson())
                    : validator.validate(callback.getToolDefinition(), call.rawArgumentJson());
            ToolCompatibilityEvidenceState expectedRawState = validation.jsonValid()
                    ? ToolCompatibilityEvidenceState.SUCCEEDED
                    : ToolCompatibilityEvidenceState.FAILED;
            ToolCompatibilityEvidenceState expectedSchemaState = validation.schemaValid() == null
                    ? validation.jsonValid()
                            ? ToolCompatibilityEvidenceState.UNOBSERVABLE
                            : ToolCompatibilityEvidenceState.NOT_REACHED
                    : state(validation.schemaValid());
            if (call.rawArgumentJsonState() != expectedRawState
                    || call.declaredSchemaState() != expectedSchemaState
                    || call.rawArgumentIssue() != validation.issue()) {
                throw new IllegalArgumentException("raw argument evidence contradicts canonical validation");
            }

            ToolCompatibilityExpectedCall expected = call.sequence() <= expectation.calls().size()
                    ? expectation.calls().get(call.sequence() - 1)
                    : null;
            Integer expectedSequence = expected == null ? null : call.sequence();
            ToolCompatibilityEvidenceState expectedCallState = expected != null
                    && expected.toolName().equals(call.toolName())
                            ? ToolCompatibilityEvidenceState.SUCCEEDED
                            : ToolCompatibilityEvidenceState.FAILED;
            ToolCompatibilityEvidenceState expectedArgumentsState = expected == null
                    || validation.parsedArguments() == null
                            ? ToolCompatibilityEvidenceState.NOT_REACHED
                            : ToolCompatibilityJsonSemantics.equals(
                                    expected.arguments(), validation.parsedArguments())
                                            ? ToolCompatibilityEvidenceState.SUCCEEDED
                                            : ToolCompatibilityEvidenceState.FAILED;
            if (!Objects.equals(call.expectedCallSequence(), expectedSequence)
                    || call.expectedCallAtSequenceState() != expectedCallState
                    || call.expectedArgumentsState() != expectedArgumentsState) {
                throw new IllegalArgumentException("tool-call oracle evidence contradicts the locked oracle");
            }
        }
    }

    private static void validateFailureEvidence(
            List<ToolCompatibilityProviderTurnEvidence> turns,
            List<ToolCompatibilityToolResponseEvidence> responses,
            String failureCategory,
            String safeErrorMessage
    ) {
        ToolCompatibilityFailure rowFailure = failureCategory == null
                ? null
                : new ToolCompatibilityFailure(failureCategory, safeErrorMessage);
        if (rowFailure == null && safeErrorMessage != null) {
            throw new IllegalArgumentException("safeErrorMessage requires a row failure category");
        }
        boolean providerFailed = turns.stream().anyMatch(turn -> turn.failure() != null);
        boolean callbackResolutionFailed = responses.stream()
                .map(ToolCompatibilityToolResponseEvidence::failure)
                .filter(Objects::nonNull)
                .anyMatch(failure -> ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE
                        .equals(failure.category()));
        if (rowFailure == null && (providerFailed || callbackResolutionFailed)) {
            throw new IllegalArgumentException("terminal lifecycle failure is missing its row projection");
        }
        if (rowFailure == null) {
            return;
        }
        switch (rowFailure.category()) {
            case ToolCompatibilityFailure.PROVIDER_FAILURE -> {
                if (!providerFailed) {
                    throw new IllegalArgumentException("provider row failure lacks failed provider-turn evidence");
                }
            }
            case ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE -> {
                if (!callbackResolutionFailed) {
                    throw new IllegalArgumentException("callback-resolution row failure lacks response evidence");
                }
            }
            case ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE,
                    ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE,
                    ToolCompatibilityFailure.CALLBACK_FAILURE -> {
                boolean matching = responses.stream()
                        .map(ToolCompatibilityToolResponseEvidence::failure)
                        .filter(Objects::nonNull)
                        .anyMatch(failure -> rowFailure.category().equals(failure.category()));
                if (!matching) {
                    throw new IllegalArgumentException("callback row failure lacks matching response evidence");
                }
            }
            case ToolCompatibilityFailure.ROW_TIMEOUT -> {
                // A confirmed-stopped timeout may contain any completed partial lifecycle evidence.
            }
            default -> throw new IllegalArgumentException("unsupported row failure category");
        }
    }

    private static boolean exactCallSequenceMatched(
            ToolCompatibilityCaseOracle.CaseExpectation expectation,
            List<ToolCompatibilityToolCallEvidence> calls
    ) {
        if (calls.size() != expectation.calls().size()) {
            return false;
        }
        for (int index = 0; index < calls.size(); index++) {
            if (!expectation.calls().get(index).toolName().equals(calls.get(index).toolName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean allExpectedArgumentsMatched(
            ToolCompatibilityCaseOracle.CaseExpectation expectation,
            List<ToolCompatibilityToolCallEvidence> calls
    ) {
        if (calls.size() < expectation.calls().size()) {
            return false;
        }
        for (int index = 0; index < expectation.calls().size(); index++) {
            if (calls.get(index).expectedArgumentsState() != ToolCompatibilityEvidenceState.SUCCEEDED) {
                return false;
            }
        }
        return true;
    }

    private static String finalAssistantOutput(List<ToolCompatibilityProviderTurnEvidence> turns) {
        String output = null;
        for (ToolCompatibilityProviderTurnEvidence turn : turns) {
            if (turn.invocationState() == ToolCompatibilityEvidenceState.SUCCEEDED
                    && turn.orderedToolCallIds().isEmpty()) {
                output = turn.assistantText();
            }
        }
        return output;
    }

    private static ToolCompatibilityTokenUsageEvidence aggregateUsage(
            List<ToolCompatibilityProviderTurnEvidence> turns
    ) {
        if (turns.isEmpty()
                || turns.stream().allMatch(turn -> turn.usage().availability()
                        == ToolCompatibilityUsageAvailability.ABSENT)) {
            return new ToolCompatibilityTokenUsageEvidence(
                    ToolCompatibilityUsageAvailability.ABSENT, null, null, null);
        }
        boolean completeCoverage = turns.stream().allMatch(turn -> turn.usage().availability()
                == ToolCompatibilityUsageAvailability.COMPLETE);
        return new ToolCompatibilityTokenUsageEvidence(
                completeCoverage
                        ? ToolCompatibilityUsageAvailability.COMPLETE
                        : ToolCompatibilityUsageAvailability.PARTIAL,
                sum(turns, UsageField.PROMPT),
                sum(turns, UsageField.COMPLETION),
                sum(turns, UsageField.TOTAL));
    }

    private static Integer sum(
            List<ToolCompatibilityProviderTurnEvidence> turns,
            UsageField field
    ) {
        Integer result = null;
        for (ToolCompatibilityProviderTurnEvidence turn : turns) {
            Integer value = switch (field) {
                case PROMPT -> turn.usage().promptTokens();
                case COMPLETION -> turn.usage().completionTokens();
                case TOTAL -> turn.usage().totalTokens();
            };
            if (value != null) {
                result = result == null ? value : Math.addExact(result, value);
            }
        }
        return result;
    }

    private static List<ToolBenchmarkAssertion> assertions(
            String caseId,
            boolean rowAttemptCompleted,
            String finalAssistantOutput,
            List<ToolCompatibilityToolCallEvidence> calls,
            List<ToolCompatibilityToolResponseEvidence> responses
    ) {
        ToolBenchmarkPrompt prompt = ToolCompatibilityProtocol.caseSelection().cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown canonical case: " + caseId));
        List<ToolBenchmarkAssertion> assertions = new ArrayList<>();
        assertions.add(assertion(
                "run_completed",
                "model invocation",
                rowAttemptCompleted,
                rowAttemptCompleted
                        ? "The model invocation completed."
                        : "The model invocation failed."));

        Set<String> executedTools = calls.stream()
                .filter(call -> call.callbackExecutionState() == ToolCompatibilityEvidenceState.SUCCEEDED
                        || call.callbackExecutionState() == ToolCompatibilityEvidenceState.FAILED)
                .map(ToolCompatibilityToolCallEvidence::toolName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ToolBenchmarkExpectation expectation = prompt.expectation();
        for (String tool : expectation.requiredExecutedTools()) {
            boolean executed = executedTools.contains(tool);
            assertions.add(assertion(
                    "required_tool_executed",
                    tool,
                    executed,
                    executed ? "The required tool executed." : "The required tool did not execute."));
        }
        for (String tool : expectation.forbiddenExecutedTools()) {
            boolean avoided = !executedTools.contains(tool);
            assertions.add(assertion(
                    "forbidden_tool_not_executed",
                    tool,
                    avoided,
                    avoided ? "The forbidden tool was not executed." : "The forbidden tool executed."));
        }
        for (String term : expectation.requiredOutputTerms()) {
            boolean present = containsIgnoreCase(finalAssistantOutput, term);
            assertions.add(assertion(
                    "output_contains",
                    term,
                    present,
                    present
                            ? "The final output contains the required term."
                            : "The final output does not contain the required term."));
        }
        String combinedResponses = responses.stream()
                .map(ToolCompatibilityToolResponseEvidence::responseData)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);
        for (String term : expectation.requiredToolResponseTerms()) {
            boolean present = containsIgnoreCase(combinedResponses, term);
            assertions.add(assertion(
                    "tool_response_contains",
                    term,
                    present,
                    present
                            ? "A tool response contains the required term."
                            : "No tool response contains the required term."));
        }
        return List.copyOf(assertions);
    }

    private static ToolBenchmarkAssertion assertion(
            String check,
            String target,
            boolean passed,
            String detail
    ) {
        return new ToolBenchmarkAssertion(check, target, passed, detail);
    }

    private static boolean containsIgnoreCase(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static ToolCompatibilityOutputLimitState outputLimitState(Boolean reached) {
        if (reached == null) {
            return ToolCompatibilityOutputLimitState.UNOBSERVABLE;
        }
        return reached
                ? ToolCompatibilityOutputLimitState.REACHED
                : ToolCompatibilityOutputLimitState.NOT_REACHED;
    }

    private static ToolCompatibilityEvidenceState state(Boolean succeeded) {
        if (succeeded == null) {
            return ToolCompatibilityEvidenceState.UNOBSERVABLE;
        }
        return succeeded
                ? ToolCompatibilityEvidenceState.SUCCEEDED
                : ToolCompatibilityEvidenceState.FAILED;
    }

    private static ToolCompatibilityEvidenceState callbackExecutionState(
            ToolCompatibilityObservedToolCall observed
    ) {
        if (!observed.callbackExecuted()) {
            return ToolCompatibilityEvidenceState.NOT_REACHED;
        }
        if (observed.callbackSucceeded() == null) {
            return ToolCompatibilityEvidenceState.UNOBSERVABLE;
        }
        if (observed.callbackSucceeded()) {
            return ToolCompatibilityEvidenceState.SUCCEEDED;
        }
        return observed.callbackFailureKind()
                        == ToolCompatibilityCallbackFailureKind.CALLBACK_BINDING_FAILURE
                ? ToolCompatibilityEvidenceState.NOT_REACHED
                : ToolCompatibilityEvidenceState.FAILED;
    }

    private static Map<String, JsonNode> metadata(Map<String, Object> metadata) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            try {
                result.put(key, JSON.valueToTree(value));
            } catch (IllegalArgumentException exception) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Provider response metadata could not be represented as JSON", exception);
            }
        });
        return result;
    }

    record Projection(
            List<ToolBenchmarkAssertion> assertions,
            boolean rowAttemptCompleted,
            boolean exactCallSequenceMatched,
            boolean allExpectedArgumentsMatched,
            boolean finalResponsePresent,
            boolean caseContractPassed,
            String finalAssistantOutput,
            ToolCompatibilityVisibleReasoningEvidence visibleReasoning,
            String diagnosticCategory,
            boolean anyProviderTurnReachedOutputLimit,
            ToolCompatibilityTokenUsageEvidence aggregateUsage
    ) {}

    private enum UsageField {
        PROMPT,
        COMPLETION,
        TOTAL
    }
}
