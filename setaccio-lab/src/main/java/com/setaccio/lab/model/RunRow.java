package com.setaccio.lab.model;

public record RunRow(
        String model,
        String input,
        String inputHash,
        long latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String outputText,
        boolean success,
        String error
) {
    public static RunRow ok(String model, String input, String hash, long latencyMs,
                            Integer tokensIn, Integer tokensOut, String outputText) {
        return new RunRow(model, input, hash, latencyMs, tokensIn, tokensOut,
                outputText, true, null);
    }

    public static RunRow fail(String model, String input, String hash, long latencyMs, String error) {
        return new RunRow(model, input, hash, latencyMs, null, null, null, false, error);
    }
}
