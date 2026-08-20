package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class ToolCompatibilityEvidenceTest {

    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityEvidence evidence =
            new ToolCompatibilityEvidence(OBJECT_MAPPER);

    @Test
    void writesExactlyThreeSharedVersionOneArtifactsAndVerifiesOffline() throws Exception {
        Fixture fixture = writeFixture("2026-08-18-valid");

        assertThat(fixture.manifest()).isEqualTo(
                fixture.runDirectory().resolve(EvidenceManifestStore.MANIFEST_FILENAME));
        try (var paths = Files.list(fixture.runDirectory())) {
            assertThat(paths.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "SUMMARY.md",
                            "manifest.json",
                            "tool-compatibility-results.json");
        }
        EvidenceManifest manifest = new EvidenceManifestStore(OBJECT_MAPPER)
                .read(fixture.runDirectory());
        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.suite()).isEqualTo(ToolCompatibilityProtocol.SUITE);
        assertThat(manifest.executionEngine())
                .isEqualTo(ToolCompatibilityProtocol.EXECUTION_ENGINE);
        assertThat(manifest.codeBaseline()).isEqualTo(CLEAN_BASELINE);
        assertThat(manifest.frameworkVersions().springBoot()).isNotBlank();
        assertThat(manifest.frameworkVersions().springAi()).isNotBlank();
        assertThat(manifest.artifacts())
                .extracting(artifact -> artifact.role())
                .containsExactly(
                        ToolCompatibilityEvidence.RAW_ROLE,
                        ToolCompatibilityEvidence.SUMMARY_ROLE);
        assertThat(manifest.settings())
                .containsEntry("protocolVersion", ToolCompatibilityProtocol.VERSION)
                .containsKeys(
                        "runSettings",
                        "modelIdentity",
                        "systemPromptIdentity",
                        "caseOracleSha256",
                        "toolDefinitionsSha256",
                        "orderedSchedule");
        assertThat(evidence.verify(fixture.runDirectory()).failures()).isEmpty();

        String raw = Files.readString(fixture.rawResult(), StandardCharsets.UTF_8);
        String summary = Files.readString(fixture.summary(), StandardCharsets.UTF_8);
        String manifestText = Files.readString(fixture.manifest(), StandardCharsets.UTF_8);
        assertThat(raw)
                .contains("\"providerTurns\"")
                .contains("\"toolCalls\"")
                .contains("\"toolResponses\"")
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());
        assertThat(summary)
                .contains("# Tool Compatibility Deterministic Summary")
                .contains("## Evidence")
                .contains("- Raw result: `tool-compatibility-results.json`")
                .contains("- Git commit: `" + "a".repeat(40) + "`")
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());
        assertThat(manifestText)
                .doesNotContain("http://", "https://", "localhost", temporaryDirectory.toString());

        assertThatThrownBy(() -> evidence.write(
                fixture.runDirectory(),
                ToolCompatibilityAnalysisTestFixtures.successfulResult(),
                CLEAN_BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be empty");
    }

    @Test
    void rejectsTamperedMissingEmptyAndUnexpectedArtifactsWithoutReanalysis() throws Exception {
        Fixture tampered = writeFixture("2026-08-18-tampered");
        String originalSummary = Files.readString(tampered.summary(), StandardCharsets.UTF_8);
        Files.writeString(tampered.rawResult(), "{\"tampered\":true}", StandardCharsets.UTF_8);
        assertThat(evidence.verify(tampered.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("SHA-256") || failure.contains("size"));
        assertThat(evidence.reanalyze(tampered.runDirectory()).valid()).isFalse();
        assertThat(Files.readString(tampered.summary(), StandardCharsets.UTF_8))
                .isEqualTo(originalSummary);

        Fixture missing = writeFixture("2026-08-18-missing");
        Files.delete(missing.rawResult());
        assertThat(evidence.verify(missing.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("missing"));
        assertThat(evidence.reanalyze(missing.runDirectory()).valid()).isFalse();

        Fixture empty = writeFixture("2026-08-18-empty");
        Files.write(empty.rawResult(), new byte[0]);
        assertThat(evidence.verify(empty.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("empty"));

        Fixture extra = writeFixture("2026-08-18-extra");
        Files.writeString(extra.runDirectory().resolve("unexpected.txt"), "unexpected");
        assertThat(evidence.verify(extra.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
        assertThat(evidence.reanalyze(extra.runDirectory()).valid()).isFalse();
    }

    @Test
    void rejectsUnsafeManifestPathsAndSymbolicLinkArtifacts() throws Exception {
        Fixture unsafe = writeFixture("2026-08-18-unsafe-path");
        ObjectNode manifest = (ObjectNode) OBJECT_MAPPER.readTree(unsafe.manifest().toFile());
        ArrayNode artifacts = (ArrayNode) manifest.path("artifacts");
        ((ObjectNode) artifacts.get(0)).put("path", "../outside.json");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(unsafe.manifest().toFile(), manifest);
        assertThat(evidence.verify(unsafe.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("manifest could not be loaded"));

        Fixture linked = writeFixture("2026-08-18-linked-artifact");
        Path outside = temporaryDirectory.resolve("outside.json");
        Files.writeString(outside, "outside");
        Files.delete(linked.rawResult());
        Files.createSymbolicLink(linked.rawResult(), outside);
        assertThat(evidence.verify(linked.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("unsafe")
                        || failure.contains("symbolic link"));
        assertThat(evidence.reanalyze(linked.runDirectory()).valid()).isFalse();
    }

    @Test
    void rejectsManifestSettingsDriftAndRepairsOnlySummaryDriftByteForByte() throws Exception {
        Fixture settingsDrift = writeFixture("2026-08-18-settings-drift");
        ObjectNode manifest = (ObjectNode) OBJECT_MAPPER
                .readTree(settingsDrift.manifest().toFile());
        ((ObjectNode) manifest.path("settings").path("runSettings"))
                .put("maxOutputTokensPerProviderTurn", 511);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(settingsDrift.manifest().toFile(), manifest);
        assertThat(evidence.verify(settingsDrift.runDirectory()).failures())
                .contains("Tool compatibility manifest settings differ from the raw locked protocol.");
        assertThat(evidence.reanalyze(settingsDrift.runDirectory()).valid()).isFalse();

        Fixture summaryDrift = writeFixture("2026-08-18-summary-drift");
        String expected = Files.readString(summaryDrift.summary(), StandardCharsets.UTF_8);
        Files.writeString(summaryDrift.summary(), "drifted", StandardCharsets.UTF_8);
        assertThat(evidence.verify(summaryDrift.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("summary"));
        assertThat(evidence.reanalyze(summaryDrift.runDirectory()).failures()).isEmpty();
        assertThat(Files.readString(summaryDrift.summary(), StandardCharsets.UTF_8))
                .isEqualTo(expected);

        Files.delete(summaryDrift.summary());
        assertThat(evidence.reanalyze(summaryDrift.runDirectory()).failures()).isEmpty();
        assertThat(Files.readString(summaryDrift.summary(), StandardCharsets.UTF_8))
                .isEqualTo(expected);
    }

    @Test
    void standaloneRunnerVerifiesAndReanalyzesWithoutStartingSpring() throws Exception {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path root = project.resolve("build/tool-compatibility");
        Files.createDirectories(root);
        Path run = Files.createDirectory(root.resolve("offline-test-" + UUID.randomUUID()));
        try {
            evidence.write(
                    run,
                    ToolCompatibilityAnalysisTestFixtures.successfulResult(),
                    CLEAN_BASELINE);
            String relative = project.relativize(run).toString();

            ToolCompatibilityOfflineRunner.main(new String[] {
                    "--mode", "verify", "--run-dir", relative
            });
            Files.writeString(
                    run.resolve(ToolCompatibilityEvidence.SUMMARY_FILENAME),
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

    @Test
    void offlineRunnerRejectsDirectoriesOutsideTheIgnoredEvidenceRoot() {
        assertThatThrownBy(() -> ToolCompatibilityOfflineRunner.resolveRunDirectory(
                Path.of(""), temporaryDirectory.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directly under build/tool-compatibility");
    }

    private Fixture writeFixture(String runId) throws Exception {
        Path run = Files.createDirectory(temporaryDirectory.resolve(runId));
        Path manifest = evidence.write(
                run,
                ToolCompatibilityAnalysisTestFixtures.successfulResult(),
                CLEAN_BASELINE);
        return new Fixture(
                run,
                run.resolve(ToolCompatibilityProtocol.RAW_FILENAME),
                run.resolve(ToolCompatibilityEvidence.SUMMARY_FILENAME),
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
