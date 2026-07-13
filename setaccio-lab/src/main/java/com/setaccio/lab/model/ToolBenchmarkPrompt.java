package com.setaccio.lab.model;

public record ToolBenchmarkPrompt(
        String id,
        String text,
        ToolBenchmarkExpectation expectation
) {
    public ToolBenchmarkPrompt {
        expectation = expectation == null ? ToolBenchmarkExpectation.none() : expectation;
    }

    public ToolBenchmarkPrompt(String id, String text) {
        this(id, text, ToolBenchmarkExpectation.none());
    }
}
