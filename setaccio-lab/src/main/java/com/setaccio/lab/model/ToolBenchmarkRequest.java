package com.setaccio.lab.model;

import java.util.List;

public record ToolBenchmarkRequest(
        String models,
        AdvisorMode advisorMode,
        List<ToolBenchmarkPrompt> prompts,
        List<String> requestedTools,
        Boolean useDefaultPrompts,
        Integer repetitions,
        Double temperature,
        Integer baseSeed,
        Integer maxTokens,
        ToolBenchmarkComparisonOrder comparisonOrder
) {
    public AdvisorMode resolvedAdvisorMode() {
        return advisorMode == null ? AdvisorMode.STANDARD : advisorMode;
    }

    public boolean resolvedUseDefaultPrompts() {
        return useDefaultPrompts == null || useDefaultPrompts;
    }

    public ToolBenchmarkRunSettings resolvedRunSettings(boolean comparison) {
        return ToolBenchmarkRunSettings.resolve(
                repetitions,
                temperature,
                baseSeed,
                maxTokens,
                comparisonOrder,
                comparison
        );
    }
}
