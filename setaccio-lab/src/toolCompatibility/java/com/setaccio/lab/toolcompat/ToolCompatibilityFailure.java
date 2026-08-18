package com.setaccio.lab.toolcompat;

record ToolCompatibilityFailure(String category, String safeMessage) {

    static final String PROVIDER_FAILURE = "PROVIDER_FAILURE";
    static final String CALLBACK_RESOLUTION_FAILURE = "CALLBACK_RESOLUTION_FAILURE";
    static final String CALLBACK_BINDING_FAILURE = "CALLBACK_BINDING_FAILURE";
    static final String CALLBACK_INVOCATION_FAILURE = "CALLBACK_INVOCATION_FAILURE";
    static final String CALLBACK_FAILURE = "CALLBACK_FAILURE";
    static final String ROW_TIMEOUT = "ROW_TIMEOUT";

    ToolCompatibilityFailure {
        category = requireCategory(category);
        String expectedMessage = safeMessageFor(category);
        if (!expectedMessage.equals(safeMessage)) {
            throw new IllegalArgumentException("safeMessage must equal the public-safe category message");
        }
    }

    static ToolCompatibilityFailure of(String category) {
        return new ToolCompatibilityFailure(category, safeMessageFor(category));
    }

    static ToolCompatibilityFailure callback(ToolCompatibilityCallbackFailureKind kind) {
        return of(kind == null ? CALLBACK_FAILURE : kind.name());
    }

    private static String requireCategory(String category) {
        if (category == null) {
            throw new IllegalArgumentException("failure category must not be null");
        }
        return switch (category) {
            case PROVIDER_FAILURE,
                    CALLBACK_RESOLUTION_FAILURE,
                    CALLBACK_BINDING_FAILURE,
                    CALLBACK_INVOCATION_FAILURE,
                    CALLBACK_FAILURE,
                    ROW_TIMEOUT -> category;
            default -> throw new IllegalArgumentException(
                    "Unknown tool compatibility failure category: " + category);
        };
    }

    private static String safeMessageFor(String category) {
        return switch (requireCategory(category)) {
            case PROVIDER_FAILURE -> "Ollama provider turn failed";
            case CALLBACK_RESOLUTION_FAILURE -> "Model-selected tool could not be resolved";
            case CALLBACK_BINDING_FAILURE -> "Tool callback arguments could not be bound";
            case CALLBACK_INVOCATION_FAILURE -> "Tool callback invocation failed";
            case CALLBACK_FAILURE -> "Tool callback failed";
            case ROW_TIMEOUT -> "Tool compatibility row deadline elapsed";
            default -> throw new IllegalStateException("Unreachable failure category");
        };
    }
}
