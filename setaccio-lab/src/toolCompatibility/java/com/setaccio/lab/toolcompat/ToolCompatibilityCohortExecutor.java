package com.setaccio.lab.toolcompat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Executes one immutable cohort in model-major order with no cross-model advisor state. */
final class ToolCompatibilityCohortExecutor {

    ToolCompatibilityCohortResult execute(
            ToolCompatibilityCohortExecutionPlan plan,
            Session session
    ) {
        if (plan == null || session == null) {
            throw new IllegalArgumentException("cohort execution plan and session are required");
        }
        plan.schedule().requireBoundTo(plan.preflight());
        plan.requireRuntimeUnchanged(session.ollamaRuntimeVersion());
        Instant startedAt = Instant.now();
        List<ToolCompatibilityCohortModelRun> modelRuns = new ArrayList<>();
        ToolCompatibilityRowAnalyzer rowAnalyzer = new ToolCompatibilityRowAnalyzer();

        for (ToolCompatibilityCohortModelIdentity modelIdentity :
                plan.preflight().orderedModels()) {
            Instant modelStartedAt = Instant.now();
            List<ToolCompatibilityRow> rows = new ArrayList<>(ToolCompatibilityProtocol.ROW_COUNT);
            ToolCompatibilityInvocationBoundary boundary =
                    new ToolCompatibilityInvocationBoundary();
            for (ToolCompatibilityCohortSchedule.Entry entry :
                    plan.schedule().entriesFor(modelIdentity)) {
                plan.requireRuntimeUnchanged(session.ollamaRuntimeVersion());
                ToolCompatibilityCohortControlledOllamaModel controlled =
                        session.controlledModel(modelIdentity, entry.effectiveSeed());
                if (controlled == null
                        || !modelIdentity.equals(controlled.modelIdentity())
                        || !java.util.Objects.equals(
                                entry.effectiveSeed(), controlled.effectiveSeed())) {
                    throw new ToolCompatibilityProtocolIntegrityException(
                            "Per-row cohort model identity or seed semantics drifted");
                }
                ToolCompatibilityInvocationTrace trace = boundary.invoke(
                        controlled.chatModel(),
                        entry.scheduledCase(),
                        plan.callbacks(),
                        plan.systemPrompt(),
                        modelIdentity.requestedTag(),
                        entry.effectiveSeed());
                rows.add(rowAnalyzer.analyze(
                        entry.scheduledCase(),
                        modelIdentity,
                        trace,
                        plan.systemPrompt(),
                        null,
                        null,
                        entry.effectiveSeed()));
            }
            modelRuns.add(ToolCompatibilityCohortModelRun.create(
                    modelIdentity,
                    modelStartedAt,
                    Instant.now(),
                    plan.systemPrompt(),
                    rows));
        }
        plan.requireRuntimeUnchanged(session.ollamaRuntimeVersion());
        return ToolCompatibilityCohortResult.create(
                startedAt,
                Instant.now(),
                plan,
                modelRuns);
    }

    interface Session {
        String ollamaRuntimeVersion();

        ToolCompatibilityCohortControlledOllamaModel controlledModel(
                ToolCompatibilityCohortModelIdentity modelIdentity,
                Integer effectiveSeed);
    }
}
