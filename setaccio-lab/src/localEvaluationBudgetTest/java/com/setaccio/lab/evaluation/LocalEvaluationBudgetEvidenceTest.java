package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvaluationBudgetEvidenceTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalEvaluationBudgetEvidence evidence = new LocalEvaluationBudgetEvidence(
            LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
            LocalEvaluationBudgetTestFixtures.PROMPT,
            LocalEvaluationBudgetTestFixtures.CATALOG,
            LocalEvaluationBudgetTestFixtures.REVIEW);

    @Test
    void writesVerifiesAndReanalyzesBothArmsAsOneProtocolPair() throws Exception {
        Pair pair = writePair("valid");
        assertThat(evidence.verifyPair(pair.budget64(), pair.budget256()).valid()).isTrue();

        Path summary64 = pair.budget64().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String expectedSummary = Files.readString(summary64, StandardCharsets.UTF_8);
        Files.delete(summary64);

        LocalEvaluationBudgetEvidence.OfflinePairResult reanalysis = evidence.reanalyzePair(
                pair.budget64(),
                pair.budget256());
        assertThat(reanalysis.valid())
                .as("pair reanalysis failures: %s", reanalysis.failures())
                .isTrue();
        assertThat(Files.readString(summary64, StandardCharsets.UTF_8)).isEqualTo(expectedSummary);
        assertThat(evidence.verifyPair(pair.budget64(), pair.budget256()).valid()).isTrue();
    }

    @Test
    void rejectsTamperedRawInputAndDoesNotRewriteAnInvalidArm() throws Exception {
        Pair pair = writePair("tampered");
        Path raw64 = pair.budget64().resolve(LocalEvaluationProtocol.RAW_FILENAME);
        Path summary64 = pair.budget64().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String originalSummary = Files.readString(summary64, StandardCharsets.UTF_8);
        Files.writeString(raw64, "\n{\"tampered\":true}\n", StandardCharsets.UTF_8);

        LocalEvaluationBudgetEvidence.OfflinePairResult verification = evidence.verifyPair(
                pair.budget64(),
                pair.budget256());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.failures())
                .anyMatch(failure -> failure.contains("64-token arm") && failure.contains("SHA-256"));

        LocalEvaluationBudgetEvidence.OfflinePairResult reanalysis = evidence.reanalyzePair(
                pair.budget64(),
                pair.budget256());
        assertThat(reanalysis.valid()).isFalse();
        assertThat(Files.readString(summary64, StandardCharsets.UTF_8)).isEqualTo(originalSummary);
    }

    @Test
    void rejectsUnexpectedArtifactsAndPairGitBaselineDrift() throws Exception {
        Pair extra = writePair("extra");
        Files.writeString(extra.budget256().resolve("unexpected.txt"), "unexpected");

        LocalEvaluationBudgetEvidence.OfflinePairResult extraResult = evidence.verifyPair(
                extra.budget64(),
                extra.budget256());
        assertThat(extraResult.valid()).isFalse();
        assertThat(extraResult.failures())
                .anyMatch(failure -> failure.contains("256-token arm") && failure.contains("Unexpected artifact"));

        Path drift64 = Files.createDirectory(temporaryDirectory.resolve("drift-64"));
        Path drift256 = Files.createDirectory(temporaryDirectory.resolve("drift-256"));
        LocalEvaluationEvidence armEvidence = new LocalEvaluationEvidence(
                LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
                LocalEvaluationBudgetTestFixtures.PROMPT,
                LocalEvaluationBudgetTestFixtures.CATALOG,
                LocalEvaluationBudgetTestFixtures.REVIEW);
        armEvidence.write(
                drift64,
                LocalEvaluationBudgetTestFixtures.result(64),
                new EvidenceCodeBaseline("c".repeat(40), false));
        armEvidence.write(
                drift256,
                LocalEvaluationBudgetTestFixtures.result(256),
                new EvidenceCodeBaseline("d".repeat(40), false));

        LocalEvaluationBudgetEvidence.OfflinePairResult driftResult = evidence.verifyPair(drift64, drift256);
        assertThat(driftResult.valid()).isFalse();
        assertThat(driftResult.failures())
                .anyMatch(failure -> failure.contains("Git code baseline"));
    }

    @Test
    void rejectsNonDistinctDirectoriesAndNonPairedTokenSettings() throws Exception {
        Pair pair = writePair("distinct");
        assertThatThrownBy(() -> evidence.writePair(
                pair.budget64(),
                LocalEvaluationBudgetTestFixtures.result(64),
                pair.budget64(),
                LocalEvaluationBudgetTestFixtures.result(256),
                LocalEvaluationBudgetTestFixtures.CLEAN_BASELINE))
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("distinct arm directories");

        Path other64 = Files.createDirectory(temporaryDirectory.resolve("other-64"));
        Path other256 = Files.createDirectory(temporaryDirectory.resolve("other-256"));
        assertThatThrownBy(() -> evidence.writePair(
                other64,
                LocalEvaluationBudgetTestFixtures.result(64),
                other256,
                LocalEvaluationBudgetTestFixtures.result(64),
                LocalEvaluationBudgetTestFixtures.CLEAN_BASELINE))
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("run settings drifted from the locked two-arm protocol");
    }

    private Pair writePair(String name) throws Exception {
        Path budget64 = temporaryDirectory.resolve(name + "-64");
        Path budget256 = temporaryDirectory.resolve(name + "-256");
        Files.createDirectory(budget64);
        Files.createDirectory(budget256);
        evidence.writePair(
                budget64,
                LocalEvaluationBudgetTestFixtures.result(64),
                budget256,
                LocalEvaluationBudgetTestFixtures.result(256),
                LocalEvaluationBudgetTestFixtures.CLEAN_BASELINE);
        return new Pair(budget64, budget256);
    }

    private record Pair(Path budget64, Path budget256) {}
}
