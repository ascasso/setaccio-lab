package com.setaccio.lab.toolcompat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Executes the one locked 32-row interleaved prompt matrix through the standard advisor boundary. */
final class ToolCompatibilityPromptMatrixExecutor {

    Execution execute(ToolCompatibilityPromptMatrixPreflight.Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared prompt matrix must not be null");
        }
        Instant startedAt = Instant.now();
        Map<ToolCompatibilityPromptCondition, List<ToolCompatibilityRow>> rowsByCondition =
                new EnumMap<>(ToolCompatibilityPromptCondition.class);
        for (ToolCompatibilityPromptCondition condition : ToolCompatibilityPromptCondition.values()) {
            rowsByCondition.put(condition, new ArrayList<>(ToolCompatibilityProtocol.ROW_COUNT));
        }
        ToolCompatibilityInvocationBoundary boundary = new ToolCompatibilityInvocationBoundary();
        ToolCompatibilityRowAnalyzer rowAnalyzer = new ToolCompatibilityRowAnalyzer();
        for (ToolCompatibilityPairedSchedule.Entry entry : prepared.pairedSchedule().entries()) {
            prepared.requireRepositoryUnchanged();
            ToolCompatibilityControlledOllamaModel controlledModel = prepared.session()
                    .controlledModel(entry.seed());
            if (controlledModel == null
                    || !prepared.modelIdentity().equals(controlledModel.modelIdentity())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Per-row controlled model identity drifted from paired preflight");
            }
            ToolCompatibilitySystemPromptIdentity systemPrompt = entry.condition().prompt(prepared.catalog());
            ToolCompatibilityInvocationTrace trace = boundary.invoke(
                    controlledModel.chatModel(),
                    entry.scheduledCase(),
                    prepared.callbacks(),
                    systemPrompt);
            rowsByCondition.get(entry.condition()).add(rowAnalyzer.analyze(
                    entry.scheduledCase(),
                    prepared.modelIdentity(),
                    trace,
                    systemPrompt,
                    entry.globalPairSequence(),
                    entry.conditionExecutionPosition()));
        }
        Instant finishedAt = Instant.now();
        ToolCompatibilityPromptMatrixResult untreated = ToolCompatibilityPromptMatrixResult.create(
                startedAt,
                finishedAt,
                prepared.modelIdentity(),
                ToolCompatibilityPromptCondition.UNTREATED,
                prepared.pairedSchedule(),
                rowsByCondition.get(ToolCompatibilityPromptCondition.UNTREATED).stream()
                        .sorted(java.util.Comparator.comparingInt(ToolCompatibilityRow::sequence))
                        .toList());
        ToolCompatibilityPromptMatrixResult prompted = ToolCompatibilityPromptMatrixResult.create(
                startedAt,
                finishedAt,
                prepared.modelIdentity(),
                ToolCompatibilityPromptCondition.PROMPTED,
                prepared.pairedSchedule(),
                rowsByCondition.get(ToolCompatibilityPromptCondition.PROMPTED).stream()
                        .sorted(java.util.Comparator.comparingInt(ToolCompatibilityRow::sequence))
                        .toList());
        return new Execution(untreated, prompted);
    }

    record Execution(
            ToolCompatibilityPromptMatrixResult untreated,
            ToolCompatibilityPromptMatrixResult prompted
    ) {

        Execution {
            if (untreated == null || prompted == null) {
                throw new IllegalArgumentException("Both prompt-condition results are required");
            }
            if (untreated.promptCondition() != ToolCompatibilityPromptCondition.UNTREATED
                    || prompted.promptCondition() != ToolCompatibilityPromptCondition.PROMPTED
                    || !untreated.pairedExecutionSchedule().equals(prompted.pairedExecutionSchedule())) {
                throw new IllegalArgumentException("paired execution results do not share the locked conditions and schedule");
            }
            if (untreated.systemPromptIdentity().equals(prompted.systemPromptIdentity())
                    || untreated.protocolVersion() != prompted.protocolVersion()
                    || !untreated.suite().equals(prompted.suite())
                    || !untreated.provider().equals(prompted.provider())
                    || !untreated.executionEngine().equals(prompted.executionEngine())
                    || !untreated.executionStrategy().equals(prompted.executionStrategy())
                    || !untreated.pullModelStrategy().equals(prompted.pullModelStrategy())
                    || !untreated.runSettings().equals(prompted.runSettings())
                    || !untreated.modelIdentity().equals(prompted.modelIdentity())
                    || !untreated.caseOracleId().equals(prompted.caseOracleId())
                    || untreated.caseOracleVersion() != prompted.caseOracleVersion()
                    || !untreated.caseOracleSha256().equals(prompted.caseOracleSha256())
                    || !untreated.orderedCaseIds().equals(prompted.orderedCaseIds())
                    || !untreated.canonicalCasesSha256().equals(prompted.canonicalCasesSha256())
                    || !untreated.orderedToolNames().equals(prompted.orderedToolNames())
                    || !untreated.toolNamesSha256().equals(prompted.toolNamesSha256())
                    || !untreated.toolDefinitionsSha256().equals(prompted.toolDefinitionsSha256())
                    || !untreated.orderedSchedule().equals(prompted.orderedSchedule())
                    || !untreated.startedAt().equals(prompted.startedAt())
                    || !untreated.finishedAt().equals(prompted.finishedAt())) {
                throw new IllegalArgumentException(
                        "paired execution results must differ only by prompt identity and observed outcomes");
            }
        }
    }
}
