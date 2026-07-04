package com.setaccio.lab.model;

import java.util.List;

public record ToolBenchmarkRow(
        String provider,
        String model,
        String promptId,
        String promptText,
        AdvisorMode advisorMode,
        List<String> requestedTools,
        List<ToolCallObservation> selectedToolCalls,
        List<ToolExecutionObservation> executedToolResponses,
        List<String> toolErrors,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        boolean success,
        String error
) {
    public static ToolBenchmarkRow ok(String provider, String model, ToolBenchmarkPrompt prompt, AdvisorMode advisorMode,
                                      List<String> requestedTools, List<ToolCallObservation> selectedToolCalls,
                                      List<ToolExecutionObservation> executedToolResponses, long latencyMs,
                                      Integer tokensIn, Integer tokensOut, String outputText) {
        return new ToolBenchmarkRow(provider, model, prompt.id(), prompt.text(), advisorMode, requestedTools,
                selectedToolCalls, executedToolResponses, List.of(), latencyMs, tokensIn, tokensOut,
                outputText, true, null);
    }

    public static ToolBenchmarkRow fail(String provider, String model, ToolBenchmarkPrompt prompt,
                                        AdvisorMode advisorMode, List<String> requestedTools,
                                        List<ToolCallObservation> selectedToolCalls,
                                        List<ToolExecutionObservation> executedToolResponses,
                                        List<String> toolErrors, long latencyMs, String error) {
        return new ToolBenchmarkRow(provider, model, prompt.id(), prompt.text(), advisorMode, requestedTools,
                selectedToolCalls, executedToolResponses, toolErrors, latencyMs, null, null,
                null, false, error);
    }
}
