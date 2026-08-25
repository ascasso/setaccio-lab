package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortFrontierTest {

    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);
    private static final List<ToolCompatibilityRow> SUCCESSFUL =
            ToolCompatibilityAnalysisTestFixtures.successfulResult().rows();

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityCohortFrontier frontier =
            new ToolCompatibilityCohortFrontier(
                    ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);

    @Test
    void selectsTheUniqueSmallestRecordedArtifactAmongAllPassModels() {
        ToolCompatibilityCohortResult result = fixture(
                List.of(peer("peer-large:3b", "a", "3000"),
                        peer("peer-small:1b", "b", "1000"),
                        reference("reference:27b-mlx", "c", "5000")),
                List.of(SUCCESSFUL, SUCCESSFUL, SUCCESSFUL));

        ToolCompatibilityCohortFrontier.FrontierResult analysis = frontier.analyze(
                "2026-08-25-measurable-frontier", CLEAN_BASELINE, result);

        assertThat(analysis.data().measurement().status())
                .isEqualTo(ToolCompatibilityCohortFrontier.Status.MEASURABLE);
        assertThat(analysis.data().measurement().qualifyingModels()).isEqualTo(3);
        assertThat(analysis.data().measurement().frontier().identity()
                .effectiveInstalledTag()).isEqualTo("peer-small:1b");
        assertThat(analysis.data().measurement().frontier().sizeBytes()).isEqualTo(1000L);
    }

    @Test
    void keepsReferenceRoleVisibleWhenItIsTheOnlyAllPassArtifact() {
        List<ToolCompatibilityRow> failedPeer = withProviderFailure(SUCCESSFUL, 1);
        ToolCompatibilityCohortResult result = fixture(
                List.of(peer("peer:1b", "a", "1000"),
                        reference("reference:27b-mlx", "b", "5000")),
                List.of(failedPeer, SUCCESSFUL));

        String report = frontier.analyze(
                        "2026-08-25-reference-frontier", CLEAN_BASELINE, result)
                .report();

        assertThat(report)
                .contains("- Frontier tag: `reference:27b-mlx`")
                .contains("- Frontier role: `reference`")
                .contains("- Qualifying models: `1`")
                .contains("smallest model by recorded installed-artifact size")
                .contains("under this exact protocol")
                .contains("not a claim about the smallest model capable of tool calling")
                .doesNotContain("## Winner", "## Ranking", "## Leaderboard");
    }

    @Test
    void reportsNotMeasurableWhenNoModelPassesEveryLockedRow() {
        List<ToolCompatibilityRow> failed = withProviderFailure(SUCCESSFUL, 1);
        ToolCompatibilityCohortResult result = fixture(
                List.of(peer("peer:1b", "a", "1000"),
                        reference("reference:27b-mlx", "b", "5000")),
                List.of(failed, failed));

        ToolCompatibilityCohortFrontier.FrontierResult analysis = frontier.analyze(
                "2026-08-25-no-frontier", CLEAN_BASELINE, result);

        assertThat(analysis.data().measurement().status())
                .isEqualTo(ToolCompatibilityCohortFrontier.Status.NOT_MEASURABLE);
        assertThat(analysis.data().measurement().frontier()).isNull();
        assertThat(analysis.report())
                .contains("- Status: `not measurable`")
                .contains("no tested installed model passed every locked row")
                .doesNotContain("- Frontier tag:");
    }

    @Test
    void reportsNotMeasurableForMissingQualifyingSizeOrAmbiguousMinimum() {
        ToolCompatibilityCohortResult missingSize = fixture(
                List.of(peer("peer:1b", "a", "1000"),
                        reference("reference:27b-mlx", "b", null)),
                List.of(withProviderFailure(SUCCESSFUL, 1), SUCCESSFUL));
        ToolCompatibilityCohortResult tiedMinimum = fixture(
                List.of(peer("peer:1b", "a", "1000"),
                        reference("reference:27b-mlx", "b", "1000")),
                List.of(SUCCESSFUL, SUCCESSFUL));

        assertThat(frontier.analyze(
                        "2026-08-25-missing-size", CLEAN_BASELINE, missingSize)
                .data().measurement().reason())
                .contains("size is unavailable or invalid");
        assertThat(frontier.analyze(
                        "2026-08-25-tied-size", CLEAN_BASELINE, tiedMinimum)
                .data().measurement().reason())
                .contains("share the smallest recorded size");
    }

    @Test
    void verifiesEvidenceBeforeAnalysisAndLeavesEveryArtifactUnchanged() throws Exception {
        Path run = Files.createDirectory(
                temporaryDirectory.resolve("2026-08-25-verified-frontier"));
        ToolCompatibilityCohortResult result = fixture(
                List.of(peer("peer:1b", "a", "1000"),
                        reference("reference:27b-mlx", "b", "5000")),
                List.of(withProviderFailure(SUCCESSFUL, 1), SUCCESSFUL));
        ToolCompatibilityCohortEvidence evidence = new ToolCompatibilityCohortEvidence(
                ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);
        evidence.write(run, result, CLEAN_BASELINE);
        Path raw = run.resolve(ToolCompatibilityCohortResult.RAW_FILENAME);
        Path summary = run.resolve(ToolCompatibilityCohortEvidence.SUMMARY_FILENAME);
        Path manifest = run.resolve("manifest.json");
        byte[] rawBefore = Files.readAllBytes(raw);
        byte[] summaryBefore = Files.readAllBytes(summary);
        byte[] manifestBefore = Files.readAllBytes(manifest);

        ToolCompatibilityCohortFrontier.FrontierResult analysis = frontier.analyze(run);

        assertThat(analysis.data().measurement().status())
                .isEqualTo(ToolCompatibilityCohortFrontier.Status.MEASURABLE);
        assertThat(Files.readAllBytes(raw)).isEqualTo(rawBefore);
        assertThat(Files.readAllBytes(summary)).isEqualTo(summaryBefore);
        assertThat(Files.readAllBytes(manifest)).isEqualTo(manifestBefore);
    }

    @Test
    void rejectsMissingEvidenceBeforeRenderingAFrontier() throws Exception {
        Path run = Files.createDirectory(
                temporaryDirectory.resolve("2026-08-25-missing-frontier-evidence"));
        ToolCompatibilityCohortEvidence evidence = new ToolCompatibilityCohortEvidence(
                ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);
        evidence.write(run, ToolCompatibilityCohortTestFixtures.result(), CLEAN_BASELINE);
        Files.delete(run.resolve(ToolCompatibilityCohortResult.RAW_FILENAME));

        assertThatThrownBy(() -> frontier.analyze(run))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("did not verify")
                .hasMessageContaining("missing");
    }

    private static ToolCompatibilityCohortResult fixture(
            List<ToolCompatibilityCohortTestFixtures.FrontierModelFixture> models,
            List<List<ToolCompatibilityRow>> rows
    ) {
        return ToolCompatibilityCohortTestFixtures.frontierResult(models, rows);
    }

    private static ToolCompatibilityCohortTestFixtures.FrontierModelFixture peer(
            String tag,
            String digestCharacter,
            String sizeBytes
    ) {
        return model(
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                tag,
                digestCharacter,
                sizeBytes);
    }

    private static ToolCompatibilityCohortTestFixtures.FrontierModelFixture reference(
            String tag,
            String digestCharacter,
            String sizeBytes
    ) {
        return model(
                ToolCompatibilityCohortModelIdentity.Role.REFERENCE,
                tag,
                digestCharacter,
                sizeBytes);
    }

    private static ToolCompatibilityCohortTestFixtures.FrontierModelFixture model(
            ToolCompatibilityCohortModelIdentity.Role role,
            String tag,
            String digestCharacter,
            String sizeBytes
    ) {
        return new ToolCompatibilityCohortTestFixtures.FrontierModelFixture(
                role, tag, digestCharacter.repeat(64), sizeBytes);
    }

    private static List<ToolCompatibilityRow> withProviderFailure(
            List<ToolCompatibilityRow> rows,
            int sequence
    ) {
        List<ToolCompatibilityRow> changed = new ArrayList<>(rows);
        ToolCompatibilityCaseSelection.ScheduledCase scheduled =
                ToolCompatibilityAnalysisTestFixtures.schedule().get(sequence - 1);
        changed.set(
                sequence - 1,
                ToolCompatibilityAnalysisTestFixtures.providerFailureRow(
                        scheduled, sequence * 10L));
        return List.copyOf(changed);
    }
}
