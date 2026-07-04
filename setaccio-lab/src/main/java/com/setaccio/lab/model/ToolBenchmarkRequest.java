package com.setaccio.lab.model;

import java.util.List;

public record ToolBenchmarkRequest(
        String models,
        AdvisorMode advisorMode,
        List<ToolBenchmarkPrompt> prompts,
        List<String> requestedTools,
        Boolean useDefaultPrompts
) {
    public AdvisorMode resolvedAdvisorMode() {
        return advisorMode == null ? AdvisorMode.STANDARD : advisorMode;
    }

    public boolean resolvedUseDefaultPrompts() {
        return useDefaultPrompts == null || useDefaultPrompts;
    }
}
