package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class LocalEvaluationEvidenceTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalEvaluationEvidence evidence = new LocalEvaluationEvidence(
            LocalEvaluationTestFixtures.OBJECT_MAPPER,
            LocalEvaluationTestFixtures.PROMPT,
            LocalEvaluationTestFixtures.CATALOG,
            LocalEvaluationTestFixtures.REVIEW);

    @Test
    void preservesThePreExecutionGitBaselineInManifestAndSummary() throws Exception {
        Path runDirectory = Files.createDirectory(temporaryDirectory.resolve("2026-08-03-dirty-baseline"));
        EvidenceCodeBaseline baseline = new EvidenceCodeBaseline("a".repeat(40), true);

        evidence.write(runDirectory, LocalEvaluationTestFixtures.successfulResult(), baseline);

        EvidenceManifest manifest = new EvidenceManifestStore(LocalEvaluationTestFixtures.OBJECT_MAPPER)
                .read(runDirectory);
        assertThat(manifest.codeBaseline()).isEqualTo(baseline);
        assertThat(Files.readString(runDirectory.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME)))
                .contains("Git commit: `" + "a".repeat(40) + "`")
                .contains("Evidence status: `diagnostic/non-final (dirty working tree)`");
        assertThat(evidence.verify(runDirectory).valid()).isTrue();
    }

    @Test
    void writesVerifiesAndDeterministicallyReanalyzesSharedVersionOneEvidence() throws Exception {
        Fixture fixture = writeFixture("2026-08-03-valid");
        EvidenceManifest manifest = new EvidenceManifestStore(LocalEvaluationTestFixtures.OBJECT_MAPPER)
                .read(fixture.runDirectory());

        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.suite()).isEqualTo(LocalEvaluationProtocol.SUITE);
        assertThat(manifest.executionEngine()).isEqualTo(LocalEvaluationProtocol.EXECUTION_ENGINE);
        assertThat(manifest.codeBaseline().gitCommit()).isNotBlank();
        assertThat(manifest.frameworkVersions().springBoot()).isNotBlank();
        assertThat(manifest.frameworkVersions().springAi()).isNotBlank();
        assertThat(manifest.artifacts())
                .extracting(artifact -> artifact.role())
                .containsExactly(LocalEvaluationEvidence.RAW_ROLE, LocalEvaluationEvidence.SUMMARY_ROLE);
        LocalEvaluationEvidence.OfflineResult initialVerification = evidence.verify(fixture.runDirectory());
        assertThat(initialVerification.valid())
                .as("initial verification failures: %s", initialVerification.failures())
                .isTrue();

        String raw = Files.readString(fixture.rawJson(), StandardCharsets.UTF_8);
        assertThat(raw)
                .contains("\"orderedSchedule\"")
                .contains("\"documentBlake3\"")
                .contains("\"claimBlake3\"")
                .contains("\"judgeModelIdentity\"")
                .contains("\"endpointCategory\" : \"local\"")
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("The Harbor Library opens")
                .doesNotContain("OLLAMA_BASE_URL")
                .doesNotContain("Authorization");

        Path summary = fixture.runDirectory().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary, StandardCharsets.UTF_8);
        Files.delete(summary);

        LocalEvaluationEvidence.OfflineResult reanalysis = evidence.reanalyze(fixture.runDirectory());
        assertThat(reanalysis.valid())
                .as("summary reanalysis failures: %s", reanalysis.failures())
                .isTrue();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(expected);
        LocalEvaluationEvidence.OfflineResult repaired = evidence.reanalyze(fixture.runDirectory());
        assertThat(repaired.valid())
                .as("summary repair failures: %s", repaired.failures())
                .isTrue();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(expected);

        assertThatThrownBy(() -> evidence.write(
                fixture.runDirectory(),
                LocalEvaluationTestFixtures.successfulResult()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raw local evaluation result");
    }

    @Test
    void rejectsTamperedMissingAndExtraArtifactsWithoutRewritingOnInvalidRawInput() throws Exception {
        Fixture tampered = writeFixture("2026-08-03-tampered");
        Path summary = tampered.runDirectory().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String originalSummary = Files.readString(summary, StandardCharsets.UTF_8);
        Files.writeString(tampered.rawJson(), "\n{\"tampered\":true}\n", StandardCharsets.UTF_8);

        assertThat(evidence.verify(tampered.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("SHA-256"));
        assertThat(evidence.reanalyze(tampered.runDirectory()).valid()).isFalse();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(originalSummary);

        Fixture missing = writeFixture("2026-08-03-missing");
        Files.delete(missing.rawJson());
        assertThat(evidence.verify(missing.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("missing"));
        assertThat(evidence.reanalyze(missing.runDirectory()).valid()).isFalse();

        Fixture extra = writeFixture("2026-08-03-extra");
        Files.writeString(extra.runDirectory().resolve("unexpected.txt"), "unexpected");
        assertThat(evidence.verify(extra.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
        assertThat(evidence.reanalyze(extra.runDirectory()).valid()).isFalse();
    }

    @Test
    void rejectsUnsafeArtifactPathsAndManifestProtocolDrift() throws Exception {
        Fixture unsafe = writeFixture("2026-08-03-unsafe");
        Path unsafeManifest = unsafe.runDirectory().resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        ObjectNode unsafeNode = (ObjectNode) LocalEvaluationTestFixtures.OBJECT_MAPPER
                .readTree(unsafeManifest.toFile());
        ArrayNode artifacts = (ArrayNode) unsafeNode.path("artifacts");
        ((ObjectNode) artifacts.get(0)).put("path", "../outside.json");
        LocalEvaluationTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(unsafeManifest.toFile(), unsafeNode);

        assertThat(evidence.verify(unsafe.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("manifest could not be loaded"));

        Fixture drifted = writeFixture("2026-08-03-manifest-drift");
        Path driftedManifest = drifted.runDirectory().resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        ObjectNode driftedNode = (ObjectNode) LocalEvaluationTestFixtures.OBJECT_MAPPER
                .readTree(driftedManifest.toFile());
        ((ObjectNode) driftedNode.path("settings")).put("promptSha256", "f".repeat(64));
        LocalEvaluationTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(driftedManifest.toFile(), driftedNode);

        assertThat(evidence.verify(drifted.runDirectory()).failures())
                .contains("Local evaluation manifest settings differ from the raw locked protocol.");
    }

    @Test
    void rejectsSummaryDriftAndRepairsItByteForByteOffline() throws Exception {
        Fixture fixture = writeFixture("2026-08-03-summary-drift");
        Path summary = fixture.runDirectory().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary, StandardCharsets.UTF_8);
        Files.writeString(summary, "\nchanged\n", StandardCharsets.UTF_8);

        assertThat(evidence.verify(fixture.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("summary"));

        LocalEvaluationEvidence.OfflineResult repairedSummary = evidence.reanalyze(fixture.runDirectory());
        assertThat(repairedSummary.valid())
                .as("summary drift repair failures: %s", repairedSummary.failures())
                .isTrue();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void sanitizesProviderErrorsBeforeTheyEnterSavedRows() {
        LocalEvaluationScheduleEntry schedule = LocalEvaluationProtocol
                .schedule(LocalEvaluationTestFixtures.CATALOG)
                .getFirst();
        LocalFactCheckJudgeResult providerFailure = new LocalFactCheckJudgeResult(
                schedule.fixtureId(),
                schedule.expectedVerdict(),
                LocalEvaluationTestFixtures.SETTINGS.judgeSettingsFor(schedule.repetition()),
                false,
                null,
                null,
                null,
                LocalFactCheckDiagnosticCategory.PROVIDER_FAILURE,
                null,
                null,
                null,
                null,
                null,
                12,
                1,
                "Connection refused at http://localhost:11434/api/chat");

        LocalEvaluationRow row = LocalEvaluationRow.from(schedule, providerFailure);

        assertThat(row.error()).isEqualTo("Judge provider invocation failed");
        assertThat(row.error()).doesNotContain("localhost", "http://");
    }

    @Test
    void offlineRunnerRejectsDirectoriesOutsideTheIgnoredEvidenceRoot() {
        assertThatThrownBy(() -> LocalEvaluationOfflineRunner.resolveRunDirectory(
                temporaryDirectory.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Run directory must be directly under build/evaluation-matrix/.");
    }

    @Test
    void offlineRunnerVerifiesAndReanalyzesSavedEvidenceWithoutStartingSpring() throws Exception {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/evaluation-matrix");
        Files.createDirectories(evidenceRoot);
        Path runDirectory = Files.createDirectory(evidenceRoot.resolve(
                "offline-test-" + UUID.randomUUID()));
        try {
            evidence.write(runDirectory, LocalEvaluationTestFixtures.successfulResult());
            String relativeRunDirectory = projectDirectory.relativize(runDirectory).toString();

            LocalEvaluationOfflineRunner.main(new String[] {
                    "--mode", "verify", "--run-dir", relativeRunDirectory
            });
            Files.writeString(
                    runDirectory.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME),
                    "drifted",
                    StandardCharsets.UTF_8);
            LocalEvaluationOfflineRunner.main(new String[] {
                    "--mode", "reanalyze", "--run-dir", relativeRunDirectory
            });

            assertThat(evidence.verify(runDirectory).valid()).isTrue();
        } finally {
            try (var paths = Files.walk(runDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Fixture writeFixture(String runId) throws Exception {
        Path runDirectory = Files.createDirectory(temporaryDirectory.resolve(runId));
        evidence.write(runDirectory, LocalEvaluationTestFixtures.successfulResult());
        return new Fixture(
                runDirectory,
                runDirectory.resolve(LocalEvaluationProtocol.RAW_FILENAME));
    }

    private record Fixture(Path runDirectory, Path rawJson) {}
}
