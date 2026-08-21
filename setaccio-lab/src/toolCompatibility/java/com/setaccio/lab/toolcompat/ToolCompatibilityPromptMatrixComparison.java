package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strictly gates an offline Phase 2 prompt comparison before a later slice renders any deltas.
 *
 * <p>The gate intentionally compares protocol identity only. Provider, tool, callback, output,
 * usage, latency, and diagnostic observations remain available to the later deterministic report
 * and are never interpreted here.</p>
 */
final class ToolCompatibilityPromptMatrixComparison {

    private final ToolCompatibilityPromptMatrixEvidence evidence;

    ToolCompatibilityPromptMatrixComparison(ObjectMapper objectMapper) {
        evidence = new ToolCompatibilityPromptMatrixEvidence(objectMapper);
    }

    ComparisonResult compare(Path baselineDirectory, Path candidateDirectory) {
        ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline =
                evidence.requireVerified(baselineDirectory, "baseline");
        ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate =
                evidence.requireVerified(candidateDirectory, "candidate");
        validateComparable(baseline, candidate);
        return new ComparisonResult(
                baseline.manifest().runId(),
                candidate.manifest().runId(),
                baseline.result().pairedExecutionSchedule().sha256(),
                baseline.result().rows().size());
    }

    private static void validateComparable(
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline,
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate
    ) {
        List<String> failures = new ArrayList<>();
        if (baseline.root().equals(candidate.root())) {
            failures.add("baseline and candidate output directories must differ");
        }

        requirePromptRoles(baseline, candidate, failures);
        requireCleanSharedGitCommit(baseline.manifest(), candidate.manifest(), failures);
        requireSameFrameworkVersions(baseline.manifest(), candidate.manifest(), failures);
        requireSameManifestProtocol(baseline.manifest(), candidate.manifest(), failures);
        requireSameResultProtocol(baseline.result(), candidate.result(), failures);
        requireScheduleDerivedPairPositions(baseline.result(), candidate.result(), failures);

        if (!failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tool compatibility prompt-matrix runs are not comparable: "
                            + String.join("; ", failures));
        }
    }

    private static void requirePromptRoles(
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline,
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate,
            List<String> failures
    ) {
        requirePromptRole("baseline", baseline.result(), ToolCompatibilityPromptCondition.UNTREATED, failures);
        requirePromptRole("candidate", candidate.result(), ToolCompatibilityPromptCondition.PROMPTED, failures);
    }

    private static void requirePromptRole(
            String label,
            ToolCompatibilityPromptMatrixResult result,
            ToolCompatibilityPromptCondition expectedCondition,
            List<String> failures
    ) {
        if (result.promptCondition() != expectedCondition) {
            failures.add(label + " run must carry the " + expectedCondition.wireValue() + " prompt condition");
        }
        ToolCompatibilitySystemPromptIdentity expectedPrompt = expectedCondition.prompt(
                ToolCompatibilityProtocol.systemPromptCatalog());
        if (!expectedPrompt.equals(result.systemPromptIdentity())) {
            failures.add(label + " run system-prompt identity does not match the "
                    + expectedCondition.wireValue() + " condition");
        }
        for (ToolCompatibilityRow row : result.rows()) {
            if (!expectedPrompt.id().equals(row.systemPromptId())
                    || expectedPrompt.version() != row.systemPromptVersion()
                    || !expectedPrompt.sha256().equals(row.systemPromptSha256())) {
                failures.add(label + " run row " + row.sequence()
                        + " has a system-prompt identity outside its condition");
            }
        }
    }

    private static void requireCleanSharedGitCommit(
            EvidenceManifest baseline,
            EvidenceManifest candidate,
            List<String> failures
    ) {
        EvidenceCodeBaseline baselineCode = baseline.codeBaseline();
        EvidenceCodeBaseline candidateCode = candidate.codeBaseline();
        if (!baselineCode.gitCommit().matches("[0-9a-f]{40}")) {
            failures.add("baseline run does not record a full Git commit");
        }
        if (!candidateCode.gitCommit().matches("[0-9a-f]{40}")) {
            failures.add("candidate run does not record a full Git commit");
        }
        if (baselineCode.workingTreeDirty()) {
            failures.add("baseline run records a dirty Git worktree");
        }
        if (candidateCode.workingTreeDirty()) {
            failures.add("candidate run records a dirty Git worktree");
        }
        if (!baselineCode.gitCommit().equals(candidateCode.gitCommit())) {
            failures.add("Git commits differ");
        }
    }

    private static void requireSameFrameworkVersions(
            EvidenceManifest baseline,
            EvidenceManifest candidate,
            List<String> failures
    ) {
        if (!baseline.frameworkVersions().equals(candidate.frameworkVersions())) {
            failures.add("Spring Boot or Spring AI framework versions differ");
        }
    }

    private static void requireSameManifestProtocol(
            EvidenceManifest baseline,
            EvidenceManifest candidate,
            List<String> failures
    ) {
        if (!baseline.executionEngine().equals(candidate.executionEngine())) {
            failures.add("manifest execution engine or advisor mode differs");
        }
        if (!nonPromptSettings(baseline).equals(nonPromptSettings(candidate))) {
            failures.add("manifest non-prompt protocol settings differ");
        }
    }

    private static Map<String, Object> nonPromptSettings(EvidenceManifest manifest) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>(manifest.settings());
        settings.remove("promptCondition");
        settings.remove("systemPromptIdentity");
        return Map.copyOf(settings);
    }

    private static void requireSameResultProtocol(
            ToolCompatibilityPromptMatrixResult baseline,
            ToolCompatibilityPromptMatrixResult candidate,
            List<String> failures
    ) {
        if (baseline.protocolVersion() != candidate.protocolVersion()
                || !baseline.suite().equals(candidate.suite())
                || !baseline.provider().equals(candidate.provider())
                || !baseline.executionStrategy().equals(candidate.executionStrategy())
                || !baseline.pullModelStrategy().equals(candidate.pullModelStrategy())) {
            failures.add("provider, execution strategy, or pull policy differs");
        }
        if (!baseline.executionEngine().equals(candidate.executionEngine())) {
            failures.add("result execution engine or advisor mode differs");
        }
        requireSameRunSettings(baseline.runSettings(), candidate.runSettings(), failures);
        if (!baseline.modelIdentity().equals(candidate.modelIdentity())) {
            failures.add("model digest or ordered model identity differs");
        }
        if (!baseline.orderedCaseIds().equals(candidate.orderedCaseIds())
                || !baseline.canonicalCasesSha256().equals(candidate.canonicalCasesSha256())) {
            failures.add("case IDs or case order differs");
        }
        if (!baseline.orderedToolNames().equals(candidate.orderedToolNames())
                || !baseline.toolNamesSha256().equals(candidate.toolNamesSha256())
                || !baseline.toolDefinitionsSha256().equals(candidate.toolDefinitionsSha256())) {
            failures.add("tool catalog identity differs");
        }
        if (!baseline.caseOracleId().equals(candidate.caseOracleId())
                || baseline.caseOracleVersion() != candidate.caseOracleVersion()
                || !baseline.caseOracleSha256().equals(candidate.caseOracleSha256())) {
            failures.add("semantic call-oracle identity differs");
        }
        if (!baseline.orderedSchedule().equals(candidate.orderedSchedule())) {
            failures.add("case order, repetition count, or seeds differ");
        }
        if (!baseline.pairedExecutionSchedule().equals(candidate.pairedExecutionSchedule())) {
            failures.add("paired-execution schedule identity differs");
        }
    }

    private static void requireSameRunSettings(
            ToolCompatibilityRunSettings baseline,
            ToolCompatibilityRunSettings candidate,
            List<String> failures
    ) {
        if (baseline.repetitions() != candidate.repetitions()) {
            failures.add("repetition count differs");
        }
        if (!baseline.seeds().equals(candidate.seeds())) {
            failures.add("seeds differ");
        }
        if (Double.compare(baseline.temperature(), candidate.temperature()) != 0) {
            failures.add("temperature differs");
        }
        if (baseline.maxOutputTokensPerProviderTurn()
                != candidate.maxOutputTokensPerProviderTurn()) {
            failures.add("per-provider-turn output tokens differ");
        }
        if (baseline.rowTimeoutMillis() != candidate.rowTimeoutMillis()) {
            failures.add("whole-row deadline differs");
        }
        if (baseline.logicalRowAttempts() != candidate.logicalRowAttempts()) {
            failures.add("logical attempts differ");
        }
    }

    private static void requireScheduleDerivedPairPositions(
            ToolCompatibilityPromptMatrixResult baseline,
            ToolCompatibilityPromptMatrixResult candidate,
            List<String> failures
    ) {
        verifyConditionSchedulePositions("baseline", baseline, failures);
        verifyConditionSchedulePositions("candidate", candidate, failures);

        Map<RowKey, ToolCompatibilityRow> baselineRows = rowsByCaseAndRepetition(
                "baseline", baseline.rows(), failures);
        Map<RowKey, ToolCompatibilityRow> candidateRows = rowsByCaseAndRepetition(
                "candidate", candidate.rows(), failures);
        if (!baselineRows.keySet().equals(candidateRows.keySet())) {
            failures.add("baseline and candidate row case/repetition pairs differ");
            return;
        }

        for (Map.Entry<RowKey, ToolCompatibilityRow> baselineEntry : baselineRows.entrySet()) {
            RowKey key = baselineEntry.getKey();
            ToolCompatibilityRow baselineRow = baselineEntry.getValue();
            ToolCompatibilityRow candidateRow = candidateRows.get(key);
            requireSameRowProtocol(key, baselineRow, candidateRow, failures);
            requireDistinctPairedPositions(key, baselineRow, candidateRow, failures);
        }
    }

    private static void verifyConditionSchedulePositions(
            String label,
            ToolCompatibilityPromptMatrixResult result,
            List<String> failures
    ) {
        ToolCompatibilityPairedSchedule schedule = result.pairedExecutionSchedule();
        for (ToolCompatibilityRow row : result.rows()) {
            ToolCompatibilityPairedSchedule.Entry expected = schedule.requireEntry(
                    result.promptCondition(), row.sequence());
            if (row.globalPairSequence() == null || row.conditionExecutionPosition() == null) {
                failures.add(label + " row " + row.sequence() + " is missing paired execution position");
            } else if (!Integer.valueOf(expected.globalPairSequence()).equals(row.globalPairSequence())
                    || expected.conditionExecutionPosition() != row.conditionExecutionPosition()) {
                failures.add(label + " row " + row.sequence()
                        + " has a schedule-inconsistent paired execution position");
            }
        }
    }

    private static Map<RowKey, ToolCompatibilityRow> rowsByCaseAndRepetition(
            String label,
            List<ToolCompatibilityRow> rows,
            List<String> failures
    ) {
        LinkedHashMap<RowKey, ToolCompatibilityRow> byKey = new LinkedHashMap<>();
        for (ToolCompatibilityRow row : rows) {
            RowKey key = new RowKey(row.caseId(), row.repetition());
            if (byKey.putIfAbsent(key, row) != null) {
                failures.add(label + " run contains duplicate case/repetition pair " + key);
            }
        }
        return Map.copyOf(byKey);
    }

    private static void requireSameRowProtocol(
            RowKey key,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate,
            List<String> failures
    ) {
        if (baseline.sequence() != candidate.sequence()
                || !Objects.equals(baseline.seed(), candidate.seed())) {
            failures.add("row protocol differs for " + key);
        }
        if (!baseline.provider().equals(candidate.provider())
                || !baseline.requestedModel().equals(candidate.requestedModel())
                || !baseline.effectiveModel().equals(candidate.effectiveModel())
                || !baseline.modelDigest().equals(candidate.modelDigest())) {
            failures.add("model or provider identity differs for " + key);
        }
        if (Double.compare(baseline.temperature(), candidate.temperature()) != 0
                || baseline.maxOutputTokensPerProviderTurn()
                        != candidate.maxOutputTokensPerProviderTurn()
                || !baseline.rowAttemptDeadline().equals(candidate.rowAttemptDeadline())
                || baseline.attemptCount() != candidate.attemptCount()) {
            failures.add("provider-turn, output-limit, row-deadline, or attempt policy differs for " + key);
        }
    }

    private static void requireDistinctPairedPositions(
            RowKey key,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate,
            List<String> failures
    ) {
        if (Objects.equals(baseline.globalPairSequence(), candidate.globalPairSequence())) {
            failures.add("paired global sequence is unexpectedly equal for " + key);
        }
        if (baseline.conditionExecutionPosition() == candidate.conditionExecutionPosition()) {
            failures.add("paired execution position is unexpectedly equal for " + key);
        }
    }

    record ComparisonResult(
            String baselineRunId,
            String candidateRunId,
            String pairedScheduleSha256,
            int pairedRowCount
    ) {

        ComparisonResult {
            if (baselineRunId == null || candidateRunId == null || pairedScheduleSha256 == null
                    || pairedRowCount != ToolCompatibilityProtocol.ROW_COUNT) {
                throw new IllegalArgumentException("complete verified paired comparison identity is required");
            }
        }
    }

    private record RowKey(String caseId, int repetition) {}
}
