package com.setaccio.lab.toolcompat;

import com.setaccio.lab.model.ToolBenchmarkAssertion;
import java.util.List;

/** Assigns one primary deterministic category without collapsing its source evidence. */
final class ToolCompatibilityDiagnosticClassifier {

    String classify(ClassificationInput input) {
        if (input.caseContractPassed()) {
            return input.visibleReasoning().markerDetectedAnywhere()
                    ? ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT
                    : null;
        }

        if (ToolCompatibilityFailure.ROW_TIMEOUT.equals(input.failureCategory())) {
            return ToolCompatibilityDiagnostic.ROW_TIMEOUT;
        }
        if (ToolCompatibilityFailure.PROVIDER_FAILURE.equals(input.failureCategory())) {
            return ToolCompatibilityDiagnostic.PROVIDER_FAILURE;
        }

        for (ToolCompatibilityToolCallEvidence call : input.toolCalls()) {
            if (call.rawArgumentJsonState() == ToolCompatibilityEvidenceState.FAILED) {
                return ToolCompatibilityDiagnostic.MALFORMED_JSON;
            }
        }
        for (ToolCompatibilityToolCallEvidence call : input.toolCalls()) {
            if (call.declaredSchemaState() == ToolCompatibilityEvidenceState.FAILED) {
                return schemaCategory(call.rawArgumentIssue());
            }
        }

        String callbackCategory = callbackCategory(input);
        if (callbackCategory != null) {
            return callbackCategory;
        }
        if (!input.exactCallSequenceMatched()) {
            return ToolCompatibilityDiagnostic.EXPECTED_CALL_SEQUENCE_MISMATCH;
        }
        if (input.toolCalls().stream().anyMatch(call ->
                call.expectedArgumentsState() == ToolCompatibilityEvidenceState.FAILED)) {
            return ToolCompatibilityDiagnostic.EXPECTED_ARGUMENT_MISMATCH;
        }
        if (!input.finalResponsePresent()) {
            return ToolCompatibilityDiagnostic.FINAL_RESPONSE_EMPTY;
        }
        if (failedAssertion(input.assertions(), "tool_response_contains")) {
            return ToolCompatibilityDiagnostic.EXPECTED_TOOL_RESPONSE_MISMATCH;
        }
        if (failedAssertion(input.assertions(), "output_contains")) {
            return ToolCompatibilityDiagnostic.FINAL_RESPONSE_CONTRACT_MISMATCH;
        }
        throw new ToolCompatibilityProtocolIntegrityException(
                "Failed tool compatibility contract has no deterministic primary diagnostic");
    }

    private static String schemaCategory(ToolCompatibilitySchemaIssue issue) {
        if (issue == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Failed raw-argument schema evidence has no specific issue");
        }
        return switch (issue) {
            case SCHEMA_TYPE_MISMATCH -> ToolCompatibilityDiagnostic.SCHEMA_TYPE_MISMATCH;
            case MISSING_REQUIRED_ARGUMENT -> ToolCompatibilityDiagnostic.MISSING_REQUIRED_ARGUMENT;
            case UNKNOWN_ARGUMENT -> ToolCompatibilityDiagnostic.UNKNOWN_ARGUMENT;
            case MALFORMED_JSON -> throw new ToolCompatibilityProtocolIntegrityException(
                    "Malformed JSON cannot be classified as a declared-schema mismatch");
            case UNSUPPORTED_SCHEMA -> throw new ToolCompatibilityProtocolIntegrityException(
                    "Unsupported declared schema is an experimental integrity failure");
        };
    }

    private static String callbackCategory(ClassificationInput input) {
        for (ToolCompatibilityToolResponseEvidence response : input.toolResponses()) {
            if (response.failure() != null) {
                if (expectedDeterministicFailure(input, response)) {
                    continue;
                }
                return switch (response.failure().category()) {
                    case ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE ->
                            ToolCompatibilityDiagnostic.CALLBACK_RESOLUTION_FAILURE;
                    case ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE ->
                            ToolCompatibilityDiagnostic.CALLBACK_BINDING_FAILURE;
                    case ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE ->
                            ToolCompatibilityDiagnostic.CALLBACK_INVOCATION_FAILURE;
                    case ToolCompatibilityFailure.CALLBACK_FAILURE ->
                            ToolCompatibilityDiagnostic.CALLBACK_FAILURE;
                    default -> throw new ToolCompatibilityProtocolIntegrityException(
                            "Tool response contains a non-callback diagnostic category");
                };
            }
        }
        return switch (input.failureCategory()) {
            case ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE ->
                    ToolCompatibilityDiagnostic.CALLBACK_RESOLUTION_FAILURE;
            case ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE ->
                    ToolCompatibilityDiagnostic.CALLBACK_BINDING_FAILURE;
            case ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE ->
                    ToolCompatibilityDiagnostic.CALLBACK_INVOCATION_FAILURE;
            case ToolCompatibilityFailure.CALLBACK_FAILURE ->
                    ToolCompatibilityDiagnostic.CALLBACK_FAILURE;
            case null -> null;
            default -> throw new ToolCompatibilityProtocolIntegrityException(
                    "Unsupported row failure category reached diagnostic classification");
        };
    }

    private static boolean expectedDeterministicFailure(
            ClassificationInput input,
            ToolCompatibilityToolResponseEvidence response
    ) {
        if (input.failureCategory() != null
                || !"deterministic-tool-failure".equals(input.caseId())
                || response.failure() == null
                || !ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE.equals(
                        response.failure().category())
                || response.responseData() == null
                || response.responseData().isBlank()) {
            return false;
        }
        return input.toolCalls().stream().anyMatch(call ->
                call.sequence() == response.toolCallSequence()
                        && "lab_fail_fixture".equals(call.toolName())
                        && call.expectedCallAtSequenceState() == ToolCompatibilityEvidenceState.SUCCEEDED
                        && call.expectedArgumentsState() == ToolCompatibilityEvidenceState.SUCCEEDED);
    }

    private static boolean failedAssertion(List<ToolBenchmarkAssertion> assertions, String check) {
        return assertions.stream().anyMatch(assertion ->
                check.equals(assertion.check()) && !assertion.passed());
    }

    record ClassificationInput(
            String caseId,
            boolean caseContractPassed,
            boolean exactCallSequenceMatched,
            boolean finalResponsePresent,
            String failureCategory,
            List<ToolCompatibilityToolCallEvidence> toolCalls,
            List<ToolCompatibilityToolResponseEvidence> toolResponses,
            List<ToolBenchmarkAssertion> assertions,
            ToolCompatibilityVisibleReasoningEvidence visibleReasoning
    ) {

        ClassificationInput {
            if (caseId == null || caseId.isBlank() || !caseId.equals(caseId.strip())) {
                throw new IllegalArgumentException("caseId must be nonblank and trimmed");
            }
            toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
            toolResponses = List.copyOf(toolResponses == null ? List.of() : toolResponses);
            assertions = List.copyOf(assertions == null ? List.of() : assertions);
            if (visibleReasoning == null) {
                throw new IllegalArgumentException("visibleReasoning must not be null");
            }
        }
    }
}
