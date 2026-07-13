package com.setaccio.lab.model;

public record ToolBenchmarkAssertion(
        String check,
        String target,
        boolean passed,
        String detail
) {}
