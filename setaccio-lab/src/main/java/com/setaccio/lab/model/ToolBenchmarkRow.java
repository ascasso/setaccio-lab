package com.setaccio.lab.model;

import java.util.List;

public record ToolBenchmarkRow(
        String provider,
        String model,
        String promptId,
        String promptText,
        ToolBenchmarkExpectation expectation,
        AdvisorMode advisorMode,
        int repetition,
        Integer pairExecutionOrder,
        String comparisonPairId,
        int generationSeed,
        List<String> requestedTools,
        List<ToolCallObservation> selectedToolCalls,
        List<ToolExecutionObservation> executedToolResponses,
        List<ToolSearchObservation> toolSearchObservations,
        List<String> toolErrors,
        List<ToolBenchmarkAssertion> assertions,
        boolean contractPassed,
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
        return new ToolBenchmarkRow(provider, model, prompt.id(), prompt.text(), prompt.expectation(), advisorMode,
                1, null, null, ToolBenchmarkRunSettings.DEFAULT_BASE_SEED, requestedTools,
                selectedToolCalls, executedToolResponses, List.of(), List.of(), List.of(), true,
                latencyMs, tokensIn, tokensOut, outputText, true, null);
    }

    public static ToolBenchmarkRow fail(String provider, String model, ToolBenchmarkPrompt prompt,
                                        AdvisorMode advisorMode, List<String> requestedTools,
                                        List<ToolCallObservation> selectedToolCalls,
                                        List<ToolExecutionObservation> executedToolResponses,
                                        List<String> toolErrors, long latencyMs, String error) {
        return new ToolBenchmarkRow(provider, model, prompt.id(), prompt.text(), prompt.expectation(), advisorMode,
                1, null, null, ToolBenchmarkRunSettings.DEFAULT_BASE_SEED, requestedTools,
                selectedToolCalls, executedToolResponses, List.of(), toolErrors, List.of(), false,
                latencyMs, null, null, null, false, error);
    }
}
