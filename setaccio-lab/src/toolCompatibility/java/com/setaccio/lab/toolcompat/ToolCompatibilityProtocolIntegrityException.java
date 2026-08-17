package com.setaccio.lab.toolcompat;

final class ToolCompatibilityProtocolIntegrityException extends IllegalStateException {

    private final ToolCompatibilitySchemaIssue schemaIssue;

    ToolCompatibilityProtocolIntegrityException(String message) {
        super(message);
        this.schemaIssue = null;
    }

    ToolCompatibilityProtocolIntegrityException(ToolCompatibilitySchemaIssue issue, String message) {
        super(message);
        this.schemaIssue = issue;
    }

    ToolCompatibilityProtocolIntegrityException(ToolCompatibilitySchemaIssue issue, String message, Throwable cause) {
        super(message, cause);
        this.schemaIssue = issue;
    }

    ToolCompatibilitySchemaIssue issue() {
        return schemaIssue;
    }
}
