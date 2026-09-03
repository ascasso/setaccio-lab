package com.setaccio.lab.thinking;

/**
 * The classified shape of one recorded provider response.
 *
 * <p>Content and reasoning are separate dimensions, so an empty visible answer that carried
 * reasoning is a different outcome from an empty answer that carried none. The Phase 4 and
 * Phase 5 suites cannot tell those apart; that is exactly what this diagnostic separates.
 */
public enum ThinkingDiagnosticOutcome {
    CONTENT_WITHOUT_THINKING,
    CONTENT_WITH_THINKING,
    EMPTY_CONTENT_WITH_THINKING,
    EMPTY_CONTENT_WITHOUT_THINKING,
    MODEL_UNAVAILABLE,
    TIMEOUT,
    PROVIDER_FAILURE
}
