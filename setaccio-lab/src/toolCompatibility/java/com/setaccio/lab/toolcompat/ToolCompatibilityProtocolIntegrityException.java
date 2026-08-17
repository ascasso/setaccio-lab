package com.setaccio.lab.toolcompat;

final class ToolCompatibilityProtocolIntegrityException extends IllegalStateException {

    private final ToolCompatibilitySchemaIssue schemaIssue;
    private final ToolCompatibilityInvocationTrace invocationTrace;

    ToolCompatibilityProtocolIntegrityException(String message) {
        super(message);
        this.schemaIssue = null;
        this.invocationTrace = null;
    }

    ToolCompatibilityProtocolIntegrityException(String message, Throwable cause) {
        super(message, cause);
        this.schemaIssue = null;
        this.invocationTrace = null;
    }

    ToolCompatibilityProtocolIntegrityException(String message, ToolCompatibilityInvocationTrace invocationTrace) {
        super(message);
        this.schemaIssue = null;
        this.invocationTrace = invocationTrace;
    }

    ToolCompatibilityProtocolIntegrityException(
            String message,
            Throwable cause,
            ToolCompatibilityInvocationTrace invocationTrace
    ) {
        super(message, cause);
        this.schemaIssue = null;
        this.invocationTrace = invocationTrace;
    }

    ToolCompatibilityProtocolIntegrityException(ToolCompatibilitySchemaIssue issue, String message) {
        super(message);
        this.schemaIssue = issue;
        this.invocationTrace = null;
    }

    ToolCompatibilityProtocolIntegrityException(ToolCompatibilitySchemaIssue issue, String message, Throwable cause) {
        super(message, cause);
        this.schemaIssue = issue;
        this.invocationTrace = null;
    }

    ToolCompatibilitySchemaIssue issue() {
        return schemaIssue;
    }

    ToolCompatibilityInvocationTrace invocationTrace() {
        return invocationTrace;
    }
}
