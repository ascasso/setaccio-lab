package com.setaccio.lab.thinking;

/** Raised when a pre-registered diagnostic model is not installed or has no complete digest. */
public class ThinkingDiagnosticModelUnavailableException extends RuntimeException {

    public ThinkingDiagnosticModelUnavailableException(String message) {
        super(message);
    }

    public ThinkingDiagnosticModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
