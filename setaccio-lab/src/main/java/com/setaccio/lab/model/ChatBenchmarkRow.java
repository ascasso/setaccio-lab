package com.setaccio.lab.model;

public record ChatBenchmarkRow(
        String provider,
        String model,
        String promptId,
        String promptText,
        AdvisorMode advisorMode,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        boolean success,
        String error
) {
    public static ChatBenchmarkRow ok(String provider, String model, ChatBenchmarkPrompt prompt,
                                      AdvisorMode advisorMode, long latencyMs,
                                      Integer tokensIn, Integer tokensOut, String outputText) {
        return new ChatBenchmarkRow(provider, model, prompt.id(), prompt.text(), advisorMode,
                latencyMs, tokensIn, tokensOut, outputText, true, null);
    }

    public static ChatBenchmarkRow fail(String provider, String model, ChatBenchmarkPrompt prompt,
                                        AdvisorMode advisorMode, long latencyMs, String error) {
        return new ChatBenchmarkRow(provider, model, prompt.id(), prompt.text(), advisorMode,
                latencyMs, null, null, null, false, error);
    }
}
