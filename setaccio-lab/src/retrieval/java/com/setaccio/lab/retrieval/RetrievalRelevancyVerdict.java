package com.setaccio.lab.retrieval;

/** Strict normalization of the raw yes/no response emitted through Spring AI's relevance evaluator. */
public enum RetrievalRelevancyVerdict {
    YES,
    NO;

    static RetrievalRelevancyVerdict normalize(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "yes" -> YES;
            case "no" -> NO;
            default -> null;
        };
    }
}
