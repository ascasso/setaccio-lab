package com.setaccio.lab.model;

import java.time.Instant;
import java.util.List;

public record ToolBenchmarkResult(
        String suite,
        String provider,
        AdvisorMode advisorMode,
        Instant startedAt,
        Instant finishedAt,
        String host,
        String ollamaBaseUrl,
        List<String> requestedTools,
        List<String> availableTools,
        List<ToolBenchmarkRow> runs
) {}
