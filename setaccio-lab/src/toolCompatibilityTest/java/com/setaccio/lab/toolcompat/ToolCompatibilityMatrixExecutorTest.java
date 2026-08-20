package com.setaccio.lab.toolcompat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCompatibilityMatrixExecutorTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "e".repeat(64));

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesTheExactSixteenRowsSequentiallyWithCanonicalSingleMultiNoMatchAndAbstentionCases() {
        RecordingSession session = new RecordingSession(false);

        ToolCompatibilityResult result = new ToolCompatibilityMatrixExecutor().execute(prepared(session));

        assertThat(session.createdModels()).hasSize(16);
        assertThat(result.rows()).hasSize(16);
        assertThat(result.rows())
                .extracting(ToolCompatibilityRow::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        assertThat(result.rows().subList(0, 8))
                .extracting(ToolCompatibilityRow::caseId)
                .containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(result.rows().subList(8, 16))
                .extracting(ToolCompatibilityRow::caseId)
                .containsExactlyElementsOf(ToolCompatibilityProtocol.CASE_IDS);
        assertThat(result.rows().subList(0, 8))
                .extracting(ToolCompatibilityRow::seed)
                .containsOnly(42);
        assertThat(result.rows().subList(8, 16))
                .extracting(ToolCompatibilityRow::seed)
                .containsOnly(43);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.attemptCount()).isOne();
            assertThat(row.rowAttemptCompleted()).isTrue();
            assertThat(row.exactCallSequenceMatched()).isTrue();
            assertThat(row.allExpectedArgumentsMatched()).isTrue();
            assertThat(row.caseContractPassed()).isTrue();
            assertThat(row.providerTurns())
                    .extracting(ToolCompatibilityProviderTurnEvidence::sequence)
                    .containsExactlyElementsOf(java.util.stream.IntStream
                            .rangeClosed(1, row.providerTurns().size()).boxed().toList());
            assertThat(row.providerTurns()).allSatisfy(turn -> {
                assertThat(turn.finishReason()).isNotBlank();
                assertThat(turn.usage().availability())
                        .isEqualTo(ToolCompatibilityUsageAvailability.COMPLETE);
                assertThat(turn.latency()).isGreaterThanOrEqualTo(Duration.ZERO);
                assertThat(turn.failure()).isNull();
            });
        });

        ToolCompatibilityRow singleStep = result.rows().getFirst();
        assertThat(singleStep.providerTurns()).hasSize(2);
        assertThat(singleStep.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.providerTurnSequence()).isOne();
            assertThat(call.toolResponseSequence()).isOne();
        });
        assertThat(singleStep.reasoningMarkerDetectedBeforeFirstToolCall()).isTrue();

        ToolCompatibilityRow multiStep = result.rows().get(4);
        assertThat(multiStep.providerTurns()).hasSize(3);
        assertThat(multiStep.toolCalls())
                .extracting(ToolCompatibilityToolCallEvidence::providerTurnSequence)
                .containsExactly(1, 2);
        assertThat(multiStep.toolResponses())
                .extracting(ToolCompatibilityToolResponseEvidence::toolCallSequence)
                .containsExactly(1, 2);
        assertThat(multiStep.providerTurns().get(1).outputLimitState())
                .isEqualTo(ToolCompatibilityOutputLimitState.REACHED);
        assertThat(multiStep.anyProviderTurnReachedOutputLimit()).isTrue();

        ToolCompatibilityRow noMatch = result.rows().get(5);
        assertThat(noMatch.toolResponses()).singleElement().satisfies(response ->
                assertThat(response.responseData()).contains("No catalog fixture matched"));

        ToolCompatibilityRow abstention = result.rows().get(6);
        assertThat(abstention.providerTurns()).singleElement();
        assertThat(abstention.toolCalls()).isEmpty();
        assertThat(abstention.toolResponses()).isEmpty();

        ToolCompatibilityRow expectedCallbackFailure = result.rows().get(7);
        assertThat(expectedCallbackFailure.toolResponses()).singleElement().satisfies(response -> {
            assertThat(response.callbackResultState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
            assertThat(response.failure().category())
                    .isEqualTo(ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE);
        });
        assertThat(session.createdModels()).allSatisfy(model ->
                assertThat(model.invocations()).isEqualTo(model.expectedProviderTurns()));
    }

    @Test
    void retainsAnInitialProviderFailureWithoutReplacementAndContinuesTheRemainingSchedule() {
        RecordingSession session = new RecordingSession(true);

        ToolCompatibilityResult result = new ToolCompatibilityMatrixExecutor().execute(prepared(session));

        assertThat(session.createdModels()).hasSize(16);
        assertThat(session.createdModels().getFirst().invocations()).isOne();
        assertThat(result.rows()).hasSize(16);
        assertThat(result.rows().getFirst()).satisfies(row -> {
            assertThat(row.attemptCount()).isOne();
            assertThat(row.rowAttemptCompleted()).isFalse();
            assertThat(row.providerTurns()).singleElement().satisfies(turn -> {
                assertThat(turn.sequence()).isOne();
                assertThat(turn.invocationState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
                assertThat(turn.failure().category())
                        .isEqualTo(ToolCompatibilityFailure.PROVIDER_FAILURE);
            });
        });
        assertThat(result.rows().subList(1, 16)).allSatisfy(row -> {
            assertThat(row.attemptCount()).isOne();
            assertThat(row.rowAttemptCompleted()).isTrue();
        });
    }

    private ToolCompatibilityPreflight.Prepared prepared(RecordingSession session) {
        return new ToolCompatibilityPreflight.Prepared(
                temporaryDirectory.resolve("not-allocated"),
                ToolCompatibilityProtocol.runSettings(),
                MODEL_IDENTITY,
                ToolCompatibilityCallbackCatalog.canonicalCallbacks(),
                session);
    }

    private static final class RecordingSession implements ToolCompatibilityPreflight.Session {

        private final List<ToolCompatibilityCaseSelection.ScheduledCase> schedule =
                ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings());
        private final List<CaseChatModel> createdModels = new ArrayList<>();
        private final boolean failFirstRow;

        private RecordingSession(boolean failFirstRow) {
            this.failFirstRow = failFirstRow;
        }

        @Override
        public ToolCompatibilityModelIdentity requireInstalled(String requestedModel) {
            throw new AssertionError("The prepared session must not repeat model preflight");
        }

        @Override
        public ToolCompatibilityControlledOllamaModel controlledModel(int seed) {
            ToolCompatibilityCaseSelection.ScheduledCase scheduled = schedule.get(createdModels.size());
            assertThat(seed).isEqualTo(scheduled.seed());
            CaseChatModel model = new CaseChatModel(
                    scheduled,
                    failFirstRow && scheduled.sequence() == 1);
            createdModels.add(model);
            return new ToolCompatibilityControlledOllamaModel(model, MODEL_IDENTITY);
        }

        private List<CaseChatModel> createdModels() {
            return List.copyOf(createdModels);
        }
    }

    private static final class CaseChatModel implements ChatModel {

        private final ToolCompatibilityCaseSelection.ScheduledCase scheduled;
        private final List<ToolCompatibilityExpectedCall> expectedCalls;
        private final boolean failInitialTurn;
        private int invocations;

        private CaseChatModel(
                ToolCompatibilityCaseSelection.ScheduledCase scheduled,
                boolean failInitialTurn
        ) {
            this.scheduled = scheduled;
            this.expectedCalls = ToolCompatibilityProtocol.caseOracle()
                    .requireCase(scheduled.caseId()).calls();
            this.failInitialTurn = failInitialTurn;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
            assertThat(options.getTemperature()).isZero();
            assertThat(options.getSeed()).isEqualTo(scheduled.seed());
            assertThat(options.getNumPredict()).isEqualTo(512);

            int turnIndex = invocations++;
            if (failInitialTurn && turnIndex == 0) {
                throw new IllegalStateException("synthetic initial provider failure");
            }
            if (turnIndex < expectedCalls.size()) {
                ToolCompatibilityExpectedCall expected = expectedCalls.get(turnIndex);
                String callId = "row-" + scheduled.sequence() + "-call-" + (turnIndex + 1);
                String assistantText = scheduled.sequence() == 1 && turnIndex == 0
                        ? "Thinking... selecting the deterministic arithmetic tool"
                        : "";
                AssistantMessage assistant = AssistantMessage.builder()
                        .content(assistantText)
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                callId,
                                "function",
                                expected.toolName(),
                                expected.arguments().toString())))
                        .build();
                int completionTokens = "catalog-multi-step".equals(scheduled.caseId())
                                && turnIndex == 1
                        ? 512
                        : 2;
                return response(assistant, "tool_calls", completionTokens, callId);
            }
            if (turnIndex == expectedCalls.size()) {
                return response(
                        new AssistantMessage(ToolCompatibilityAnalysisTestFixtures.finalText(
                                scheduled.caseId())),
                        "stop",
                        3,
                        "row-" + scheduled.sequence() + "-final");
            }
            throw new AssertionError("No provider replay or replacement is permitted");
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder()
                    .model(ToolCompatibilityProtocol.INITIAL_MODEL)
                    .build();
        }

        private int invocations() {
            return invocations;
        }

        private int expectedProviderTurns() {
            return failInitialTurn ? 1 : expectedCalls.size() + 1;
        }
    }

    private static ChatResponse response(
            AssistantMessage assistant,
            String finishReason,
            int completionTokens,
            String id
    ) {
        return new ChatResponse(
                List.of(new Generation(
                        assistant,
                        ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                ChatResponseMetadata.builder()
                        .id(id)
                        .model("fake-model")
                        .usage(new DefaultUsage(5, completionTokens))
                        .keyValue("provider", "fake")
                        .build());
    }
}
