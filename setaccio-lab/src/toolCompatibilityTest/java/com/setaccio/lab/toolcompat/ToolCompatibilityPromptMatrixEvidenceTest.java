package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPromptMatrixEvidenceTest {

    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityPromptMatrixEvidence evidence =
            new ToolCompatibilityPromptMatrixEvidence(OBJECT_MAPPER);

    @Test
    void writesAndVerifiesOneExactThreeArtifactConditionRunWithTheSharedSchedule() throws Exception {
        Fixture fixture = writeFixture("2026-08-21-baseline", ToolCompatibilityPromptCondition.UNTREATED);

        try (var paths = Files.list(fixture.runDirectory())) {
            assertThat(paths.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SUMMARY.md",
                            "manifest.json",
                            ToolCompatibilityPromptMatrixResult.RAW_FILENAME);
        }
        EvidenceManifest manifest = new EvidenceManifestStore(OBJECT_MAPPER).read(fixture.runDirectory());
        assertThat(manifest.suite()).isEqualTo(ToolCompatibilityPromptMatrixResult.SUITE);
        assertThat(manifest.executionEngine())
                .isEqualTo(ToolCompatibilityProtocol.EXECUTION_ENGINE);
        assertThat(manifest.codeBaseline()).isEqualTo(CLEAN_BASELINE);
        assertThat(manifest.artifacts()).extracting(artifact -> artifact.role())
                .containsExactly(
                        ToolCompatibilityPromptMatrixEvidence.RAW_ROLE,
                        ToolCompatibilityPromptMatrixEvidence.SUMMARY_ROLE);
        assertThat(manifest.settings())
                .containsEntry("promptCondition", "untreated")
                .containsKeys("systemPromptIdentity", "pairedExecutionSchedule", "orderedSchedule");
        assertThat(evidence.verify(fixture.runDirectory()).failures()).isEmpty();

        String raw = Files.readString(fixture.rawResult(), StandardCharsets.UTF_8);
        String summary = Files.readString(fixture.summary(), StandardCharsets.UTF_8);
        assertThat(raw)
                .contains("\"globalPairSequence\"")
                .contains("\"conditionExecutionPosition\"")
                .contains(ToolCompatibilityPairedSchedule.SHA256)
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());
        assertThat(summary)
                .contains("# Tool Compatibility Prompt-Matrix Deterministic Summary")
                .contains("- Prompt condition: `untreated`")
                .contains("- Paired schedule SHA-256: `" + ToolCompatibilityPairedSchedule.SHA256 + "`")
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());

        assertThatThrownBy(() -> evidence.write(
                fixture.runDirectory(),
                ToolCompatibilityPromptMatrixTestFixtures.result(
                        ToolCompatibilityPromptCondition.UNTREATED),
                CLEAN_BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be empty");
    }

    @Test
    void rejectsIncompleteEvidenceAndRegeneratesOnlyADriftedSummary() throws Exception {
        Path incompleteBaseline = Files.createDirectory(temporaryDirectory.resolve("2026-08-21-incomplete"));
        Path incompleteCandidate = Files.createDirectory(temporaryDirectory.resolve("2026-08-21-incomplete-candidate"));
        assertThat(evidence.verify(incompleteBaseline).valid()).isFalse();
        assertThat(evidence.verify(incompleteCandidate).valid()).isFalse();

        Fixture fixture = writeFixture("2026-08-21-prompted", ToolCompatibilityPromptCondition.PROMPTED);
        String expectedSummary = Files.readString(fixture.summary(), StandardCharsets.UTF_8);
        Files.writeString(fixture.summary(), "drifted", StandardCharsets.UTF_8);

        assertThat(evidence.verify(fixture.runDirectory()).valid()).isFalse();
        assertThat(evidence.reanalyze(fixture.runDirectory()).valid()).isTrue();
        assertThat(Files.readString(fixture.summary(), StandardCharsets.UTF_8)).isEqualTo(expectedSummary);
    }

    @Test
    void standaloneRunnerRoutesPromptMatrixEvidenceForVerificationAndReanalysis() throws Exception {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path root = project.resolve("build/tool-compatibility");
        Files.createDirectories(root);
        Path run = Files.createDirectory(root.resolve("prompt-matrix-offline-test-" + UUID.randomUUID()));
        try {
            evidence.write(
                    run,
                    ToolCompatibilityPromptMatrixTestFixtures.result(
                            ToolCompatibilityPromptCondition.UNTREATED),
                    CLEAN_BASELINE);
            String expectedSummary = Files.readString(
                    run.resolve(ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME),
                    StandardCharsets.UTF_8);
            String relative = project.relativize(run).toString();

            ToolCompatibilityOfflineRunner.main(new String[] {
                    "--mode", "verify", "--run-dir", relative
            });
            Files.writeString(
                    run.resolve(ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME),
                    "drifted",
                    StandardCharsets.UTF_8);
            ToolCompatibilityOfflineRunner.main(new String[] {
                    "--mode", "reanalyze", "--run-dir", relative
            });

            assertThat(evidence.verify(run).valid()).isTrue();
            assertThat(Files.readString(
                    run.resolve(ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME),
                    StandardCharsets.UTF_8)).isEqualTo(expectedSummary);
        } finally {
            deleteRecursively(run);
        }
    }

    private Fixture writeFixture(String runId, ToolCompatibilityPromptCondition condition) throws Exception {
        Path run = Files.createDirectory(temporaryDirectory.resolve(runId));
        Path manifest = evidence.write(
                run,
                ToolCompatibilityPromptMatrixTestFixtures.result(condition),
                CLEAN_BASELINE);
        return new Fixture(
                run,
                run.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME),
                run.resolve(ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME),
                manifest);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Fixture(
            Path runDirectory,
            Path rawResult,
            Path summary,
            Path manifest
    ) {}
}
