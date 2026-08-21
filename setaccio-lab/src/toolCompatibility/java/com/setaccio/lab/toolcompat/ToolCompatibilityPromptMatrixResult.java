package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One complete, condition-specific Phase 2 result with its shared paired schedule. */
record ToolCompatibilityPromptMatrixResult(
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
        ToolCompatibilityPromptCondition promptCondition,
        ToolCompatibilitySystemPromptIdentity systemPromptIdentity,
        ToolCompatibilityPairedSchedule pairedExecutionSchedule,
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

    static final int VERSION = 1;
    static final String SUITE = "ollama-tool-compatibility-prompt-matrix";
    static final String RAW_FILENAME = "tool-compatibility-prompt-matrix-results.json";

    ToolCompatibilityPromptMatrixResult {
        if (protocolVersion != VERSION
                || !SUITE.equals(suite)
                || !ToolCompatibilityProtocol.PROVIDER.equals(provider)
                || !ToolCompatibilityProtocol.EXECUTION_ENGINE.equals(executionEngine)
                || !ToolCompatibilityProtocol.EXECUTION_STRATEGY.equals(executionStrategy)
                || !ToolCompatibilityProtocol.PULL_MODEL_STRATEGY.equals(pullModelStrategy)) {
            throw new IllegalArgumentException("result protocol identity must equal the locked Phase 2 protocol");
        }
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("result timestamps must be ordered and complete");
        }
        ToolCompatibilityRunSettings expectedSettings = ToolCompatibilityProtocol.runSettings();
        if (!expectedSettings.equals(runSettings)) {
            throw new IllegalArgumentException("result settings must equal the locked Phase 2 settings");
        }
        if (modelIdentity == null || !runSettings.requestedModel().equals(modelIdentity.requestedModel())) {
            throw new IllegalArgumentException("result model identity must match its requested model");
        }
        if (promptCondition == null || systemPromptIdentity == null || pairedExecutionSchedule == null) {
            throw new IllegalArgumentException("result prompt condition, identity, and paired schedule are required");
        }
        ToolCompatibilitySystemPromptCatalog catalog = ToolCompatibilityProtocol.systemPromptCatalog();
        ToolCompatibilitySystemPromptIdentity expectedPrompt = promptCondition.prompt(catalog);
        if (!expectedPrompt.equals(systemPromptIdentity)) {
            throw new IllegalArgumentException("result system-prompt identity does not match its locked condition");
        }
        pairedExecutionSchedule.requireLocked();

        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        if (!oracle.id().equals(caseOracleId)
                || oracle.version() != caseOracleVersion
                || !oracle.sha256().equals(caseOracleSha256)) {
            throw new IllegalArgumentException("result case-oracle identity must equal the locked oracle");
        }
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        orderedCaseIds = List.copyOf(orderedCaseIds == null ? List.of() : orderedCaseIds);
        orderedToolNames = List.copyOf(orderedToolNames == null ? List.of() : orderedToolNames);
        ToolCompatibilityToolDefinitionIdentity toolDefinitions = ToolCompatibilityToolDefinitionIdentity.canonical();
        if (!selection.caseIds().equals(orderedCaseIds)
                || !selection.canonicalCasesSha256().equals(canonicalCasesSha256)
                || !selection.toolNames().equals(orderedToolNames)
                || !selection.toolNamesSha256().equals(toolNamesSha256)
                || !toolDefinitions.orderedToolNames().equals(orderedToolNames)
                || !toolDefinitions.sha256().equals(toolDefinitionsSha256)) {
            throw new IllegalArgumentException("result canonical case or tool selection drifted");
        }
        List<ToolCompatibilityCaseSelection.ScheduledCase> expectedSchedule = ToolCompatibilityProtocol.schedule(
                selection, runSettings);
        orderedSchedule = List.copyOf(orderedSchedule == null ? List.of() : orderedSchedule);
        rows = List.copyOf(rows == null ? List.of() : rows);
        if (!expectedSchedule.equals(orderedSchedule) || rows.size() != orderedSchedule.size()) {
            throw new IllegalArgumentException("result must contain the complete locked condition schedule");
        }
        for (int index = 0; index < rows.size(); index++) {
            ToolCompatibilityCaseSelection.ScheduledCase scheduledCase = orderedSchedule.get(index);
            ToolCompatibilityPairedSchedule.Entry pairedEntry = pairedExecutionSchedule.requireEntry(
                    promptCondition, scheduledCase.sequence());
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
                    || !systemPromptIdentity.sha256().equals(row.systemPromptSha256())
                    || !Integer.valueOf(pairedEntry.globalPairSequence()).equals(row.globalPairSequence())
                    || pairedEntry.conditionExecutionPosition() != row.conditionExecutionPosition()) {
                throw new IllegalArgumentException("result row does not match its paired schedule, condition, or model identity");
            }
        }
        Duration observedConditionTime = rows.stream()
                .map(ToolCompatibilityRow::rowLatency)
                .reduce(Duration.ZERO, Duration::plus);
        if (Duration.between(startedAt, finishedAt).compareTo(observedConditionTime) < 0) {
            throw new IllegalArgumentException(
                    "result duration cannot be shorter than its sequential condition row latency");
        }
    }

    static ToolCompatibilityPromptMatrixResult create(
            Instant startedAt,
            Instant finishedAt,
            ToolCompatibilityModelIdentity modelIdentity,
            ToolCompatibilityPromptCondition promptCondition,
            ToolCompatibilityPairedSchedule pairedExecutionSchedule,
            List<ToolCompatibilityRow> rows
    ) {
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        ToolCompatibilityToolDefinitionIdentity toolDefinitions = ToolCompatibilityToolDefinitionIdentity.canonical();
        ToolCompatibilitySystemPromptIdentity systemPrompt = promptCondition.prompt(
                ToolCompatibilityProtocol.systemPromptCatalog());
        return new ToolCompatibilityPromptMatrixResult(
                VERSION,
                SUITE,
                ToolCompatibilityProtocol.PROVIDER,
                ToolCompatibilityProtocol.EXECUTION_ENGINE,
                ToolCompatibilityProtocol.EXECUTION_STRATEGY,
                ToolCompatibilityProtocol.PULL_MODEL_STRATEGY,
                startedAt,
                finishedAt,
                settings,
                modelIdentity,
                promptCondition,
                systemPrompt,
                pairedExecutionSchedule,
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

    static Map<String, Object> manifestSettings(ToolCompatibilityPromptMatrixResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("modelIdentity", result.modelIdentity());
        settings.put("promptCondition", result.promptCondition());
        settings.put("systemPromptIdentity", result.systemPromptIdentity());
        settings.put("pairedExecutionSchedule", result.pairedExecutionSchedule());
        settings.put("caseOracleId", result.caseOracleId());
        settings.put("caseOracleVersion", result.caseOracleVersion());
        settings.put("caseOracleSha256", result.caseOracleSha256());
        settings.put("orderedCaseIds", result.orderedCaseIds());
        settings.put("canonicalCasesSha256", result.canonicalCasesSha256());
        settings.put("orderedToolNames", result.orderedToolNames());
        settings.put("toolNamesSha256", result.toolNamesSha256());
        settings.put("toolDefinitionsSha256", result.toolDefinitionsSha256());
        settings.put("orderedSchedule", result.orderedSchedule());
        return Collections.unmodifiableMap(settings);
    }
}
