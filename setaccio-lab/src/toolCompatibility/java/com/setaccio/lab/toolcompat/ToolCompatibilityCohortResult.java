package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete prompt-bound, runtime-bound result for one ordered local-model cohort. */
record ToolCompatibilityCohortResult(
        int protocolVersion,
        String suite,
        String provider,
        String executionEngine,
        String executionStrategy,
        String pullModelStrategy,
        Instant startedAt,
        Instant finishedAt,
        ToolCompatibilityCohortRunSettings runSettings,
        String ollamaRuntimeVersion,
        ToolCompatibilityHumanDecision humanDecision,
        ToolCompatibilityPromptCondition promptCondition,
        ToolCompatibilitySystemPromptIdentity systemPromptIdentity,
        ToolCompatibilityCohortSchedule cohortSchedule,
        String caseOracleId,
        int caseOracleVersion,
        String caseOracleSha256,
        List<String> orderedCaseIds,
        String canonicalCasesSha256,
        List<String> orderedToolNames,
        String toolNamesSha256,
        String toolDefinitionsSha256,
        List<ToolCompatibilityCohortModelIdentity> orderedModels,
        List<ToolCompatibilityCohortModelRun> modelRuns
) {

    static final int VERSION = 1;
    static final String SUITE = "ollama-tool-compatibility-cohort";
    static final String RAW_FILENAME = "tool-compatibility-cohort-results.json";

    ToolCompatibilityCohortResult {
        if (protocolVersion != VERSION
                || !SUITE.equals(suite)
                || !ToolCompatibilityProtocol.PROVIDER.equals(provider)
                || !ToolCompatibilityProtocol.EXECUTION_ENGINE.equals(executionEngine)
                || !ToolCompatibilityProtocol.EXECUTION_STRATEGY.equals(executionStrategy)
                || !ToolCompatibilityProtocol.PULL_MODEL_STRATEGY.equals(pullModelStrategy)) {
            throw new IllegalArgumentException("cohort result protocol identity drifted");
        }
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("cohort result timestamps must be complete and ordered");
        }
        if (!ToolCompatibilityCohortRunSettings.locked().equals(runSettings)
                || ollamaRuntimeVersion == null
                || ollamaRuntimeVersion.isBlank()
                || humanDecision == null
                || promptCondition == null
                || systemPromptIdentity == null
                || cohortSchedule == null) {
            throw new IllegalArgumentException("cohort result settings and identities are incomplete");
        }
        ToolCompatibilityPhase2DecisionLock.requireMatches(humanDecision);
        ToolCompatibilityCohortPromptPolicy.Selection policy =
                ToolCompatibilityCohortPromptPolicy.resolve(
                        humanDecision, ToolCompatibilityPhase2DecisionLock.binding());
        if (policy.executionState()
                        != ToolCompatibilityCohortPromptPolicy.ExecutionState.EXECUTABLE
                || policy.promptCondition() != promptCondition
                || !policy.prompt(ToolCompatibilityProtocol.systemPromptCatalog())
                        .equals(systemPromptIdentity)) {
            throw new IllegalArgumentException(
                    "cohort result prompt identity does not match its bound human decision");
        }
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        if (!oracle.id().equals(caseOracleId)
                || oracle.version() != caseOracleVersion
                || !oracle.sha256().equals(caseOracleSha256)) {
            throw new IllegalArgumentException("cohort result case-oracle identity drifted");
        }
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        ToolCompatibilityToolDefinitionIdentity toolDefinitions =
                ToolCompatibilityToolDefinitionIdentity.canonical();
        orderedCaseIds = List.copyOf(orderedCaseIds == null ? List.of() : orderedCaseIds);
        orderedToolNames = List.copyOf(orderedToolNames == null ? List.of() : orderedToolNames);
        if (!selection.caseIds().equals(orderedCaseIds)
                || !selection.canonicalCasesSha256().equals(canonicalCasesSha256)
                || !selection.toolNames().equals(orderedToolNames)
                || !selection.toolNamesSha256().equals(toolNamesSha256)
                || !toolDefinitions.orderedToolNames().equals(orderedToolNames)
                || !toolDefinitions.sha256().equals(toolDefinitionsSha256)) {
            throw new IllegalArgumentException("cohort result case or tool identity drifted");
        }
        orderedModels = List.copyOf(orderedModels == null ? List.of() : orderedModels);
        modelRuns = List.copyOf(modelRuns == null ? List.of() : modelRuns);
        if (!cohortSchedule.ollamaRuntimeVersion().equals(ollamaRuntimeVersion)
                || !cohortSchedule.orderedModels().equals(orderedModels)
                || modelRuns.size() != orderedModels.size()) {
            throw new IllegalArgumentException(
                    "cohort result models or runtime drifted from its schedule");
        }
        for (int index = 0; index < orderedModels.size(); index++) {
            ToolCompatibilityCohortModelRun run = modelRuns.get(index);
            if (!orderedModels.get(index).equals(run.modelIdentity())
                    || !systemPromptIdentity.equals(run.systemPromptIdentity())) {
                throw new IllegalArgumentException(
                        "cohort model runs must preserve exact order and one prompt policy");
            }
        }
        Duration observed = modelRuns.stream()
                .flatMap(run -> run.rows().stream())
                .map(ToolCompatibilityRow::rowLatency)
                .reduce(Duration.ZERO, Duration::plus);
        if (Duration.between(startedAt, finishedAt).compareTo(observed) < 0) {
            throw new IllegalArgumentException(
                    "cohort duration cannot be shorter than its sequential row latency");
        }
    }

    static ToolCompatibilityCohortResult create(
            Instant startedAt,
            Instant finishedAt,
            ToolCompatibilityCohortExecutionPlan plan,
            List<ToolCompatibilityCohortModelRun> modelRuns
    ) {
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        ToolCompatibilityCaseSelection selection = ToolCompatibilityProtocol.caseSelection();
        ToolCompatibilityToolDefinitionIdentity toolDefinitions =
                ToolCompatibilityToolDefinitionIdentity.canonical();
        return new ToolCompatibilityCohortResult(
                VERSION,
                SUITE,
                ToolCompatibilityProtocol.PROVIDER,
                ToolCompatibilityProtocol.EXECUTION_ENGINE,
                ToolCompatibilityProtocol.EXECUTION_STRATEGY,
                ToolCompatibilityProtocol.PULL_MODEL_STRATEGY,
                startedAt,
                finishedAt,
                ToolCompatibilityCohortRunSettings.locked(),
                plan.preflight().ollamaRuntimeVersion(),
                plan.humanDecision(),
                plan.promptPolicy().promptCondition(),
                plan.systemPrompt(),
                plan.schedule(),
                oracle.id(),
                oracle.version(),
                oracle.sha256(),
                selection.caseIds(),
                selection.canonicalCasesSha256(),
                selection.toolNames(),
                selection.toolNamesSha256(),
                toolDefinitions.sha256(),
                plan.preflight().orderedModels(),
                modelRuns);
    }

    static Map<String, Object> manifestSettings(ToolCompatibilityCohortResult result) {
        if (result == null) {
            throw new IllegalArgumentException("cohort result is required");
        }
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("ollamaRuntimeVersion", result.ollamaRuntimeVersion());
        settings.put("humanDecision", result.humanDecision());
        settings.put("promptCondition", result.promptCondition());
        settings.put("systemPromptIdentity", result.systemPromptIdentity());
        settings.put("cohortSchedule", result.cohortSchedule());
        settings.put("caseOracleId", result.caseOracleId());
        settings.put("caseOracleVersion", result.caseOracleVersion());
        settings.put("caseOracleSha256", result.caseOracleSha256());
        settings.put("orderedCaseIds", result.orderedCaseIds());
        settings.put("canonicalCasesSha256", result.canonicalCasesSha256());
        settings.put("orderedToolNames", result.orderedToolNames());
        settings.put("toolNamesSha256", result.toolNamesSha256());
        settings.put("toolDefinitionsSha256", result.toolDefinitionsSha256());
        settings.put("orderedModels", result.orderedModels());
        return Collections.unmodifiableMap(settings);
    }
}
