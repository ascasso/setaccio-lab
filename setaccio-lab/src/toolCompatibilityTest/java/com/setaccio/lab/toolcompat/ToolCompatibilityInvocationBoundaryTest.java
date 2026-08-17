package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityInvocationBoundaryTest {

    @Test
    void buildsTheLockedNoPullOllamaModelWithoutContactingTheProvider() {
        OllamaApi api = ToolCompatibilityInvocationBoundary.createControlledOllamaApi("http://localhost:11434");
        ChatModel model = ToolCompatibilityInvocationBoundary.createControlledOllamaModel(
                api,
                new ToolCompatibilityModelIdentity(
                        ToolCompatibilityProtocol.INITIAL_MODEL,
                        ToolCompatibilityProtocol.INITIAL_MODEL,
                        "a".repeat(64)),
                42);

        assertThat(model.getOptions()).isInstanceOf(OllamaChatOptions.class);
        OllamaChatOptions options = (OllamaChatOptions) model.getOptions();
        assertThat(options.getModel()).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
        assertThat(options.getTemperature()).isZero();
        assertThat(options.getSeed()).isEqualTo(42);
        assertThat(options.getNumPredict()).isEqualTo(512);
    }

    @Test
    void observesEachStandardAdvisorTurnAndLinksTwoCallbacksToTheirOriginatingTurns() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedChatModel model = new ScriptedChatModel(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> response(
                    AssistantMessage.builder().content(null).toolCalls(List.of(toolCall(
                            "call-lookup", "lab_lookup_catalog_item", "{\"itemId\":\"fixture-invoice-sample\"}"))).build(),
                    "tool_calls", 11, 512, "turn-1");
            case 1 -> response(
                    AssistantMessage.builder().content("").toolCalls(List.of(toolCall(
                            "call-list", "lab_list_catalog_items", "{\"category\":\"document\"}"))).build(),
                    "tool_calls", 20, 5, "turn-2");
            case 2 -> response(new AssistantMessage("fixture-invoice-sample appears in the document catalog."),
                    "stop", 28, 7, "turn-3");
            default -> throw new AssertionError("The standard advisor retried or made an extra provider call");
        });

        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationBoundary().invoke(
                model,
                scheduled("catalog-multi-step"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(calls).hasValue(3);
        assertThat(trace.status()).isEqualTo(ToolCompatibilityInvocationStatus.COMPLETED);
        assertThat(trace.safeForNextSequentialAttempt()).isTrue();
        assertThat(trace.providerTurns()).hasSize(3);
        assertThat(trace.providerTurns())
                .extracting(ToolCompatibilityObservedProviderTurn::sequence)
                .containsExactly(1, 2, 3);
        assertThat(trace.providerTurns())
                .extracting(ToolCompatibilityObservedProviderTurn::assistantText)
                .containsExactly(null, "", "fixture-invoice-sample appears in the document catalog.");
        assertThat(trace.providerTurns().get(0).orderedToolCallIds()).containsExactly("call-lookup");
        assertThat(trace.providerTurns().get(1).orderedToolCallIds()).containsExactly("call-list");
        assertThat(trace.providerTurns())
                .extracting(ToolCompatibilityObservedProviderTurn::finishReason)
                .containsExactly("tool_calls", "tool_calls", "stop");
        assertThat(trace.providerTurns().get(0).responseMetadata()).containsEntry("provider", "fake");
        assertThat(trace.providerTurns().get(0).completionTokens()).isEqualTo(512);
        assertThat(trace.providerTurns().get(0).outputTokenLimitReached()).isTrue();
        assertThat(trace.toolCalls()).hasSize(2);
        assertThat(trace.toolCalls())
                .extracting(
                        ToolCompatibilityObservedToolCall::globalSequence,
                        ToolCompatibilityObservedToolCall::providerTurnSequence,
                        ToolCompatibilityObservedToolCall::callId,
                        ToolCompatibilityObservedToolCall::expectedCallSequence,
                        ToolCompatibilityObservedToolCall::expectedCallAtSequence,
                        ToolCompatibilityObservedToolCall::expectedArgumentsMatched,
                        ToolCompatibilityObservedToolCall::callbackBindingSucceeded,
                        ToolCompatibilityObservedToolCall::callbackExecuted,
                        ToolCompatibilityObservedToolCall::callbackSucceeded)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 1, "call-lookup", 1, true, true, true, true, true),
                        org.assertj.core.groups.Tuple.tuple(2, 2, "call-list", 2, true, true, true, true, true));
        assertThat(trace.toolCalls().getFirst().rawArguments())
                .isEqualTo("{\"itemId\":\"fixture-invoice-sample\"}");
    }

    @Test
    void retainsSchemaCoercionSeparatelyFromCallbackSuccess() {
        ScriptedChatModel model = twoTurnModel(
                toolCall("call-add", "lab_add_numbers", "{\"left\":\"17.25\",\"right\":\"4.75\"}"),
                "schema coercion observed");

        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationBoundary().invoke(
                model,
                scheduled("arithmetic-add"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(trace.status()).isEqualTo(ToolCompatibilityInvocationStatus.COMPLETED);
        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.rawArgumentJsonValid()).isTrue();
            assertThat(call.rawArgumentSchemaValid()).isFalse();
            assertThat(call.rawArgumentIssue()).isEqualTo(ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH);
            assertThat(call.expectedArgumentsMatched()).isFalse();
            assertThat(call.callbackBindingSucceeded()).isTrue();
            assertThat(call.callbackExecuted()).isTrue();
            assertThat(call.callbackSucceeded()).isTrue();
            assertThat(call.callbackResponse()).contains("22.00");
        });
    }

    @Test
    void retainsCallbackBindingFailureWithoutReplayingTheProviderTurn() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedChatModel model = new ScriptedChatModel(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> response(AssistantMessage.builder().content("").toolCalls(List.of(toolCall(
                    "call-add", "lab_add_numbers", "{\"left\":\"not-a-number\",\"right\":\"4.75\"}"))).build(),
                    "tool_calls", 4, 3, "binding-1");
            case 1 -> {
                assertThat(toolResponses(prompt)).singleElement();
                yield response(new AssistantMessage("The callback reported a binding error."), "stop", 6, 2, "binding-2");
            }
            default -> throw new AssertionError("No provider retry is permitted");
        });

        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationBoundary().invoke(
                model,
                scheduled("arithmetic-add"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(calls).hasValue(2);
        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.callbackBindingSucceeded()).isFalse();
            assertThat(call.callbackExecuted()).isTrue();
            assertThat(call.callbackSucceeded()).isFalse();
            assertThat(call.callbackFailureKind())
                    .isEqualTo(ToolCompatibilityCallbackFailureKind.CALLBACK_BINDING_FAILURE);
            assertThat(call.callbackResponse()).isNotBlank();
        });
    }

    @Test
    void retainsCallbackInvocationFailureSeparatelyFromBindingFailure() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedChatModel model = new ScriptedChatModel(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> response(AssistantMessage.builder().content("").toolCalls(List.of(toolCall(
                    "call-fail", "lab_fail_fixture", "{}"))).build(), "tool_calls", 3, 2, "failure-1");
            case 1 -> {
                assertThat(toolResponses(prompt)).singleElement();
                yield response(new AssistantMessage("The fixture tool returned an error."), "stop", 7, 4, "failure-2");
            }
            default -> throw new AssertionError("No provider retry is permitted");
        });

        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationBoundary().invoke(
                model,
                scheduled("deterministic-tool-failure"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(calls).hasValue(2);
        assertThat(trace.status()).isEqualTo(ToolCompatibilityInvocationStatus.COMPLETED);
        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.rawArgumentSchemaValid()).isTrue();
            assertThat(call.callbackBindingSucceeded()).isTrue();
            assertThat(call.callbackExecuted()).isTrue();
            assertThat(call.callbackSucceeded()).isFalse();
            assertThat(call.callbackFailureKind())
                    .isEqualTo(ToolCompatibilityCallbackFailureKind.CALLBACK_INVOCATION_FAILURE);
            assertThat(call.callbackResponse()).contains("fixture-tool-failure");
        });
    }

    @Test
    void retainsAProviderFailureOnALaterTurnWithoutAReplay() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedChatModel model = new ScriptedChatModel(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> response(AssistantMessage.builder().content("").toolCalls(List.of(toolCall(
                    "call-lookup", "lab_lookup_catalog_item", "{\"itemId\":\"fixture-invoice-sample\"}"))).build(),
                    "tool_calls", 4, 2, "provider-1");
            case 1 -> throw new IllegalStateException("later provider failure");
            default -> throw new AssertionError("No provider retry is permitted");
        });

        ToolCompatibilityInvocationTrace trace = new ToolCompatibilityInvocationBoundary().invoke(
                model,
                scheduled("catalog-multi-step"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(calls).hasValue(2);
        assertThat(trace.status()).isEqualTo(ToolCompatibilityInvocationStatus.PROVIDER_FAILURE);
        assertThat(trace.providerTurns()).hasSize(2);
        assertThat(trace.providerTurns().get(0).state()).isEqualTo(ToolCompatibilityProviderTurnState.COMPLETED);
        assertThat(trace.providerTurns().get(1).state()).isEqualTo(ToolCompatibilityProviderTurnState.PROVIDER_FAILURE);
        assertThat(trace.providerTurns().get(1).providerFailure()).contains("later provider failure");
        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.callbackSucceeded()).isTrue();
            assertThat(call.callbackResponse()).isNotBlank();
        });
    }

    @Test
    void preservesCompletedObservationsAndPreventsOverlapAfterAnInterruptedRowTimeout() throws Exception {
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maximumConcurrentCalls = new AtomicInteger();
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean secondModelStarted = new AtomicBoolean();
        AtomicInteger firstModelCalls = new AtomicInteger();
        ScriptedChatModel slowModel = new ScriptedChatModel(prompt -> switch (firstModelCalls.getAndIncrement()) {
            case 0 -> response(AssistantMessage.builder().content("").toolCalls(List.of(toolCall(
                    "call-add", "lab_add_numbers", "{\"left\":17.25,\"right\":4.75}"))).build(),
                    "tool_calls", 3, 2, "timeout-1");
            case 1 -> {
                activeCalls.incrementAndGet();
                maximumConcurrentCalls.accumulateAndGet(activeCalls.get(), Math::max);
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                    throw new AssertionError("The row deadline should interrupt this provider turn");
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw new IllegalStateException("interrupted provider turn", exception);
                } finally {
                    activeCalls.decrementAndGet();
                }
            }
            default -> throw new AssertionError("No provider retry is permitted");
        });
        ToolCompatibilityInvocationBoundary boundary = ToolCompatibilityInvocationBoundary.forProviderFreeDeadlineTest(
                Duration.ofMillis(80), Duration.ofSeconds(1));

        ToolCompatibilityInvocationTrace timedOut = boundary.invoke(
                slowModel,
                scheduled("arithmetic-add"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(timedOut.status()).isEqualTo(ToolCompatibilityInvocationStatus.ROW_TIMEOUT);
        assertThat(timedOut.safeForNextSequentialAttempt()).isTrue();
        assertThat(timedOut.providerTurns()).isNotEmpty();
        assertThat(timedOut.toolCalls()).singleElement().satisfies(call -> assertThat(call.callbackSucceeded()).isTrue());
        assertThat(activeCalls).hasValue(0);

        ScriptedChatModel nextModel = new ScriptedChatModel(prompt -> {
            assertThat(activeCalls).hasValue(0);
            secondModelStarted.set(true);
            return response(new AssistantMessage("next row"), "stop", 1, 1, "next-row");
        });
        ToolCompatibilityInvocationTrace next = boundary.invoke(
                nextModel,
                scheduled("no-applicable-domain-tool"),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());

        assertThat(secondModelStarted).isTrue();
        assertThat(maximumConcurrentCalls).hasValue(1);
        assertThat(next.status()).isEqualTo(ToolCompatibilityInvocationStatus.COMPLETED);
    }

    @Test
    void refusesTheNextSequentialAttemptWhenTimedOutProviderWorkIgnoresInterruption() throws Exception {
        CountDownLatch releaseBlockedCall = new CountDownLatch(1);
        CountDownLatch blockedCallStarted = new CountDownLatch(1);
        ScriptedChatModel uninterruptibleModel = new ScriptedChatModel(prompt -> {
            blockedCallStarted.countDown();
            while (releaseBlockedCall.getCount() > 0) {
                try {
                    releaseBlockedCall.await(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Deliberately model an unsafe provider call that ignores cancellation.
                }
            }
            return response(new AssistantMessage("late response"), "stop", 1, 1, "late");
        });
        ToolCompatibilityInvocationBoundary boundary = ToolCompatibilityInvocationBoundary.forProviderFreeDeadlineTest(
                Duration.ofMillis(30), Duration.ofMillis(40));

        try {
            ToolCompatibilityInvocationTrace trace = boundary.invoke(
                    uninterruptibleModel,
                    scheduled("no-applicable-domain-tool"),
                    ToolCompatibilityCallbackCatalog.canonicalCallbacks());

            assertThat(blockedCallStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(trace.status()).isEqualTo(ToolCompatibilityInvocationStatus.TIMEOUT_WORK_NOT_STOPPED);
            assertThat(trace.safeForNextSequentialAttempt()).isFalse();
            assertThatThrownBy(() -> boundary.invoke(
                    twoTurnModel(toolCall("unused", "lab_add_numbers", "{\"left\":1,\"right\":2}"), "unused"),
                    scheduled("arithmetic-add"),
                    ToolCompatibilityCallbackCatalog.canonicalCallbacks()))
                    .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                    .hasMessageContaining("did not stop safely");
        } finally {
            releaseBlockedCall.countDown();
        }
    }

    private static ToolCompatibilityCaseSelection.ScheduledCase scheduled(String caseId) {
        return new ToolCompatibilityCaseSelection.ScheduledCase(1, 1, 42, caseId);
    }

    private static ScriptedChatModel twoTurnModel(AssistantMessage.ToolCall toolCall, String finalText) {
        AtomicInteger calls = new AtomicInteger();
        return new ScriptedChatModel(prompt -> switch (calls.getAndIncrement()) {
            case 0 -> response(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                    "tool_calls", 4, 2, "tool-turn");
            case 1 -> response(new AssistantMessage(finalText), "stop", 6, 3, "final-turn");
            default -> throw new AssertionError("No provider retry is permitted");
        });
    }

    private static AssistantMessage.ToolCall toolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private static ChatResponse response(
            AssistantMessage assistant,
            String finishReason,
            int promptTokens,
            int completionTokens,
            String id
    ) {
        return new ChatResponse(
                List.of(new Generation(assistant, ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder()
                        .id(id)
                        .model("fake-model")
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .keyValue("provider", "fake")
                        .build());
    }

    private static List<ToolResponseMessage.ToolResponse> toolResponses(Prompt prompt) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        prompt.getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .forEach(message -> responses.addAll(message.getResponses()));
        return responses;
    }

    private static final class ScriptedChatModel implements ChatModel {

        private final Step step;

        private ScriptedChatModel(Step step) {
            this.step = step;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
            assertThat(options.getTemperature()).isZero();
            assertThat(options.getSeed()).isEqualTo(42);
            assertThat(options.getNumPredict()).isEqualTo(512);
            return step.respond(prompt);
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder().model(ToolCompatibilityProtocol.INITIAL_MODEL).build();
        }
    }

    @FunctionalInterface
    private interface Step {
        ChatResponse respond(Prompt prompt);
    }
}
