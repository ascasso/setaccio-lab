package com.setaccio.lab.model;

import java.time.Instant;
import java.util.List;

public record ChatBenchmarkResult(
        String suite,
        String provider,
        AdvisorMode advisorMode,
        Instant startedAt,
        Instant finishedAt,
        String host,
        String ollamaBaseUrl,
        List<ChatBenchmarkRow> runs
) {}
