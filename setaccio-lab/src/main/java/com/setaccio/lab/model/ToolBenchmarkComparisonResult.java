package com.setaccio.lab.model;

import java.time.Instant;
import java.util.List;

public record ToolBenchmarkComparisonResult(
        String suite,
        String provider,
        String toolSearchIndexType,
        Instant startedAt,
        Instant finishedAt,
        String host,
        String ollamaBaseUrl,
        List<String> requestedTools,
        List<String> availableTools,
        ToolBenchmarkResult standard,
        ToolBenchmarkResult toolSearch
) {}
