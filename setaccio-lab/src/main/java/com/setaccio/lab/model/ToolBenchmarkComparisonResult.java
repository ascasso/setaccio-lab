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
        ToolBenchmarkRunSettings runSettings,
        String executionStrategy,
        List<String> requestedTools,
        List<String> availableTools,
        ToolBenchmarkResult standard,
        ToolBenchmarkResult toolSearch
) {}
