package com.setaccio.lab.toolcompat;

import java.util.List;

/** Typed holder for a deterministic diagnostic category. */
record ToolCompatibilityDiagnostic(String category) {

    static final String ROW_TIMEOUT = "ROW_TIMEOUT";
    static final String PROVIDER_FAILURE = "PROVIDER_FAILURE";
    static final String MALFORMED_JSON = "MALFORMED_JSON";
    static final String SCHEMA_TYPE_MISMATCH = "SCHEMA_TYPE_MISMATCH";
    static final String MISSING_REQUIRED_ARGUMENT = "MISSING_REQUIRED_ARGUMENT";
    static final String UNKNOWN_ARGUMENT = "UNKNOWN_ARGUMENT";
    static final String CALLBACK_RESOLUTION_FAILURE = "CALLBACK_RESOLUTION_FAILURE";
    static final String CALLBACK_BINDING_FAILURE = "CALLBACK_BINDING_FAILURE";
    static final String CALLBACK_INVOCATION_FAILURE = "CALLBACK_INVOCATION_FAILURE";
    static final String CALLBACK_FAILURE = "CALLBACK_FAILURE";
    static final String EXPECTED_CALL_SEQUENCE_MISMATCH = "EXPECTED_CALL_SEQUENCE_MISMATCH";
    static final String EXPECTED_ARGUMENT_MISMATCH = "EXPECTED_ARGUMENT_MISMATCH";
    static final String FINAL_RESPONSE_EMPTY = "FINAL_RESPONSE_EMPTY";
    static final String EXPECTED_TOOL_RESPONSE_MISMATCH = "EXPECTED_TOOL_RESPONSE_MISMATCH";
    static final String FINAL_RESPONSE_CONTRACT_MISMATCH = "FINAL_RESPONSE_CONTRACT_MISMATCH";
    static final String VISIBLE_REASONING_TEXT = "VISIBLE_REASONING_TEXT";

    private static final List<String> CATEGORIES = List.of(
            ROW_TIMEOUT,
            PROVIDER_FAILURE,
            MALFORMED_JSON,
            SCHEMA_TYPE_MISMATCH,
            MISSING_REQUIRED_ARGUMENT,
            UNKNOWN_ARGUMENT,
            CALLBACK_RESOLUTION_FAILURE,
            CALLBACK_BINDING_FAILURE,
            CALLBACK_INVOCATION_FAILURE,
            CALLBACK_FAILURE,
            EXPECTED_CALL_SEQUENCE_MISMATCH,
            EXPECTED_ARGUMENT_MISMATCH,
            FINAL_RESPONSE_EMPTY,
            EXPECTED_TOOL_RESPONSE_MISMATCH,
            FINAL_RESPONSE_CONTRACT_MISMATCH,
            VISIBLE_REASONING_TEXT);

    ToolCompatibilityDiagnostic {
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("unknown tool compatibility diagnostic category: " + category);
        }
    }

    static List<String> categories() {
        return CATEGORIES;
    }
}
