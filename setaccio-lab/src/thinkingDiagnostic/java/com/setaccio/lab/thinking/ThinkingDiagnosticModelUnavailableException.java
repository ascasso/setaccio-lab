package com.setaccio.lab.thinking;

/**
 * Raised when a pre-registered diagnostic model is not installed, has no complete digest, does
 * not satisfy its assigned role's capability expectation, or duplicates the other role's artifact.
 */
public class ThinkingDiagnosticModelUnavailableException extends RuntimeException {

    public ThinkingDiagnosticModelUnavailableException(String message) {
        super(message);
    }

    public ThinkingDiagnosticModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
