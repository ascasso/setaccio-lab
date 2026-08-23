package com.setaccio.lab.toolcompat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortExecutorTest {

    private static final String RUNTIME_VERSION = "0.32.15";
    private static final String PEER_ONE = "cohort-peer-one:1b";
    private static final String PEER_TWO = "cohort-peer-two:3b";
    private static final String REFERENCE = "cohort-reference:27b-mlx";
    private static final ToolCompatibilityHumanDecisionBinding BINDING =
            new ToolCompatibilityHumanDecisionBinding(
                    "baseline-run",
                    "candidate-run",
                    ToolCompatibilitySystemPromptCatalog.SHA256,
                    "e".repeat(64),
                    LocalDate.parse("2026-08-23"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesEveryModelMajorRowSequentiallyAndRetainsFailuresAndSeedSemantics()
            throws Exception {
        ToolCompatibilityCohortExecutionPlan plan = plan(temporaryDirectory);
        RecordingSession session = new RecordingSession(true);

        ToolCompatibilityCohortResult result =
                new ToolCompatibilityCohortExecutor().execute(plan, session);

        assertThat(result.modelRuns()).hasSize(3);
        assertThat(result.modelRuns())
                .extracting(run -> run.modelIdentity().requestedTag())
                .containsExactly(PEER_ONE, PEER_TWO, REFERENCE);
        assertThat(result.modelRuns()).allSatisfy(run -> assertThat(run.rows()).hasSize(16));
        assertThat(session.invocations()).hasSize(48);
        assertThat(session.invocations().subList(0, 16))
                .extracting(Invocation::modelTag)
                .containsOnly(PEER_ONE);
        assertThat(session.invocations().subList(16, 32))
                .extracting(Invocation::modelTag)
                .containsOnly(PEER_TWO);
        assertThat(session.invocations().subList(32, 48))
                .extracting(Invocation::modelTag)
                .containsOnly(REFERENCE);
        assertThat(session.invocations().subList(0, 16))
                .extracting(Invocation::effectiveSeed)
                .containsOnly(42, 43);
        assertThat(session.invocations().subList(16, 32))
                .extracting(Invocation::effectiveSeed)
                .containsOnlyNulls();
        assertThat(session.invocations())
                .extracting(Invocation::thinkOption)
                .containsOnlyNulls();

        ToolCompatibilityRow retainedFailure = result.modelRuns().getFirst().rows().getFirst();
        assertThat(retainedFailure.rowAttemptCompleted()).isFalse();
        assertThat(retainedFailure.failureCategory())
                .isEqualTo(ToolCompatibilityFailure.PROVIDER_FAILURE);
        assertThat(result.modelRuns().get(1).rows())
                .extracting(ToolCompatibilityRow::seed)
                .containsOnlyNulls();
        assertThat(result.modelRuns().get(2).rows()).hasSize(16);
        assertThat(result.orderedModels().get(1).metadata().thinkingMode().value())
                .isEqualTo("effective-default-unavailable");
        assertThat(plan.preflight().outputDirectory()).doesNotExist();
    }

    @Test
    void abortsOnRuntimeVersionDriftBeforeTheNextProviderTurn() throws Exception {
        ToolCompatibilityCohortExecutionPlan plan = plan(
                Files.createDirectory(temporaryDirectory.resolve("runtime-drift")));
        AtomicInteger runtimeReads = new AtomicInteger();
        AtomicInteger controlledModels = new AtomicInteger();
        ToolCompatibilityCohortExecutor.Session session =
                new ToolCompatibilityCohortExecutor.Session() {
                    @Override
                    public String ollamaRuntimeVersion() {
                        return runtimeReads.incrementAndGet() <= 2
                                ? RUNTIME_VERSION
                                : "0.32.16";
                    }

                    @Override
                    public ToolCompatibilityCohortControlledOllamaModel controlledModel(
                            ToolCompatibilityCohortModelIdentity identity,
                            Integer effectiveSeed
                    ) {
                        controlledModels.incrementAndGet();
                        return new ToolCompatibilityCohortControlledOllamaModel(
                                new FinalResponseModel(identity.requestedTag(), effectiveSeed, false, null),
                                identity,
                                effectiveSeed);
                    }
                };

        assertThatThrownBy(() -> new ToolCompatibilityCohortExecutor().execute(plan, session))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("runtime version drifted");
        assertThat(controlledModels).hasValue(1);
        assertThat(plan.preflight().outputDirectory()).doesNotExist();
    }

    @Test
    void scheduleIdentityBindsOrderMetadataAndUnsupportedSeedState() throws Exception {
        ToolCompatibilityCohortExecutionPlan plan = plan(
                Files.createDirectory(temporaryDirectory.resolve("schedule")));
        ToolCompatibilityCohortSchedule schedule = plan.schedule();

        assertThat(schedule.entries()).hasSize(48);
        assertThat(schedule.entries())
                .extracting(ToolCompatibilityCohortSchedule.Entry::globalSequence)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 48).boxed().toList());
        assertThat(schedule.entriesFor(plan.preflight().peers().get(1)))
                .extracting(ToolCompatibilityCohortSchedule.Entry::effectiveSeed)
                .containsOnlyNulls();
        assertThat(ToolCompatibilityCohortSchedule.create(
                RUNTIME_VERSION,
                plan.preflight().orderedModels()).sha256())
                .isEqualTo(schedule.sha256());
        assertThat(ToolCompatibilityCohortSchedule.create(
                "0.32.16",
                plan.preflight().orderedModels()).sha256())
                .isNotEqualTo(schedule.sha256());
    }

    private static ToolCompatibilityCohortExecutionPlan plan(Path parent) throws Exception {
        Path project = Files.createDirectory(parent.resolve("project"));
        ToolCompatibilityCohortPreflight.Prepared preflight =
                new ToolCompatibilityCohortPreflight().prepare(
                        new ToolCompatibilityCohortPreflight.Input(
                                project,
                                "http://localhost:11434",
                                List.of(PEER_ONE, PEER_TWO),
                                REFERENCE,
                                "build/tool-compatibility/2026-08-23-cohort"),
                        () -> new ToolCompatibilityCohortInventory(
                                RUNTIME_VERSION,
                                List.of(
                                        inventoryModel(
                                                PEER_ONE,
                                                "a".repeat(64),
                                                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                                                "thinking-not-advertised"),
                                        inventoryModel(
                                                PEER_TWO,
                                                "b".repeat(64),
                                                ToolCompatibilityCohortSeedSemantics.UNSUPPORTED,
                                                "effective-default-unavailable"),
                                        inventoryModel(
                                                REFERENCE,
                                                "c".repeat(64),
                                                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                                                "thinking-advertised-default-unavailable"))));
        ToolCompatibilityHumanDecision decision = new ToolCompatibilityHumanDecision(
                ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE,
                BINDING);
        return ToolCompatibilityCohortExecutionPlan.create(preflight, decision, BINDING);
    }

    private static ToolCompatibilityCohortInventoryModel inventoryModel(
            String tag,
            String digest,
            ToolCompatibilityCohortSeedSemantics seedSemantics,
            String thinkingMode
    ) {
        ToolCompatibilityMetadataField unavailable = ToolCompatibilityMetadataField.unavailable();
        return new ToolCompatibilityCohortInventoryModel(
                tag,
                digest,
                ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL,
                seedSemantics,
                new ToolCompatibilityCohortModelMetadata(
                        unavailable,
                        unavailable,
                        ToolCompatibilityMetadataField.available(
                                tag.endsWith("-mlx") ? "MLX" : "GGUF"),
                        unavailable,
                        unavailable,
                        unavailable,
                        ToolCompatibilityMetadataField.available("tools"),
                        ToolCompatibilityMetadataField.available(thinkingMode)));
    }

    private static final class RecordingSession implements ToolCompatibilityCohortExecutor.Session {

        private final List<Invocation> invocations = new ArrayList<>();
        private final boolean failFirstProviderTurn;

        private RecordingSession(boolean failFirstProviderTurn) {
            this.failFirstProviderTurn = failFirstProviderTurn;
        }

        @Override
        public String ollamaRuntimeVersion() {
            return RUNTIME_VERSION;
        }

        @Override
        public ToolCompatibilityCohortControlledOllamaModel controlledModel(
                ToolCompatibilityCohortModelIdentity modelIdentity,
                Integer effectiveSeed
        ) {
            boolean fail = failFirstProviderTurn && invocations.isEmpty();
            return new ToolCompatibilityCohortControlledOllamaModel(
                    new FinalResponseModel(
                            modelIdentity.requestedTag(),
                            effectiveSeed,
                            fail,
                            invocations),
                    modelIdentity,
                    effectiveSeed);
        }

        private List<Invocation> invocations() {
            return List.copyOf(invocations);
        }
    }

    private static final class FinalResponseModel implements ChatModel {

        private final String modelTag;
        private final Integer effectiveSeed;
        private final boolean fail;
        private final List<Invocation> invocations;

        private FinalResponseModel(
                String modelTag,
                Integer effectiveSeed,
                boolean fail,
                List<Invocation> invocations
        ) {
            this.modelTag = modelTag;
            this.effectiveSeed = effectiveSeed;
            this.fail = fail;
            this.invocations = invocations;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            Invocation invocation = new Invocation(
                    options.getModel(),
                    options.getSeed(),
                    options.getThinkOption());
            if (invocations != null) {
                invocations.add(invocation);
            }
            assertThat(options.getModel()).isEqualTo(modelTag);
            assertThat(options.getSeed()).isEqualTo(effectiveSeed);
            assertThat(options.getNumPredict())
                    .isEqualTo(ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN);
            assertThat(options.getTemperature()).isZero();
            assertThat(options.getThinkOption()).isNull();
            if (fail) {
                throw new IllegalStateException("synthetic first provider failure");
            }
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage("synthetic final response"),
                            ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder()
                            .id("synthetic-response")
                            .model(modelTag)
                            .usage(new DefaultUsage(5, 3))
                            .build());
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder().model(modelTag).build();
        }
    }

    private record Invocation(
            String modelTag,
            Integer effectiveSeed,
            org.springframework.ai.ollama.api.ThinkOption thinkOption
    ) {}
}
