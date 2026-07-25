package com.setaccio.lab.vision;

import java.time.Instant;
import java.util.List;

public record VisionMatrixResult(
        String suite,
        String provider,
        String host,
        Instant startedAt,
        Instant finishedAt,
        VisionMatrixRunSettings runSettings,
        List<VisionMatrixModelIdentity> modelIdentities,
        String executionStrategy,
        String pullModelStrategy,
        String promptId,
        String promptVersion,
        String promptSha256,
        List<VisionMatrixInput> inputs,
        List<VisionMatrixRow> rows
) {

    public VisionMatrixResult {
        modelIdentities = modelIdentities == null ? List.of() : List.copyOf(modelIdentities);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
