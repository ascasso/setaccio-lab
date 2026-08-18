package com.setaccio.lab.toolcompat;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.method.MethodToolCallback;
import tools.jackson.core.JacksonException;

/**
 * One logical, standard-advisor tool-calling attempt. It observes recursive provider
 * turns but does not replace Spring AI's tool loop or define the final evidence schema.
 */
final class ToolCompatibilityInvocationBoundary {

    private static final Duration STOP_CONFIRMATION_TIMEOUT = Duration.ofSeconds(1);
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();

    private final Duration rowDeadline;
    private final Duration stopConfirmationTimeout;
    private boolean nextSequentialAttemptAllowed = true;

    ToolCompatibilityInvocationBoundary() {
        this(ToolCompatibilityProtocol.ROW_TIMEOUT, STOP_CONFIRMATION_TIMEOUT);
    }

    static OllamaApi createControlledOllamaApi(String loopbackBaseUrl) {
        return new OllamaChatModelFactory().createApi(loopbackBaseUrl, ToolCompatibilityProtocol.ROW_TIMEOUT);
    }

    static ToolCompatibilityControlledOllamaModel createControlledOllamaModel(
            OllamaApi ollamaApi,
            OllamaApi.ListModelResponse installedModels,
            int seed
    ) {
        Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
        if (!ToolCompatibilityProtocol.SEEDS.contains(seed)) {
            throw new IllegalArgumentException("seed must be one of the locked protocol seeds");
        }
        ToolCompatibilityModelIdentity modelIdentity = ToolCompatibilityModelInventory.requireInstalled(
                installedModels,
                ToolCompatibilityProtocol.INITIAL_MODEL);
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelIdentity.requestedModel())
                .temperature(settings.temperature())
                .seed(seed)
                .numPredict(settings.maxOutputTokensPerProviderTurn())
                .build();
        return new ToolCompatibilityControlledOllamaModel(
                OllamaChatModelFactory.createNoPullModel(
                        ollamaApi,
                        options,
                        ToolCompatibilityProtocol.ROW_TIMEOUT,
                        ToolCompatibilityProtocol.LOGICAL_ROW_ATTEMPTS),
                modelIdentity);
    }

    static ToolCompatibilityInvocationBoundary forProviderFreeDeadlineTest(
            Duration rowDeadline, Duration stopConfirmationTimeout) {
        return new ToolCompatibilityInvocationBoundary(rowDeadline, stopConfirmationTimeout);
    }

    private ToolCompatibilityInvocationBoundary(Duration rowDeadline, Duration stopConfirmationTimeout) {
        this.rowDeadline = requirePositive(rowDeadline, "rowDeadline");
        this.stopConfirmationTimeout = requirePositive(stopConfirmationTimeout, "stopConfirmationTimeout");
    }

    synchronized ToolCompatibilityInvocationTrace invoke(
            ChatModel chatModel,
            ToolCompatibilityCaseSelection.ScheduledCase scheduledCase,
            List<ToolCallback> callbacks
    ) {
        Objects.requireNonNull(chatModel, "chatModel must not be null");
        Objects.requireNonNull(scheduledCase, "scheduledCase must not be null");
        if (!nextSequentialAttemptAllowed) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "A previous timed-out tool compatibility attempt did not stop safely");
        }
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        if (!ToolCompatibilityProtocol.SEEDS.contains(scheduledCase.seed())) {
            throw new IllegalArgumentException("scheduled case seed must be one of the locked protocol seeds");
        }
        ToolBenchmarkPrompt prompt = requireCanonicalPrompt(scheduledCase.caseId());
        List<ToolCallback> canonicalCallbacks = ToolCompatibilityCallbackCatalog.requireExactCallbacks(callbacks);
        TraceRecorder recorder = new TraceRecorder(
                ToolCompatibilityProtocol.caseOracle().requireCase(scheduledCase.caseId()),
                canonicalCallbacks,
                settings.maxOutputTokensPerProviderTurn());
        ObservingAdvisor observingAdvisor = new ObservingAdvisor(recorder);
        List<ToolCallback> observedCallbacks = canonicalCallbacks.stream()
                .map(callback -> new ObservingToolCallback(callback, recorder))
                .map(ToolCallback.class::cast)
                .toList();

        ExecutorService executor = newSingleWorker();
        long attemptStarted = System.nanoTime();
        Future<Void> future = executor.submit(invokeTask(
                chatModel, prompt, scheduledCase.seed(), settings, observedCallbacks, observingAdvisor));
        try {
            future.get(rowDeadline.toNanos(), TimeUnit.NANOSECONDS);
            recorder.complete();
            executor.shutdown();
            return recorder.snapshot(true, elapsedMillis(attemptStarted));
        } catch (TimeoutException exception) {
            recorder.rowTimedOut();
            future.cancel(true);
            executor.shutdownNow();
            boolean stopped = awaitTermination(executor, stopConfirmationTimeout);
            if (!stopped) {
                recorder.timedOutWorkDidNotStop();
                nextSequentialAttemptAllowed = false;
                ToolCompatibilityInvocationTrace trace = recorder.snapshot(
                        false, elapsedMillis(attemptStarted));
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Timed-out tool compatibility work could not be confirmed stopped",
                        trace);
            }
            return recorder.snapshot(true, elapsedMillis(attemptStarted));
        } catch (InterruptedException exception) {
            future.cancel(true);
            executor.shutdownNow();
            nextSequentialAttemptAllowed = false;
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for tool compatibility attempt", exception);
        } catch (ExecutionException exception) {
            executor.shutdownNow();
            awaitTermination(executor, stopConfirmationTimeout);
            Throwable cause = exception.getCause();
            if (cause instanceof ToolCompatibilityProtocolIntegrityException integrityException) {
                throw integrityException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            if (recorder.hasProviderFailure()) {
                return recorder.snapshot(true, elapsedMillis(attemptStarted));
            }
            if (recorder.recordTerminalCallbackFailure(cause)) {
                return recorder.snapshot(true, elapsedMillis(attemptStarted));
            }
            ToolCompatibilityInvocationTrace trace = recorder.snapshot(
                    true, elapsedMillis(attemptStarted));
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Standard tool-calling advisor failed outside an observed provider or callback stage",
                    cause == null ? exception : cause,
                    trace);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Void> invokeTask(
            ChatModel chatModel,
            ToolBenchmarkPrompt prompt,
            int seed,
            ToolCompatibilityRunSettings settings,
            List<ToolCallback> callbacks,
            ObservingAdvisor observingAdvisor
    ) {
        return () -> {
            OllamaChatOptions options = OllamaChatOptions.builder()
                    .model(settings.requestedModel())
                    .temperature(settings.temperature())
                    .seed(seed)
                    .numPredict(settings.maxOutputTokensPerProviderTurn())
                    .build();
            ToolCallingAdvisor toolCallingAdvisor = ToolCallingAdvisor.builder()
                    .toolCallingManager(ToolCallingManager.builder().build())
                    .build();
            ChatClient.builder(chatModel)
                    .defaultAdvisors(toolCallingAdvisor, observingAdvisor)
                    .build()
                    .prompt(prompt.text())
                    .options(options.mutate())
                    .tools(callbacks)
                    .call()
                    .chatResponse();
            return null;
        };
    }

    private static ToolBenchmarkPrompt requireCanonicalPrompt(String caseId) {
        return ToolCompatibilityProtocol.caseSelection().cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown canonical tool compatibility case: " + caseId));
    }

    private static ExecutorService newSingleWorker() {
        ThreadFactory factory = runnable -> {
            Thread worker = new Thread(runnable, "tool-compatibility-attempt-" + WORKER_SEQUENCE.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    private static boolean awaitTermination(ExecutorService executor, Duration timeout) {
        try {
            return executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? "Provider invocation failed" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private static final class ObservingAdvisor implements CallAdvisor {

        private final TraceRecorder recorder;

        private ObservingAdvisor(TraceRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public ChatClientResponse adviseCall(
                ChatClientRequest request, CallAdvisorChain chain) {
            TraceRecorder.MutableProviderTurn turn = recorder.beginProviderTurn(request);
            long started = System.nanoTime();
            try {
                ChatClientResponse response = chain.nextCall(request);
                recorder.completeProviderTurn(turn, response.chatResponse(), elapsedMillis(started));
                return response;
            } catch (ToolCompatibilityProtocolIntegrityException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                recorder.failProviderTurn(turn, elapsedMillis(started), safeMessage(exception));
                throw exception;
            }
        }

        @Override
        public String getName() {
            return "Tool Compatibility Per-Turn Observer";
        }

        @Override
        public int getOrder() {
            return ToolCallingAdvisor.DEFAULT_ORDER + 1;
        }
    }

    private static final class ObservingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final TraceRecorder recorder;

        private ObservingToolCallback(ToolCallback delegate, TraceRecorder recorder) {
            this.delegate = delegate;
            this.recorder = recorder;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return invoke(toolInput, () -> delegate.call(toolInput));
        }

        @Override
        public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
            return invoke(toolInput, () -> delegate.call(toolInput, toolContext));
        }

        private String invoke(String toolInput, Callable<String> callback) {
            TraceRecorder.MutableToolCall toolCall = recorder.beginCallback(
                    delegate.getToolDefinition().name(), toolInput);
            try {
                String response = callback.call();
                recorder.callbackSucceeded(toolCall, response);
                return response;
            } catch (RuntimeException exception) {
                recorder.callbackFailed(toolCall, delegate, exception);
                throw exception;
            } catch (Exception exception) {
                recorder.callbackFailed(toolCall, delegate, exception);
                throw new IllegalStateException("Tool callback failed", exception);
            }
        }
    }

    private static final class TraceRecorder {

        private final ToolCompatibilityCaseOracle.CaseExpectation expectation;
        private final Map<String, ToolCallback> callbacksByName;
        private final ToolCompatibilitySchemaValidator schemaValidator = new ToolCompatibilitySchemaValidator();
        private final int outputTokenLimit;
        private final List<MutableProviderTurn> providerTurns = new ArrayList<>();
        private final List<MutableToolCall> toolCalls = new ArrayList<>();
        private ToolCompatibilityInvocationStatus status = ToolCompatibilityInvocationStatus.COMPLETED;
        private String terminalMessage;

        private TraceRecorder(
                ToolCompatibilityCaseOracle.CaseExpectation expectation,
                List<ToolCallback> callbacks,
                int outputTokenLimit
        ) {
            this.expectation = expectation;
            this.outputTokenLimit = outputTokenLimit;
            this.callbacksByName = new LinkedHashMap<>();
            for (ToolCallback callback : callbacks) {
                callbacksByName.put(callback.getToolDefinition().name(), callback);
            }
        }

        private synchronized MutableProviderTurn beginProviderTurn(ChatClientRequest request) {
            MutableProviderTurn turn = new MutableProviderTurn(providerTurns.size() + 1);
            providerTurns.add(turn);
            recordToolResponses(request);
            return turn;
        }

        private synchronized void completeProviderTurn(
                MutableProviderTurn turn, ChatResponse response, long latencyMillis) {
            turn.latencyMillis = latencyMillis;
            turn.state = ToolCompatibilityProviderTurnState.COMPLETED;
            if (response == null || response.getResult() == null) {
                return;
            }
            Generation generation = response.getResult();
            AssistantMessage assistant = generation.getOutput();
            turn.assistantText = assistant == null ? null : assistant.getText();
            ChatGenerationMetadata generationMetadata = generation.getMetadata();
            turn.finishReason = generationMetadata == null ? null : generationMetadata.getFinishReason();
            captureResponseMetadata(turn, response.getMetadata());
            if (assistant == null) {
                return;
            }
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                turn.orderedToolCallIds.add(toolCall.id());
                toolCalls.add(newToolCall(turn.sequence, toolCall));
            }
        }

        private synchronized void failProviderTurn(MutableProviderTurn turn, long latencyMillis, String failure) {
            turn.latencyMillis = latencyMillis;
            turn.state = ToolCompatibilityProviderTurnState.PROVIDER_FAILURE;
            turn.providerFailure = failure;
            if (status == ToolCompatibilityInvocationStatus.COMPLETED) {
                status = ToolCompatibilityInvocationStatus.PROVIDER_FAILURE;
                terminalMessage = failure;
            }
        }

        private synchronized MutableToolCall beginCallback(String toolName, String callbackInput) {
            String expectedInput = callbackInput == null || callbackInput.isBlank() ? "{}" : callbackInput;
            for (MutableToolCall toolCall : toolCalls) {
                if (!toolCall.callbackExecuted
                        && Objects.equals(toolCall.toolName, toolName)
                        && Objects.equals(toolCall.callbackInput(), expectedInput)) {
                    toolCall.callbackExecuted = true;
                    return toolCall;
                }
            }
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Spring AI callback could not be linked to an observed raw tool call");
        }

        private synchronized void callbackSucceeded(MutableToolCall toolCall, String response) {
            toolCall.callbackBindingSucceeded = true;
            toolCall.callbackSucceeded = true;
            toolCall.callbackResponse = response;
        }

        private synchronized void callbackFailed(
                MutableToolCall toolCall,
                ToolCallback callback,
                Throwable failure
        ) {
            toolCall.callbackSucceeded = false;
            ToolCompatibilityCallbackFailureKind failureKind = classifyCallbackFailure(callback, failure);
            if (failureKind == ToolCompatibilityCallbackFailureKind.CALLBACK_BINDING_FAILURE) {
                toolCall.callbackBindingSucceeded = false;
            } else if (failureKind == ToolCompatibilityCallbackFailureKind.CALLBACK_INVOCATION_FAILURE) {
                toolCall.callbackBindingSucceeded = true;
            }
            toolCall.callbackFailureKind = failureKind;
            toolCall.callbackFailure = safeMessage(failure);
        }

        private synchronized void complete() {
            if (status == ToolCompatibilityInvocationStatus.COMPLETED) {
                return;
            }
        }

        private synchronized boolean hasProviderFailure() {
            return status == ToolCompatibilityInvocationStatus.PROVIDER_FAILURE;
        }

        private synchronized boolean recordTerminalCallbackFailure(Throwable failure) {
            String failureMessage = safeMessage(failure);
            for (int index = toolCalls.size() - 1; index >= 0; index--) {
                MutableToolCall toolCall = toolCalls.get(index);
                if (!toolCall.callbackExecuted && !callbacksByName.containsKey(toolCall.toolName)) {
                    toolCall.callbackSucceeded = false;
                    toolCall.callbackFailureKind =
                            ToolCompatibilityCallbackFailureKind.CALLBACK_RESOLUTION_FAILURE;
                    toolCall.callbackFailure = failureMessage;
                    status = ToolCompatibilityInvocationStatus.CALLBACK_FAILURE;
                    terminalMessage = failureMessage;
                    return true;
                }
                if (toolCall.callbackExecuted && Boolean.FALSE.equals(toolCall.callbackSucceeded)) {
                    status = ToolCompatibilityInvocationStatus.CALLBACK_FAILURE;
                    terminalMessage = failureMessage;
                    return true;
                }
            }
            return false;
        }

        private synchronized void rowTimedOut() {
            status = ToolCompatibilityInvocationStatus.ROW_TIMEOUT;
            terminalMessage = "Tool compatibility row deadline elapsed";
        }

        private synchronized void timedOutWorkDidNotStop() {
            status = ToolCompatibilityInvocationStatus.TIMEOUT_WORK_NOT_STOPPED;
            terminalMessage = "Timed-out tool compatibility work did not stop before the confirmation deadline";
        }

        private synchronized ToolCompatibilityInvocationTrace snapshot(
                boolean safeForNextAttempt,
                long rowLatencyMillis
        ) {
            return new ToolCompatibilityInvocationTrace(
                    status,
                    providerTurns.stream().map(MutableProviderTurn::snapshot).toList(),
                    toolCalls.stream().map(MutableToolCall::snapshot).toList(),
                    terminalMessage,
                    safeForNextAttempt,
                    rowLatencyMillis);
        }

        private static ToolCompatibilityCallbackFailureKind classifyCallbackFailure(
                ToolCallback callback,
                Throwable failure
        ) {
            if (!(callback instanceof MethodToolCallback) || !(failure instanceof ToolExecutionException)) {
                return null;
            }
            return hasCause(failure, JacksonException.class)
                    ? ToolCompatibilityCallbackFailureKind.CALLBACK_BINDING_FAILURE
                    : ToolCompatibilityCallbackFailureKind.CALLBACK_INVOCATION_FAILURE;
        }

        private static boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
            Throwable current = failure;
            while (current != null) {
                if (expectedType.isInstance(current)) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private void recordToolResponses(ChatClientRequest request) {
            request.prompt().getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast)
                    .flatMap(message -> message.getResponses().stream())
                    .forEach(response -> toolCalls.stream()
                            .filter(call -> Objects.equals(call.callId, response.id())
                                    && Objects.equals(call.toolName, response.name()))
                            .findFirst()
                            .ifPresentOrElse(
                                    call -> call.recordCallbackResponse(response.responseData()),
                                    () -> {
                                        throw new ToolCompatibilityProtocolIntegrityException(
                                                "Tool response could not be linked to an observed tool call");
                                    }));
        }

        private MutableToolCall newToolCall(int providerTurnSequence, AssistantMessage.ToolCall toolCall) {
            ToolCompatibilitySchemaValidator.RawArgumentValidation validation;
            ToolCallback callback = callbacksByName.get(toolCall.name());
            if (callback == null) {
                validation = schemaValidator.validateJsonOnly(toolCall.arguments());
            } else {
                validation = schemaValidator.validate(callback.getToolDefinition(), toolCall.arguments());
            }
            int sequence = toolCalls.size() + 1;
            ToolCompatibilityExpectedCall expected = sequence <= expectation.calls().size()
                    ? expectation.calls().get(sequence - 1)
                    : null;
            Boolean expectedCallAtSequence = expected == null ? Boolean.FALSE
                    : expected.toolName().equals(toolCall.name());
            Boolean expectedArgumentsMatched = expected == null || validation.parsedArguments() == null
                    ? Boolean.FALSE
                    : ToolCompatibilityJsonSemantics.equals(
                            expected.arguments(), validation.parsedArguments());
            return new MutableToolCall(
                    sequence,
                    providerTurnSequence,
                    toolCall,
                    validation,
                    expected == null ? null : sequence,
                    expectedCallAtSequence,
                    expectedArgumentsMatched);
        }

        private void captureResponseMetadata(MutableProviderTurn turn, ChatResponseMetadata metadata) {
            if (metadata == null) {
                return;
            }
            turn.responseId = metadata.getId();
            turn.responseModel = metadata.getModel();
            metadata.entrySet().forEach(entry -> turn.responseMetadata.put(entry.getKey(), entry.getValue()));
            Usage usage = metadata.getUsage();
            if (usage == null || usage instanceof EmptyUsage) {
                return;
            }
            turn.promptTokens = usage.getPromptTokens();
            turn.completionTokens = usage.getCompletionTokens();
            turn.totalTokens = usage.getTotalTokens();
            turn.outputTokenLimitReached = turn.completionTokens == null
                    ? null
                    : turn.completionTokens >= outputTokenLimit;
        }

        private static final class MutableProviderTurn {
            private final int sequence;
            private final List<String> orderedToolCallIds = new ArrayList<>();
            private final Map<String, Object> responseMetadata = new LinkedHashMap<>();
            private String assistantText;
            private String responseId;
            private String responseModel;
            private String finishReason;
            private Integer promptTokens;
            private Integer completionTokens;
            private Integer totalTokens;
            private Boolean outputTokenLimitReached;
            private long latencyMillis;
            private ToolCompatibilityProviderTurnState state = ToolCompatibilityProviderTurnState.COMPLETED;
            private String providerFailure;

            private MutableProviderTurn(int sequence) {
                this.sequence = sequence;
            }

            private ToolCompatibilityObservedProviderTurn snapshot() {
                return new ToolCompatibilityObservedProviderTurn(
                        sequence,
                        assistantText,
                        orderedToolCallIds,
                        responseId,
                        responseModel,
                        responseMetadata,
                        finishReason,
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        outputTokenLimitReached,
                        latencyMillis,
                        state,
                        providerFailure);
            }
        }

        private static final class MutableToolCall {
            private final int sequence;
            private final int providerTurnSequence;
            private final String callId;
            private final String type;
            private final String toolName;
            private final String rawArguments;
            private final boolean rawArgumentJsonValid;
            private final Boolean rawArgumentSchemaValid;
            private final ToolCompatibilitySchemaIssue rawArgumentIssue;
            private final Integer expectedCallSequence;
            private final Boolean expectedCallAtSequence;
            private final Boolean expectedArgumentsMatched;
            private Boolean callbackBindingSucceeded;
            private boolean callbackExecuted;
            private Boolean callbackSucceeded;
            private String callbackResponse;
            private ToolCompatibilityCallbackFailureKind callbackFailureKind;
            private String callbackFailure;

            private MutableToolCall(
                    int sequence,
                    int providerTurnSequence,
                    AssistantMessage.ToolCall toolCall,
                    ToolCompatibilitySchemaValidator.RawArgumentValidation validation,
                    Integer expectedCallSequence,
                    Boolean expectedCallAtSequence,
                    Boolean expectedArgumentsMatched
            ) {
                this.sequence = sequence;
                this.providerTurnSequence = providerTurnSequence;
                this.callId = toolCall.id();
                this.type = toolCall.type();
                this.toolName = toolCall.name();
                this.rawArguments = toolCall.arguments();
                this.rawArgumentJsonValid = validation.jsonValid();
                this.rawArgumentSchemaValid = validation.schemaValid();
                this.rawArgumentIssue = validation.issue();
                this.expectedCallSequence = expectedCallSequence;
                this.expectedCallAtSequence = expectedCallAtSequence;
                this.expectedArgumentsMatched = expectedArgumentsMatched;
            }

            private String callbackInput() {
                return rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments;
            }

            private void recordCallbackResponse(String response) {
                if (callbackResponse != null && !Objects.equals(callbackResponse, response)) {
                    throw new ToolCompatibilityProtocolIntegrityException(
                            "Callback response changed before the next provider turn");
                }
                callbackResponse = response;
            }

            private ToolCompatibilityObservedToolCall snapshot() {
                return new ToolCompatibilityObservedToolCall(
                        sequence,
                        providerTurnSequence,
                        callId,
                        type,
                        toolName,
                        rawArguments,
                        rawArgumentJsonValid,
                        rawArgumentSchemaValid,
                        rawArgumentIssue,
                        expectedCallSequence,
                        expectedCallAtSequence,
                        expectedArgumentsMatched,
                        callbackBindingSucceeded,
                        callbackExecuted,
                        callbackSucceeded,
                        callbackResponse,
                        callbackFailureKind,
                        callbackFailure);
            }
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
