package com.setaccio.lab.model;

import java.time.Instant;
import java.util.List;

public record BenchmarkResult(
        String suite,
        Instant startedAt,
        Instant finishedAt,
        String host,
        String ollamaBaseUrl,
        List<RunRow> runs
) {}
