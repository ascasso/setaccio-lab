package com.setaccio.lab.thinking;

/** The Spring AI boundary used for one pre-registered diagnostic arm. */
public enum ThinkingDiagnosticExecutionBoundary {

    FACT_CHECK_EVALUATOR("spring-ai-fact-checking-evaluator-recording-boundary"),
    CHAT_INVOCATION("spring-ai-provider-neutral-chat-invocation");

    private final String id;

    ThinkingDiagnosticExecutionBoundary(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
