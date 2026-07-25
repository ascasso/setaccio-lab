package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionMatrixEvidenceTest {

    @TempDir
    Path temporaryDirectory;

    private final VisionMatrixEvidence evidence = new VisionMatrixEvidence(
            VisionMatrixTestFixtures.OBJECT_MAPPER,
            VisionMatrixTestFixtures.PROMPT);

    @Test
    void writesVerifiesAndDeterministicallyReanalyzesVersionOneEvidence() throws Exception {
        Fixture fixture = writeFixture("2026-07-25-v1");
        EvidenceManifest manifest = new EvidenceManifestStore(VisionMatrixTestFixtures.OBJECT_MAPPER)
                .read(fixture.runDirectory());

        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.suite()).isEqualTo(VisionMatrixProtocol.SUITE);
        assertThat(manifest.executionEngine()).isEqualTo(VisionMatrixProtocol.EXECUTION_ENGINE);
        assertThat(manifest.artifacts())
                .extracting(artifact -> artifact.role())
                .containsExactly(VisionMatrixEvidence.RAW_ROLE, VisionMatrixEvidence.SUMMARY_ROLE);
        assertThat(evidence.verify(fixture.runDirectory()).valid()).isTrue();

        String raw = Files.readString(fixture.rawJson(), StandardCharsets.UTF_8);
        assertThat(raw)
                .doesNotContain("Private fixture observation")
                .doesNotContain("fixture concept")
                .doesNotContain("unsupported fixture detail")
                .doesNotContain("images/");

        Path summary = fixture.runDirectory().resolve(VisionMatrixEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary, StandardCharsets.UTF_8);
        Files.delete(summary);

        assertThat(evidence.reanalyze(fixture.runDirectory()).valid()).isTrue();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(evidence.reanalyze(fixture.runDirectory()).valid()).isTrue();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void rejectsTamperedOrMissingRawEvidenceWithoutChangingSummary() throws Exception {
        Fixture tampered = writeFixture("2026-07-25-tampered");
        Path summary = tampered.runDirectory().resolve(VisionMatrixEvidence.SUMMARY_FILENAME);
        String originalSummary = Files.readString(summary, StandardCharsets.UTF_8);
        Files.writeString(tampered.rawJson(), "\n{\"tampered\":true}\n", StandardCharsets.UTF_8);

        VisionMatrixEvidence.OfflineResult verification = evidence.verify(tampered.runDirectory());
        VisionMatrixEvidence.OfflineResult reanalysis = evidence.reanalyze(tampered.runDirectory());

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures()).anyMatch(failure -> failure.contains("SHA-256"));
        assertThat(reanalysis.valid()).isFalse();
        assertThat(Files.readString(summary, StandardCharsets.UTF_8)).isEqualTo(originalSummary);

        Fixture missing = writeFixture("2026-07-25-missing");
        Files.delete(missing.rawJson());
        assertThat(evidence.verify(missing.runDirectory()).valid()).isFalse();
        assertThat(evidence.reanalyze(missing.runDirectory()).valid()).isFalse();
    }

    @Test
    void rejectsManifestProtocolDriftAndUnexpectedArtifacts() throws Exception {
        Fixture fixture = writeFixture("2026-07-25-drift");
        Path manifestPath = fixture.runDirectory().resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        ObjectNode manifest = (ObjectNode) VisionMatrixTestFixtures.OBJECT_MAPPER
                .readTree(manifestPath.toFile());
        ArrayNode models = (ArrayNode) manifest.path("settings").path("models");
        models.set(0, VisionMatrixTestFixtures.OBJECT_MAPPER
                .getNodeFactory().textNode("drifted:model"));
        VisionMatrixTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(manifestPath.toFile(), manifest);
        Files.writeString(fixture.runDirectory().resolve("unexpected.txt"), "unexpected");

        VisionMatrixEvidence.OfflineResult verification = evidence.verify(fixture.runDirectory());

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures())
                .anyMatch(failure -> failure.contains("settings differ"))
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
    }

    @Test
    void summaryKeepsSemanticReviewAndAutomatedDimensionsDistinct() throws Exception {
        Fixture fixture = writeFixture("2026-07-25-summary");
        String summary = Files.readString(
                fixture.runDirectory().resolve(VisionMatrixEvidence.SUMMARY_FILENAME),
                StandardCharsets.UTF_8);

        assertThat(summary)
                .contains("## Invocation and structural outcomes")
                .contains("## Expected-observation review")
                .contains("## Unsupported-detail review")
                .contains("## Repetition consistency")
                .contains("## Token availability")
                .contains("## Latency")
                .contains("## Infrastructure failures")
                .contains("Not performed")
                .contains("does not calculate percentiles");
    }

    private Fixture writeFixture(String runId) throws Exception {
        Path corpusDirectory = temporaryDirectory.resolve(runId + "-corpus");
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                corpusDirectory,
                List.of("vision-one", "vision-two"));
        VisionMatrixResult result = VisionMatrixTestFixtures.successfulMatrix(
                corpus,
                List.of("model-a", "model-b"),
                null);
        VisionMatrixAnalyzer.MatrixAnalysis analysis =
                new VisionMatrixAnalyzer(VisionMatrixTestFixtures.PROMPT).analyze(result);
        Path runDirectory = Files.createDirectory(temporaryDirectory.resolve(runId));
        evidence.write(runDirectory, result, analysis);
        return new Fixture(
                runDirectory,
                runDirectory.resolve(VisionMatrixProtocol.RAW_FILENAME));
    }

    private record Fixture(Path runDirectory, Path rawJson) {}
}
