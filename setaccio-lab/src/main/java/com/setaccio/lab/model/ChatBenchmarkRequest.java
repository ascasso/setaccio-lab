package com.setaccio.lab.model;

import java.util.List;

public record ChatBenchmarkRequest(
        String models,
        AdvisorMode advisorMode,
        List<ChatBenchmarkPrompt> prompts,
        Boolean useDefaultPrompts
) {
    public AdvisorMode resolvedAdvisorMode() {
        return advisorMode == null ? AdvisorMode.STANDARD : advisorMode;
    }

    public boolean resolvedUseDefaultPrompts() {
        return useDefaultPrompts == null || useDefaultPrompts;
    }
}
