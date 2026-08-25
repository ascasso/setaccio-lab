package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortComparisonTest {

    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityCohortComparison comparison =
            new ToolCompatibilityCohortComparison(
                    ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);

    @Test
    void comparesEveryPeerInOrderAndClassifiesAllFourPairedOutcomes() {
        List<ToolCompatibilityRow> successful =
                ToolCompatibilityAnalysisTestFixtures.successfulResult().rows();
        List<ToolCompatibilityRow> firstPeer = new ArrayList<>(successful);
        List<ToolCompatibilityRow> secondPeer = new ArrayList<>(successful);
        List<ToolCompatibilityRow> reference = new ArrayList<>(successful);
        replaceWithProviderFailure(firstPeer, 1);
        replaceWithProviderFailure(firstPeer, 3);
        replaceWithProviderFailure(reference, 2);
        replaceWithProviderFailure(reference, 3);

        ToolCompatibilityCohortComparison.ComparisonResult result = comparison.compare(
                "2026-08-25-comparison-fixture",
                CLEAN_BASELINE,
                ToolCompatibilityCohortTestFixtures.comparisonResult(
                        firstPeer, secondPeer, reference));

        assertThat(result.data().peers())
                .extracting(peer -> peer.peer().effectiveInstalledTag())
                .containsExactly("fixture-peer-one:1b", "fixture-peer-two:3b");
        ToolCompatibilityCohortComparison.PeerComparison first =
                result.data().peers().getFirst();
        assertThat(counts(first)).containsExactlyEntriesOf(Map.of(
                ToolCompatibilityCohortComparison.Outcome.BOTH_PASS, 13L,
                ToolCompatibilityCohortComparison.Outcome.REFERENCE_ONLY, 1L,
                ToolCompatibilityCohortComparison.Outcome.PEER_ONLY, 1L,
                ToolCompatibilityCohortComparison.Outcome.NEITHER, 1L));
        assertThat(first.rows().getFirst().peerDiagnostic())
                .isEqualTo(ToolCompatibilityDiagnostic.PROVIDER_FAILURE);
        assertThat(first.rows().get(1).referenceDiagnostic())
                .isEqualTo(ToolCompatibilityDiagnostic.PROVIDER_FAILURE);
        assertThat(first.rows().getFirst().latencyDeltaMillis()).isEqualTo(0L);
        assertThat(first.rows().getFirst().totalTokenDelta()).isNull();
        assertThat(first.rows().get(3).totalTokenDelta()).isZero();

        ToolCompatibilityCohortComparison.PeerComparison second =
                result.data().peers().getLast();
        assertThat(second.count(ToolCompatibilityCohortComparison.Outcome.BOTH_PASS))
                .isEqualTo(14);
        assertThat(second.count(ToolCompatibilityCohortComparison.Outcome.PEER_ONLY))
                .isEqualTo(2);
    }

    @Test
    void rendersOneDeterministicBoundedReportWithVisibleDeploymentIdentity() {
        ToolCompatibilityCohortResult fixture = ToolCompatibilityCohortTestFixtures.result();

        String first = comparison.compare(
                        "2026-08-25-report-fixture", CLEAN_BASELINE, fixture)
                .report();
        String second = comparison.compare(
                        "2026-08-25-report-fixture", CLEAN_BASELINE, fixture)
                .report();

        assertThat(first).isEqualTo(second)
                .startsWith("# Offline Tool Compatibility Reference Comparison\n")
                .contains("- Reference artifact/runtime format: `MLX`")
                .contains("- Peer artifact/runtime format: `GGUF`")
                .contains("### Locked case/repetition overlap")
                .contains("Passed by both: `16`")
                .contains("### Compatibility failures unique to one side")
                .contains("Reference-minus-peer deltas")
                .contains("Output limit (peer / reference)")
                .contains("reached / reached")
                .contains("A reference pass is not semantic ground truth")
                .contains("not backend-normalized")
                .contains("This deterministic report produces no aggregate score")
                .doesNotContain("## Winner", "## Ranking", "## Leaderboard");
    }

    @Test
    void computesSignedReferenceMinusPeerResourceDeltasWithoutImputation() {
        ToolCompatibilityCohortComparison.RowComparison row =
                new ToolCompatibilityCohortComparison.RowComparison(
                        "arithmetic-add",
                        1,
                        ToolCompatibilityCohortComparison.Outcome.BOTH_PASS,
                        "pass",
                        "pass",
                        ToolCompatibilityOutputLimitState.NOT_REACHED,
                        ToolCompatibilityOutputLimitState.NOT_REACHED,
                        30,
                        10,
                        new ToolCompatibilityCohortComparison.TokenObservation(
                                ToolCompatibilityUsageAvailability.COMPLETE, 6),
                        new ToolCompatibilityCohortComparison.TokenObservation(
                                ToolCompatibilityUsageAvailability.COMPLETE, 10));

        assertThat(row.latencyDeltaMillis()).isEqualTo(-20);
        assertThat(row.totalTokenDelta()).isEqualTo(4);
    }

    @Test
    void verifiesEvidenceBeforeComparisonAndLeavesEveryArtifactUnchanged() throws Exception {
        Path run = Files.createDirectory(
                temporaryDirectory.resolve("2026-08-25-verified-comparison"));
        ToolCompatibilityCohortEvidence evidence = new ToolCompatibilityCohortEvidence(
                ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);
        evidence.write(run, ToolCompatibilityCohortTestFixtures.result(), CLEAN_BASELINE);
        Path raw = run.resolve(ToolCompatibilityCohortResult.RAW_FILENAME);
        Path summary = run.resolve(ToolCompatibilityCohortEvidence.SUMMARY_FILENAME);
        Path manifest = run.resolve("manifest.json");
        byte[] rawBefore = Files.readAllBytes(raw);
        byte[] summaryBefore = Files.readAllBytes(summary);
        byte[] manifestBefore = Files.readAllBytes(manifest);

        ToolCompatibilityCohortComparison.ComparisonResult result = comparison.compare(run);

        assertThat(result.data().runId()).isEqualTo(run.getFileName().toString());
        assertThat(Files.readAllBytes(raw)).isEqualTo(rawBefore);
        assertThat(Files.readAllBytes(summary)).isEqualTo(summaryBefore);
        assertThat(Files.readAllBytes(manifest)).isEqualTo(manifestBefore);
    }

    @Test
    void rejectsMissingEvidenceBeforeRenderingAnyComparison() throws Exception {
        Path run = Files.createDirectory(
                temporaryDirectory.resolve("2026-08-25-missing-comparison-evidence"));
        ToolCompatibilityCohortEvidence evidence = new ToolCompatibilityCohortEvidence(
                ToolCompatibilityCohortTestFixtures.OBJECT_MAPPER);
        evidence.write(run, ToolCompatibilityCohortTestFixtures.result(), CLEAN_BASELINE);
        Files.delete(run.resolve(ToolCompatibilityCohortResult.RAW_FILENAME));

        assertThatThrownBy(() -> comparison.compare(run))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("did not verify")
                .hasMessageContaining("missing");
    }

    private static void replaceWithProviderFailure(
            List<ToolCompatibilityRow> rows,
            int sequence
    ) {
        ToolCompatibilityCaseSelection.ScheduledCase scheduled =
                ToolCompatibilityAnalysisTestFixtures.schedule().get(sequence - 1);
        rows.set(
                sequence - 1,
                ToolCompatibilityAnalysisTestFixtures.providerFailureRow(
                        scheduled, sequence * 10L));
    }

    private static Map<ToolCompatibilityCohortComparison.Outcome, Long> counts(
            ToolCompatibilityCohortComparison.PeerComparison comparison
    ) {
        return Map.of(
                ToolCompatibilityCohortComparison.Outcome.BOTH_PASS,
                comparison.count(ToolCompatibilityCohortComparison.Outcome.BOTH_PASS),
                ToolCompatibilityCohortComparison.Outcome.REFERENCE_ONLY,
                comparison.count(ToolCompatibilityCohortComparison.Outcome.REFERENCE_ONLY),
                ToolCompatibilityCohortComparison.Outcome.PEER_ONLY,
                comparison.count(ToolCompatibilityCohortComparison.Outcome.PEER_ONLY),
                ToolCompatibilityCohortComparison.Outcome.NEITHER,
                comparison.count(ToolCompatibilityCohortComparison.Outcome.NEITHER));
    }
}
