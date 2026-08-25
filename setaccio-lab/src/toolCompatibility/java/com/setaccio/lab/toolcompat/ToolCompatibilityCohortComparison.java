package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds the deterministic, provider-free T3.5 peer/reference comparison. */
final class ToolCompatibilityCohortComparison {

    private final ToolCompatibilityCohortEvidence evidence;
    private final ToolCompatibilityCohortComparisonReport report;

    ToolCompatibilityCohortComparison(ObjectMapper objectMapper) {
        evidence = new ToolCompatibilityCohortEvidence(objectMapper);
        report = new ToolCompatibilityCohortComparisonReport();
    }

    ComparisonResult compare(Path runDirectory) {
        ToolCompatibilityCohortEvidence.VerifiedCohort verified =
                evidence.requireVerified(runDirectory, "cohort run");
        return compare(
                verified.manifest().runId(),
                verified.manifest().codeBaseline(),
                verified.result());
    }

    ComparisonResult compare(
            String runId,
            EvidenceCodeBaseline codeBaseline,
            ToolCompatibilityCohortResult result
    ) {
        if (runId == null || runId.isBlank() || !runId.equals(runId.strip())) {
            throw new IllegalArgumentException("cohort comparison run ID must be nonblank and trimmed");
        }
        if (codeBaseline == null || result == null) {
            throw new IllegalArgumentException(
                    "cohort comparison code baseline and result are required");
        }
        List<ToolCompatibilityCohortModelRun> runs = result.modelRuns();
        if (runs.size() < 2) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "T3.5 comparison requires at least one peer and one reference");
        }
        ToolCompatibilityCohortModelRun reference = runs.getLast();
        if (reference.modelIdentity().role()
                != ToolCompatibilityCohortModelIdentity.Role.REFERENCE) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "T3.5 comparison requires the ordered final model to be the reference");
        }

        List<PeerComparison> peers = new ArrayList<>();
        for (int index = 0; index < runs.size() - 1; index++) {
            ToolCompatibilityCohortModelRun peer = runs.get(index);
            if (peer.modelIdentity().role()
                    != ToolCompatibilityCohortModelIdentity.Role.PEER) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "T3.5 comparison encountered a non-peer before the reference");
            }
            peers.add(compare(peer, reference));
        }

        ComparisonData data = new ComparisonData(
                runId,
                codeBaseline,
                result.ollamaRuntimeVersion(),
                result.runSettings(),
                result.orderedCaseIds(),
                reference.modelIdentity(),
                peers);
        return new ComparisonResult(data, report.render(data));
    }

    private static PeerComparison compare(
            ToolCompatibilityCohortModelRun peer,
            ToolCompatibilityCohortModelRun reference
    ) {
        List<ToolCompatibilityRow> peerRows = peer.rows();
        List<ToolCompatibilityRow> referenceRows = reference.rows();
        if (peerRows.size() != referenceRows.size()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Peer and reference evidence must contain the same number of rows");
        }
        List<RowComparison> rows = new ArrayList<>(peerRows.size());
        for (int index = 0; index < peerRows.size(); index++) {
            ToolCompatibilityRow peerRow = peerRows.get(index);
            ToolCompatibilityRow referenceRow = referenceRows.get(index);
            if (peerRow.sequence() != referenceRow.sequence()
                    || !peerRow.caseId().equals(referenceRow.caseId())
                    || peerRow.repetition() != referenceRow.repetition()) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Peer and reference rows must preserve the same locked schedule");
            }
            rows.add(new RowComparison(
                    peerRow.caseId(),
                    peerRow.repetition(),
                    Outcome.from(
                            peerRow.caseContractPassed(),
                            referenceRow.caseContractPassed()),
                    diagnostic(peerRow),
                    diagnostic(referenceRow),
                    outputLimit(peerRow),
                    outputLimit(referenceRow),
                    peerRow.rowLatency().toMillis(),
                    referenceRow.rowLatency().toMillis(),
                    TokenObservation.from(peerRow.aggregateUsage()),
                    TokenObservation.from(referenceRow.aggregateUsage())));
        }
        return new PeerComparison(peer.modelIdentity(), List.copyOf(rows));
    }

    private static String diagnostic(ToolCompatibilityRow row) {
        if (row.diagnosticCategory() != null && !row.diagnosticCategory().isBlank()) {
            return row.diagnosticCategory();
        }
        if (row.caseContractPassed()) {
            return "pass";
        }
        throw new ToolCompatibilityProtocolIntegrityException(
                "A failed comparison row is missing its deterministic diagnostic");
    }

    private static ToolCompatibilityOutputLimitState outputLimit(
            ToolCompatibilityRow row
    ) {
        if (row.providerTurns().stream().anyMatch(turn ->
                turn.outputLimitState() == ToolCompatibilityOutputLimitState.REACHED)) {
            return ToolCompatibilityOutputLimitState.REACHED;
        }
        if (row.providerTurns().isEmpty() || row.providerTurns().stream().anyMatch(turn ->
                turn.outputLimitState() == ToolCompatibilityOutputLimitState.UNOBSERVABLE)) {
            return ToolCompatibilityOutputLimitState.UNOBSERVABLE;
        }
        return ToolCompatibilityOutputLimitState.NOT_REACHED;
    }

    record ComparisonResult(ComparisonData data, String report) {

        ComparisonResult {
            if (data == null || report == null || report.isBlank()) {
                throw new IllegalArgumentException(
                        "cohort comparison data and nonblank report are required");
            }
        }
    }

    record ComparisonData(
            String runId,
            EvidenceCodeBaseline codeBaseline,
            String ollamaRuntimeVersion,
            ToolCompatibilityCohortRunSettings runSettings,
            List<String> orderedCaseIds,
            ToolCompatibilityCohortModelIdentity reference,
            List<PeerComparison> peers
    ) {

        ComparisonData {
            if (runId == null
                    || codeBaseline == null
                    || ollamaRuntimeVersion == null
                    || runSettings == null
                    || reference == null) {
                throw new IllegalArgumentException("cohort comparison identity is incomplete");
            }
            orderedCaseIds = List.copyOf(orderedCaseIds == null ? List.of() : orderedCaseIds);
            peers = List.copyOf(peers == null ? List.of() : peers);
            if (orderedCaseIds.isEmpty()
                    || peers.isEmpty()
                    || reference.role() != ToolCompatibilityCohortModelIdentity.Role.REFERENCE) {
                throw new IllegalArgumentException(
                        "cohort comparison requires cases, peers, and one reference");
            }
        }
    }

    record PeerComparison(
            ToolCompatibilityCohortModelIdentity peer,
            List<RowComparison> rows
    ) {

        PeerComparison {
            if (peer == null || peer.role() != ToolCompatibilityCohortModelIdentity.Role.PEER) {
                throw new IllegalArgumentException("peer comparison requires a peer identity");
            }
            rows = List.copyOf(rows == null ? List.of() : rows);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("peer comparison requires paired rows");
            }
        }

        long count(Outcome outcome) {
            return rows.stream().filter(row -> row.outcome() == outcome).count();
        }
    }

    record RowComparison(
            String caseId,
            int repetition,
            Outcome outcome,
            String peerDiagnostic,
            String referenceDiagnostic,
            ToolCompatibilityOutputLimitState peerOutputLimit,
            ToolCompatibilityOutputLimitState referenceOutputLimit,
            long peerLatencyMillis,
            long referenceLatencyMillis,
            TokenObservation peerTokens,
            TokenObservation referenceTokens
    ) {

        RowComparison {
            if (caseId == null
                    || caseId.isBlank()
                    || repetition < 1
                    || outcome == null
                    || peerDiagnostic == null
                    || referenceDiagnostic == null
                    || peerOutputLimit == null
                    || referenceOutputLimit == null
                    || peerLatencyMillis < 0
                    || referenceLatencyMillis < 0
                    || peerTokens == null
                    || referenceTokens == null) {
                throw new IllegalArgumentException("paired comparison row is incomplete");
            }
        }

        long latencyDeltaMillis() {
            return Math.subtractExact(referenceLatencyMillis, peerLatencyMillis);
        }

        Integer totalTokenDelta() {
            if (peerTokens.availability() != ToolCompatibilityUsageAvailability.COMPLETE
                    || referenceTokens.availability()
                            != ToolCompatibilityUsageAvailability.COMPLETE
                    || peerTokens.totalTokens() == null
                    || referenceTokens.totalTokens() == null) {
                return null;
            }
            return Math.subtractExact(
                    referenceTokens.totalTokens(), peerTokens.totalTokens());
        }
    }

    record TokenObservation(
            ToolCompatibilityUsageAvailability availability,
            Integer totalTokens
    ) {

        TokenObservation {
            if (availability == null || (totalTokens != null && totalTokens < 0)) {
                throw new IllegalArgumentException("token observation is invalid");
            }
        }

        static TokenObservation from(ToolCompatibilityTokenUsageEvidence usage) {
            if (usage == null) {
                throw new IllegalArgumentException("aggregate usage is required");
            }
            return new TokenObservation(usage.availability(), usage.totalTokens());
        }
    }

    enum Outcome {
        BOTH_PASS,
        REFERENCE_ONLY,
        PEER_ONLY,
        NEITHER;

        private static Outcome from(boolean peerPassed, boolean referencePassed) {
            if (peerPassed && referencePassed) {
                return BOTH_PASS;
            }
            if (referencePassed) {
                return REFERENCE_ONLY;
            }
            if (peerPassed) {
                return PEER_ONLY;
            }
            return NEITHER;
        }
    }
}
