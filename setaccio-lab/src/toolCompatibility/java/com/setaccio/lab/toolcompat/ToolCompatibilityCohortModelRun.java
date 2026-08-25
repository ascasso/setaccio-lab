package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One complete sixteen-row model segment inside the ordered cohort execution. */
record ToolCompatibilityCohortModelRun(
        ToolCompatibilityCohortModelIdentity modelIdentity,
        Instant startedAt,
        Instant finishedAt,
        ToolCompatibilitySystemPromptIdentity systemPromptIdentity,
        List<ToolCompatibilityCaseSelection.ScheduledCase> orderedSchedule,
        List<ToolCompatibilityRow> rows
) {

    ToolCompatibilityCohortModelRun {
        if (modelIdentity == null
                || startedAt == null
                || finishedAt == null
                || finishedAt.isBefore(startedAt)
                || systemPromptIdentity == null) {
            throw new IllegalArgumentException("cohort model run identity and timestamps are required");
        }
        ToolCompatibilityProtocol.systemPromptCatalog().requirePrompt(systemPromptIdentity);
        List<ToolCompatibilityCaseSelection.ScheduledCase> expectedSchedule =
                ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings());
        orderedSchedule = List.copyOf(orderedSchedule == null ? List.of() : orderedSchedule);
        rows = List.copyOf(rows == null ? List.of() : rows);
        if (!expectedSchedule.equals(orderedSchedule) || rows.size() != expectedSchedule.size()) {
            throw new IllegalArgumentException(
                    "cohort model run must retain the complete per-model schedule");
        }
        for (int index = 0; index < rows.size(); index++) {
            ToolCompatibilityCaseSelection.ScheduledCase scheduled = orderedSchedule.get(index);
            ToolCompatibilityRow row = rows.get(index);
            Integer expectedSeed = modelIdentity.seedSemantics()
                    == ToolCompatibilityCohortSeedSemantics.SUPPORTED
                    ? scheduled.seed()
                    : null;
            if (row.sequence() != scheduled.sequence()
                    || !row.caseId().equals(scheduled.caseId())
                    || row.repetition() != scheduled.repetition()
                    || !Objects.equals(row.seed(), expectedSeed)
                    || !modelIdentity.requestedTag().equals(row.requestedModel())
                    || !modelIdentity.effectiveInstalledTag().equals(row.effectiveModel())
                    || !modelIdentity.digest().equals(row.modelDigest())
                    || !systemPromptIdentity.id().equals(row.systemPromptId())
                    || systemPromptIdentity.version() != row.systemPromptVersion()
                    || !systemPromptIdentity.sha256().equals(row.systemPromptSha256())
                    || row.globalPairSequence() != null
                    || row.conditionExecutionPosition() != null) {
                throw new IllegalArgumentException(
                        "cohort row drifted from its model, prompt, seed, or schedule identity");
            }
        }
        Duration observed = rows.stream()
                .map(ToolCompatibilityRow::rowLatency)
                .reduce(Duration.ZERO, Duration::plus);
        if (Duration.between(startedAt, finishedAt).compareTo(observed) < 0) {
            throw new IllegalArgumentException(
                    "cohort model duration cannot be shorter than its sequential row latency");
        }
    }

    static ToolCompatibilityCohortModelRun create(
            ToolCompatibilityCohortModelIdentity modelIdentity,
            Instant startedAt,
            Instant finishedAt,
            ToolCompatibilitySystemPromptIdentity systemPromptIdentity,
            List<ToolCompatibilityRow> rows
    ) {
        return new ToolCompatibilityCohortModelRun(
                modelIdentity,
                startedAt,
                finishedAt,
                systemPromptIdentity,
                ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(),
                        ToolCompatibilityProtocol.runSettings()),
                rows);
    }
}
