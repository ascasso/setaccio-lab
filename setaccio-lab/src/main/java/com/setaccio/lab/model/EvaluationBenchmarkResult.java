package com.setaccio.lab.model;

import java.time.Instant;
import java.util.List;

public record EvaluationBenchmarkResult(
        String suite,
        String evaluatorProvider,
        String evaluatorModel,
        Instant startedAt,
        Instant finishedAt,
        String host,
        List<EvaluationBenchmarkRow> runs
) {}
