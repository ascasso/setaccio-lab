package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
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

class ToolCompatibilityPromptMatrixExecutorTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "e".repeat(64));
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesTheExactInterleavedScheduleAndInjectsOnlyThePromptedSystemMessage() {
        RecordingSession session = new RecordingSession(null);

        ToolCompatibilityPromptMatrixExecutor.Execution execution =
                new ToolCompatibilityPromptMatrixExecutor().execute(prepared(
                        session,
                        ignored -> CLEAN_BASELINE));

        ToolCompatibilityPairedSchedule schedule = ToolCompatibilityPairedSchedule.locked();
        assertThat(session.createdModels()).hasSize(32);
        assertThat(session.createdModels())
                .extracting(RecordingChatModel::entry)
                .containsExactlyElementsOf(schedule.entries());
        assertThat(session.createdModels()).allSatisfy(model -> assertThat(model.invocations()).isOne());
        assertThat(execution.untreated().rows()).hasSize(16);
        assertThat(execution.prompted().rows()).hasSize(16);
        assertThat(execution.untreated().rows())
                .extracting(ToolCompatibilityRow::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());
        assertThat(execution.prompted().rows())
                .extracting(ToolCompatibilityRow::sequence)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 16).boxed().toList());

        assertThat(execution.untreated().rows()).allSatisfy(row -> {
            ToolCompatibilityPairedSchedule.Entry entry = schedule.requireEntry(
                    ToolCompatibilityPromptCondition.UNTREATED, row.sequence());
            assertThat(row.attemptCount()).isOne();
            assertThat(row.globalPairSequence()).isEqualTo(entry.globalPairSequence());
            assertThat(row.conditionExecutionPosition()).isEqualTo(entry.conditionExecutionPosition());
            assertThat(row.systemPromptId()).isEqualTo(
                    ToolCompatibilitySystemPromptIdentity.UNTREATED_ID);
        });
        assertThat(execution.prompted().rows()).allSatisfy(row -> {
            ToolCompatibilityPairedSchedule.Entry entry = schedule.requireEntry(
                    ToolCompatibilityPromptCondition.PROMPTED, row.sequence());
            assertThat(row.attemptCount()).isOne();
            assertThat(row.globalPairSequence()).isEqualTo(entry.globalPairSequence());
            assertThat(row.conditionExecutionPosition()).isEqualTo(entry.conditionExecutionPosition());
            assertThat(row.systemPromptId()).isEqualTo(
                    ToolCompatibilitySystemPromptIdentity.DISCIPLINE_ID);
        });
        assertThat(execution.untreated().pairedExecutionSchedule())
                .isEqualTo(execution.prompted().pairedExecutionSchedule());
        assertThat(execution.untreated().modelIdentity())
                .isEqualTo(execution.prompted().modelIdentity());
        assertThat(execution.untreated().runSettings())
                .isEqualTo(execution.prompted().runSettings());
        assertThat(execution.untreated().orderedSchedule())
                .isEqualTo(execution.prompted().orderedSchedule());
        assertThat(execution.untreated().systemPromptIdentity())
                .isNotEqualTo(execution.prompted().systemPromptIdentity());
    }

    @Test
    void retainsOneProviderFailureWithoutReplacingTheLogicalRowOrChangingTheSchedule() {
        RecordingSession session = new RecordingSession(2);

        ToolCompatibilityPromptMatrixExecutor.Execution execution =
                new ToolCompatibilityPromptMatrixExecutor().execute(prepared(
                        session,
                        ignored -> CLEAN_BASELINE));

        assertThat(session.createdModels()).hasSize(32);
        assertThat(session.createdModels()).allSatisfy(model -> assertThat(model.invocations()).isOne());
        ToolCompatibilityRow failedCandidate = execution.prompted().rows().getFirst();
        assertThat(failedCandidate.globalPairSequence()).isEqualTo(2);
        assertThat(failedCandidate.attemptCount()).isOne();
        assertThat(failedCandidate.rowAttemptCompleted()).isFalse();
        assertThat(failedCandidate.providerTurns()).singleElement().satisfies(turn -> {
            assertThat(turn.sequence()).isOne();
            assertThat(turn.invocationState()).isEqualTo(ToolCompatibilityEvidenceState.FAILED);
            assertThat(turn.failure().category())
                    .isEqualTo(ToolCompatibilityFailure.PROVIDER_FAILURE);
        });
        assertThat(execution.untreated().rows()).hasSize(16);
        assertThat(execution.prompted().rows()).hasSize(16);
    }

    @Test
    void abortsBeforeTheNextLogicalRowWhenTheRepositoryDrifts() {
        RecordingSession session = new RecordingSession(null);
        AtomicInteger captures = new AtomicInteger();

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixExecutor().execute(prepared(
                session,
                ignored -> captures.getAndIncrement() == 0
                        ? CLEAN_BASELINE
                        : new EvidenceCodeBaseline("b".repeat(40), false))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
        assertThat(session.createdModels()).hasSize(1);
    }

    @Test
    void leavesBothConditionRunsIncompleteWhenTheRepositoryDriftsBeforeManifestFinalization()
            throws Exception {
        RecordingSession session = new RecordingSession(null);
        AtomicInteger captures = new AtomicInteger();
        ToolCompatibilityPromptMatrixPreflight.Prepared prepared = prepared(
                session,
                ignored -> captures.getAndIncrement() < 32
                        ? CLEAN_BASELINE
                        : new EvidenceCodeBaseline("b".repeat(40), false));
        Path baseline = Files.createDirectory(temporaryDirectory.resolve("baseline"));
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate"));
        ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs outputs =
                new ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs(
                        baseline,
                        candidate);

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixOrchestrator().executeAndWrite(
                prepared,
                outputs,
                new ToolCompatibilityPromptMatrixEvidence(
                        JsonMapper.builder().findAndAddModules().build())))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
        assertThat(session.createdModels()).hasSize(32);
        assertThat(baseline.resolve("manifest.json")).doesNotExist();
        assertThat(candidate.resolve("manifest.json")).doesNotExist();
        assertThat(baseline.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME)).isRegularFile();
        assertThat(candidate.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME)).isRegularFile();
    }

    @Test
    void invalidatesTheFirstManifestWhenRepositoryDriftPreventsTheSecondFinalization()
            throws Exception {
        RecordingSession session = new RecordingSession(null);
        AtomicInteger captures = new AtomicInteger();
        ToolCompatibilityPromptMatrixPreflight.Prepared prepared = prepared(
                session,
                ignored -> captures.getAndIncrement() < 33
                        ? CLEAN_BASELINE
                        : new EvidenceCodeBaseline("b".repeat(40), false));
        Path baseline = Files.createDirectory(temporaryDirectory.resolve("baseline-after-first"));
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate-after-first"));

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixOrchestrator().executeAndWrite(
                prepared,
                new ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs(baseline, candidate),
                new ToolCompatibilityPromptMatrixEvidence(
                        JsonMapper.builder().findAndAddModules().build())))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
        assertThat(baseline.resolve("manifest.json")).doesNotExist();
        assertThat(candidate.resolve("manifest.json")).doesNotExist();
        assertThat(baseline.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME)).isRegularFile();
        assertThat(candidate.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME)).isRegularFile();
    }

    private ToolCompatibilityPromptMatrixPreflight.Prepared prepared(
            RecordingSession session,
            ToolCompatibilityPromptMatrixPreflight.RepositoryState repositoryState
    ) {
        return new ToolCompatibilityPromptMatrixPreflight.Prepared(
                temporaryDirectory,
                temporaryDirectory.resolve("baseline"),
                temporaryDirectory.resolve("candidate"),
                ToolCompatibilityProtocol.runSettings(),
                MODEL_IDENTITY,
                ToolCompatibilityCallbackCatalog.canonicalCallbacks(),
                ToolCompatibilityProtocol.systemPromptCatalog(),
                ToolCompatibilityPairedSchedule.locked(),
                CLEAN_BASELINE,
                repositoryState,
                session);
    }

    private static final class RecordingSession implements ToolCompatibilityPreflight.Session {

        private final List<RecordingChatModel> createdModels = new ArrayList<>();
        private final Integer failureGlobalPairSequence;

        private RecordingSession(Integer failureGlobalPairSequence) {
            this.failureGlobalPairSequence = failureGlobalPairSequence;
        }

        @Override
        public ToolCompatibilityModelIdentity requireInstalled(String requestedModel) {
            throw new AssertionError("Prepared execution must not repeat model preflight");
        }

        @Override
        public ToolCompatibilityControlledOllamaModel controlledModel(int seed) {
            ToolCompatibilityPairedSchedule.Entry entry = ToolCompatibilityPairedSchedule.locked()
                    .entries().get(createdModels.size());
            assertThat(seed).isEqualTo(entry.seed());
            RecordingChatModel model = new RecordingChatModel(
                    entry,
                    failureGlobalPairSequence != null
                            && failureGlobalPairSequence == entry.globalPairSequence());
            createdModels.add(model);
            return new ToolCompatibilityControlledOllamaModel(model, MODEL_IDENTITY);
        }

        private List<RecordingChatModel> createdModels() {
            return List.copyOf(createdModels);
        }
    }

    private static final class RecordingChatModel implements ChatModel {

        private final ToolCompatibilityPairedSchedule.Entry entry;
        private final boolean fail;
        private int invocations;

        private RecordingChatModel(ToolCompatibilityPairedSchedule.Entry entry, boolean fail) {
            this.entry = entry;
            this.fail = fail;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
            assertThat(options.getTemperature()).isZero();
            assertThat(options.getSeed()).isEqualTo(entry.seed());
            assertThat(options.getNumPredict()).isEqualTo(512);
            List<SystemMessage> systemMessages = prompt.getInstructions().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .toList();
            if (entry.condition() == ToolCompatibilityPromptCondition.UNTREATED) {
                assertThat(systemMessages).isEmpty();
            } else {
                assertThat(systemMessages).singleElement().satisfies(message -> assertThat(message.getText())
                        .isEqualTo(ToolCompatibilityProtocol.systemPromptCatalog().toolDiscipline().text()));
            }
            invocations++;
            if (fail) {
                throw new IllegalStateException("synthetic paired provider failure");
            }
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage("synthetic final response"),
                            ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder()
                            .id("pair-" + entry.globalPairSequence())
                            .model("fake-model")
                            .usage(new DefaultUsage(5, 3))
                            .build());
        }

        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder()
                    .model(ToolCompatibilityProtocol.INITIAL_MODEL)
                    .build();
        }

        private ToolCompatibilityPairedSchedule.Entry entry() {
            return entry;
        }

        private int invocations() {
            return invocations;
        }
    }
}
