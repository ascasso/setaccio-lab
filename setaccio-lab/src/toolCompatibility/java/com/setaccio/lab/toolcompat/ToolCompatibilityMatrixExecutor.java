package com.setaccio.lab.toolcompat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Executes the locked schedule sequentially through one standard-advisor boundary. */
final class ToolCompatibilityMatrixExecutor {

    ToolCompatibilityResult execute(ToolCompatibilityPreflight.Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared tool compatibility run must not be null");
        }
        Instant startedAt = Instant.now();
        List<ToolCompatibilityRow> rows = new ArrayList<>(ToolCompatibilityProtocol.ROW_COUNT);
        ToolCompatibilityInvocationBoundary boundary = new ToolCompatibilityInvocationBoundary();
        ToolCompatibilityRowAnalyzer rowAnalyzer = new ToolCompatibilityRowAnalyzer();
        for (ToolCompatibilityCaseSelection.ScheduledCase scheduledCase :
                ToolCompatibilityProtocol.schedule(
                        ToolCompatibilityProtocol.caseSelection(), prepared.settings())) {
            ToolCompatibilityControlledOllamaModel controlledModel =
                    prepared.session().controlledModel(scheduledCase.seed());
            if (controlledModel == null
                    || !prepared.modelIdentity().equals(controlledModel.modelIdentity())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Per-row controlled model identity drifted from preflight");
            }
            ToolCompatibilityInvocationTrace trace = boundary.invoke(
                    controlledModel.chatModel(), scheduledCase, prepared.callbacks());
            rows.add(rowAnalyzer.analyze(scheduledCase, prepared.modelIdentity(), trace));
        }
        return ToolCompatibilityResult.create(
                startedAt,
                Instant.now(),
                prepared.modelIdentity(),
                List.copyOf(rows));
    }
}
