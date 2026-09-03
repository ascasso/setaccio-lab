package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPromptMatrixPreflightTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "f".repeat(64));
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    @Test
    void preflightsBothFreshOutputsAndTheCleanProtocolBeforeAnyAllocation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        AtomicInteger sessions = new AtomicInteger();

        ToolCompatibilityPromptMatrixPreflight.Prepared prepared =
                new ToolCompatibilityPromptMatrixPreflight().prepare(
                        input(project, "2026-08-21-baseline", "2026-08-21-prompted"),
                        ignored -> CLEAN_BASELINE,
                        (baseUrl, timeout) -> {
                            sessions.incrementAndGet();
                            assertThat(baseUrl).isEqualTo("http://localhost:11434");
                            assertThat(timeout).isEqualTo(Duration.ofMinutes(2));
                            return session();
                        });

        assertThat(sessions).hasValue(1);
        assertThat(prepared.codeBaseline()).isEqualTo(CLEAN_BASELINE);
        assertThat(prepared.settings()).isEqualTo(ToolCompatibilityProtocol.runSettings());
        assertThat(prepared.pairedSchedule()).isEqualTo(ToolCompatibilityPairedSchedule.locked());
        assertThat(prepared.baselineOutputDirectory()).doesNotExist();
        assertThat(prepared.candidateOutputDirectory()).doesNotExist();

        ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs outputs =
                ToolCompatibilityPromptMatrixPreflight.allocateBoth(prepared);
        assertThat(outputs.baseline()).isDirectory();
        assertThat(outputs.candidate()).isDirectory();
        assertThat(outputs.baseline()).isNotEqualTo(outputs.candidate());
    }

    @Test
    void rejectsEitherOutputCollisionAndDirtyRepositoryBeforeCreatingASession() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("strict-project"));
        Path existing = Files.createDirectories(project.resolve(
                "local/evidence/tool-compatibility/2026-08-21-existing"));
        AtomicInteger sessions = new AtomicInteger();
        ToolCompatibilityPreflight.SessionFactory factory = (baseUrl, timeout) -> {
            sessions.incrementAndGet();
            return session();
        };

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixPreflight().prepare(
                input(project, "2026-08-21-existing", "2026-08-21-prompted"),
                ignored -> CLEAN_BASELINE,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThat(existing).isDirectory();

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixPreflight().prepare(
                input(project, "2026-08-21-same", "2026-08-21-same"),
                ignored -> CLEAN_BASELINE,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");

        assertThatThrownBy(() -> new ToolCompatibilityPromptMatrixPreflight().prepare(
                input(project, "2026-08-21-clean", "2026-08-21-candidate"),
                ignored -> new EvidenceCodeBaseline("b".repeat(40), true),
                factory))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("clean Git commit");
        assertThat(sessions).hasValue(0);
    }

    @Test
    void refusesRepositoryDriftBeforeEitherOutputIsAllocated() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("drift-project"));
        AtomicInteger captures = new AtomicInteger();
        ToolCompatibilityPromptMatrixPreflight.Prepared prepared =
                new ToolCompatibilityPromptMatrixPreflight().prepare(
                        input(project, "2026-08-21-baseline", "2026-08-21-prompted"),
                        ignored -> captures.getAndIncrement() == 0
                                ? CLEAN_BASELINE
                                : new EvidenceCodeBaseline("b".repeat(40), false),
                        (baseUrl, timeout) -> session());

        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixPreflight.allocateBoth(prepared))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
        assertThat(prepared.baselineOutputDirectory()).doesNotExist();
        assertThat(prepared.candidateOutputDirectory()).doesNotExist();
    }

    @Test
    void leavesTheFirstAllocatedDirectoryIncompleteWhenRepositoryDriftPreventsTheSecond() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("allocation-drift-project"));
        AtomicInteger captures = new AtomicInteger();
        ToolCompatibilityPromptMatrixPreflight.Prepared prepared =
                new ToolCompatibilityPromptMatrixPreflight().prepare(
                        input(project, "2026-08-21-baseline", "2026-08-21-prompted"),
                        ignored -> captures.getAndIncrement() < 2
                                ? CLEAN_BASELINE
                                : new EvidenceCodeBaseline("b".repeat(40), false),
                        (baseUrl, timeout) -> session());

        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixPreflight.allocateBoth(prepared))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
        assertThat(prepared.baselineOutputDirectory()).isDirectory();
        assertThat(prepared.candidateOutputDirectory()).doesNotExist();
    }

    private static ToolCompatibilityPromptMatrixPreflight.Input input(
            Path project,
            String baselineRunId,
            String candidateRunId
    ) {
        return new ToolCompatibilityPromptMatrixPreflight.Input(
                project,
                "http://localhost:11434",
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "512",
                "PT2M",
                "local/evidence/tool-compatibility/" + baselineRunId,
                "local/evidence/tool-compatibility/" + candidateRunId);
    }

    private static ToolCompatibilityPreflight.Session session() {
        return new ToolCompatibilityPreflight.Session() {
            @Override
            public ToolCompatibilityModelIdentity requireInstalled(String requestedModel) {
                assertThat(requestedModel).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
                return MODEL_IDENTITY;
            }

            @Override
            public ToolCompatibilityControlledOllamaModel controlledModel(int seed) {
                throw new AssertionError("Preflight must not execute a model");
            }
        };
    }
}
