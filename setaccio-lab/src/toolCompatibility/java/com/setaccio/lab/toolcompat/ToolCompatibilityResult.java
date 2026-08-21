package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

record ToolCompatibilityResult(
        int protocolVersion,
        String suite,
        String provider,
        String executionEngine,
        String executionStrategy,
        String pullModelStrategy,
        Instant startedAt,
        Instant finishedAt,
        ToolCompatibilityRunSettings runSettings,
        ToolCompatibilityModelIdentity modelIdentity,
        ToolCompatibilitySystemPromptIdentity systemPromptIdentity,
        String caseOracleId,
        int caseOracleVersion,
        String caseOracleSha256,
        List<String> orderedCaseIds,
        String canonicalCasesSha256,
        List<String> orderedToolNames,
        String toolNamesSha256,
        String toolDefinitionsSha256,
        List<ToolCompatibilityCaseSelection.ScheduledCase> orderedSchedule,
        List<ToolCompatibilityRow> rows
) {

    ToolCompatibilityResult {
        if (protocolVersion != ToolCompatibilityProtocol.VERSION
                || !ToolCompatibilityProtocol.SUITE.equals(suite)
                || !ToolCompatibilityProtocol.PROVIDER.equals(provider)
                || !ToolCompatibilityProtocol.EXECUTION_ENGINE.equals(executionEngine)
                || !ToolCompatibilityProtocol.EXECUTION_STRATEGY.equals(executionStrategy)
                || !ToolCompatibilityProtocol.PULL_MODEL_STRATEGY.equals(pullModelStrategy)) {
            throw new IllegalArgumentException("result protocol identity must equal the locked Phase 1 protocol");
        }
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("result timestamps must be ordered and complete");
        }
        ToolCompatibilityRunSettings expectedSettings = ToolCompatibilityProtocol.runSettings();
        if (!expectedSettings.equals(runSettings)) {
            throw new IllegalArgumentException("result settings must equal the locked Phase 1 settings");
        }
        if (modelIdentity == null
                || !runSettings.requestedModel().equals(modelIdentity.requestedModel())) {
            throw new IllegalArgumentException("result model identity must match its requested model");
        }
        if (systemPromptIdentity == null) {
            throw new IllegalArgumentException("result system-prompt identity must not be null");
        }
        systemPromptIdentity.requireUntreated();

        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        if (!oracle.id().equals(caseOracleId)
                || oracle.version() != caseOracleVersion
                || !oracle.sha256().equals(caseOracleSha256)) {
            throw new IllegalArgumentException("result case-oracle identity must equal the locked oracle");
        }
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        orderedCaseIds = List.copyOf(orderedCaseIds == null ? List.of() : orderedCaseIds);
        orderedToolNames = List.copyOf(orderedToolNames == null ? List.of() : orderedToolNames);
        ToolCompatibilityToolDefinitionIdentity toolDefinitions =
                ToolCompatibilityToolDefinitionIdentity.canonical();
        if (!selection.caseIds().equals(orderedCaseIds)
                || !selection.canonicalCasesSha256().equals(canonicalCasesSha256)
                || !selection.toolNames().equals(orderedToolNames)
                || !selection.toolNamesSha256().equals(toolNamesSha256)
                || !toolDefinitions.orderedToolNames().equals(orderedToolNames)
                || !toolDefinitions.sha256().equals(toolDefinitionsSha256)) {
            throw new IllegalArgumentException("result canonical case or tool selection drifted");
        }
        List<ToolCompatibilityCaseSelection.ScheduledCase> expectedSchedule =
                ToolCompatibilityProtocol.schedule(selection, runSettings);
        orderedSchedule = List.copyOf(orderedSchedule == null ? List.of() : orderedSchedule);
        rows = List.copyOf(rows == null ? List.of() : rows);
        if (!expectedSchedule.equals(orderedSchedule) || rows.size() != orderedSchedule.size()) {
            throw new IllegalArgumentException("result must contain the complete locked ordered schedule");
        }
        if (rows.stream().anyMatch(row -> row.globalPairSequence() != null
                || row.conditionExecutionPosition() != null)) {
            throw new IllegalArgumentException("Phase 1 result rows must not contain paired-execution metadata");
        }
        for (int index = 0; index < rows.size(); index++) {
            ToolCompatibilityCaseSelection.ScheduledCase scheduledCase = orderedSchedule.get(index);
            ToolCompatibilityRow row = rows.get(index);
            if (row.sequence() != scheduledCase.sequence()
                    || row.repetition() != scheduledCase.repetition()
                    || !row.seed().equals(scheduledCase.seed())
                    || !row.caseId().equals(scheduledCase.caseId())
                    || !provider.equals(row.provider())
                    || !modelIdentity.requestedModel().equals(row.requestedModel())
                    || !modelIdentity.effectiveModel().equals(row.effectiveModel())
                    || !modelIdentity.digest().equals(row.modelDigest())
                    || !systemPromptIdentity.id().equals(row.systemPromptId())
                    || systemPromptIdentity.version() != row.systemPromptVersion()
                    || !systemPromptIdentity.sha256().equals(row.systemPromptSha256())) {
                throw new IllegalArgumentException(
                        "result row does not match its schedule, system-prompt, or model identity");
            }
        }
        Duration observedRowTime = rows.stream()
                .map(ToolCompatibilityRow::rowLatency)
                .reduce(Duration.ZERO, Duration::plus);
        if (Duration.between(startedAt, finishedAt).compareTo(observedRowTime) < 0) {
            throw new IllegalArgumentException(
                    "result duration cannot be shorter than its sequential row latency");
        }
    }

    static ToolCompatibilityResult create(
            Instant startedAt,
            Instant finishedAt,
            ToolCompatibilityModelIdentity modelIdentity,
            List<ToolCompatibilityRow> rows
    ) {
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        ToolCompatibilityToolDefinitionIdentity toolDefinitions =
                ToolCompatibilityToolDefinitionIdentity.canonical();
        return new ToolCompatibilityResult(
                ToolCompatibilityProtocol.VERSION,
                ToolCompatibilityProtocol.SUITE,
                ToolCompatibilityProtocol.PROVIDER,
                ToolCompatibilityProtocol.EXECUTION_ENGINE,
                ToolCompatibilityProtocol.EXECUTION_STRATEGY,
                ToolCompatibilityProtocol.PULL_MODEL_STRATEGY,
                startedAt,
                finishedAt,
                settings,
                modelIdentity,
                ToolCompatibilityProtocol.systemPromptIdentity(),
                oracle.id(),
                oracle.version(),
                oracle.sha256(),
                selection.caseIds(),
                selection.canonicalCasesSha256(),
                selection.toolNames(),
                selection.toolNamesSha256(),
                toolDefinitions.sha256(),
                ToolCompatibilityProtocol.schedule(selection, settings),
                rows);
    }
}
