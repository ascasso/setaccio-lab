package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCompatibilityCohortEvidenceTest {

    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityCohortEvidence evidence =
            new ToolCompatibilityCohortEvidence(
                    ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);

    @Test
    void writesStrictSharedVersionOneEvidenceAndVerifiesOffline() throws Exception {
        Fixture fixture = writeFixture("2026-08-23-valid-cohort");

        try (var paths = Files.list(fixture.runDirectory())) {
            assertThat(paths.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SUMMARY.md",
                            "manifest.json",
                            ToolCompatibilityCohortResult.RAW_FILENAME);
        }
        EvidenceManifest manifest = new EvidenceManifestStore(
                ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER)
                .read(fixture.runDirectory());
        assertThat(manifest.manifestVersion()).isOne();
        assertThat(manifest.suite()).isEqualTo(ToolCompatibilityCohortResult.SUITE);
        assertThat(manifest.executionEngine())
                .isEqualTo(ToolCompatibilityProtocol.EXECUTION_ENGINE);
        assertThat(manifest.codeBaseline()).isEqualTo(CLEAN_BASELINE);
        assertThat(manifest.settings())
                .containsKeys(
                        "ollamaRuntimeVersion",
                        "humanDecision",
                        "cohortSchedule",
                        "orderedModels");
        assertThat(evidence.verify(fixture.runDirectory()).failures()).isEmpty();

        String raw = Files.readString(fixture.raw(), StandardCharsets.UTF_8);
        String summary = Files.readString(fixture.summary(), StandardCharsets.UTF_8);
        assertThat(raw)
                .contains("\"modelRuns\"")
                .contains("\"seedSemantics\"")
                .contains("\"thinkingMode\"")
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());
        assertThat(summary)
                .contains("# Tool Compatibility Cohort Multidimensional Summary")
                .contains("## Ordered Per-Model Analysis")
                .contains("fixture-peer:1b")
                .contains("fixture-reference:27b-mlx")
                .contains("This report produces no aggregate score")
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());
    }

    @Test
    void rejectsRawAndManifestTamperingAndRepairsOnlySummaryDrift() throws Exception {
        Fixture rawDrift = writeFixture("2026-08-23-raw-drift");
        Files.writeString(rawDrift.raw(), "{\"tampered\":true}", StandardCharsets.UTF_8);
        assertThat(evidence.verify(rawDrift.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("SHA-256") || failure.contains("size"));
        assertThat(evidence.reanalyze(rawDrift.runDirectory()).valid()).isFalse();

        Fixture settingsDrift = writeFixture("2026-08-23-settings-drift");
        ObjectNode manifest = (ObjectNode) ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER
                .readTree(settingsDrift.manifest().toFile());
        ((ObjectNode) manifest.path("settings"))
                .put("ollamaRuntimeVersion", "0.32.16");
        ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(settingsDrift.manifest().toFile(), manifest);
        assertThat(evidence.verify(settingsDrift.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("manifest settings"));
        assertThat(evidence.reanalyze(settingsDrift.runDirectory()).valid()).isFalse();

        Fixture summaryDrift = writeFixture("2026-08-23-summary-drift");
        String expected = Files.readString(summaryDrift.summary(), StandardCharsets.UTF_8);
        Files.writeString(summaryDrift.summary(), "drifted", StandardCharsets.UTF_8);
        assertThat(evidence.verify(summaryDrift.runDirectory()).valid()).isFalse();
        assertThat(evidence.reanalyze(summaryDrift.runDirectory()).failures()).isEmpty();
        assertThat(Files.readString(summaryDrift.summary(), StandardCharsets.UTF_8))
                .isEqualTo(expected);
    }

    @Test
    void sharedOfflineRunnerDetectsCohortEvidenceWithoutProviderAccess() throws Exception {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path root = project.resolve("build/tool-compatibility");
        Files.createDirectories(root);
        Path run = Files.createDirectory(root.resolve("cohort-offline-" + UUID.randomUUID()));
        try {
            evidence.write(run, ToolCompatibilityCohortTestFixtures.result(), CLEAN_BASELINE);
            String relative = project.relativize(run).toString();

            ToolCompatibilityOfflineRunner.main(new String[] {
                    "--mode", "verify", "--run-dir", relative
            });
            Files.writeString(
                    run.resolve(ToolCompatibilityCohortEvidence.SUMMARY_FILENAME),
                    "drifted",
                    StandardCharsets.UTF_8);
            ToolCompatibilityOfflineRunner.main(new String[] {
                    "--mode", "reanalyze", "--run-dir", relative
            });

            assertThat(evidence.verify(run).valid()).isTrue();
        } finally {
            deleteRecursively(run);
        }
    }

    private Fixture writeFixture(String runId) throws Exception {
        Path run = Files.createDirectory(temporaryDirectory.resolve(runId));
        Path manifest = evidence.write(
                run,
                ToolCompatibilityCohortTestFixtures.result(),
                CLEAN_BASELINE);
        return new Fixture(
                run,
                run.resolve(ToolCompatibilityCohortResult.RAW_FILENAME),
                run.resolve(ToolCompatibilityCohortEvidence.SUMMARY_FILENAME),
                manifest);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Fixture(Path runDirectory, Path raw, Path summary, Path manifest) {}
}
