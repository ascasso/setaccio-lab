package com.setaccio.lab.toolcompat;

import com.setaccio.lab.model.ToolBenchmarkAssertion;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityDiagnosticClassifierTest {

    private final ToolCompatibilityDiagnosticClassifier classifier =
            new ToolCompatibilityDiagnosticClassifier();

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCategory")
    void assignsEveryDeterministicCategory(
            String expected,
            ToolCompatibilityDiagnosticClassifier.ClassificationInput input
    ) {
        assertThat(classifier.classify(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("precedenceEdges")
    void appliesTheLockedPrimaryCategoryPrecedence(
            String description,
            String expected,
            ToolCompatibilityDiagnosticClassifier.ClassificationInput input
    ) {
        assertThat(classifier.classify(input)).as(description).isEqualTo(expected);
    }

    @Test
    void leavesACleanPassingContractUnclassified() {
        assertThat(classifier.classify(input(
                true, true, true, null, List.of(), List.of(), List.of(), none())))
                .isNull();
    }

    @Test
    void treatsAnUnclassifiedFailedContractAsIntegrityFailure() {
        assertThatThrownBy(() -> classifier.classify(input(
                false, true, true, null, List.of(), List.of(), List.of(), none())))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("no deterministic primary diagnostic");
    }

    @Test
    void rejectsACatchAllDiagnosticCategory() {
        assertThatThrownBy(() -> new ToolCompatibilityDiagnostic("OTHER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    private static Stream<Arguments> everyCategory() {
        return Stream.of(
                Arguments.of(ToolCompatibilityDiagnostic.ROW_TIMEOUT,
                        input(false, true, false, ToolCompatibilityFailure.ROW_TIMEOUT,
                                List.of(), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.PROVIDER_FAILURE,
                        input(false, true, false, ToolCompatibilityFailure.PROVIDER_FAILURE,
                                List.of(), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.MALFORMED_JSON,
                        input(false, true, true, null,
                                List.of(malformedCall()), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.SCHEMA_TYPE_MISMATCH,
                        schemaInput(ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH)),
                Arguments.of(ToolCompatibilityDiagnostic.MISSING_REQUIRED_ARGUMENT,
                        schemaInput(ToolCompatibilitySchemaIssue.MISSING_REQUIRED_ARGUMENT)),
                Arguments.of(ToolCompatibilityDiagnostic.UNKNOWN_ARGUMENT,
                        schemaInput(ToolCompatibilitySchemaIssue.UNKNOWN_ARGUMENT)),
                Arguments.of(ToolCompatibilityDiagnostic.CALLBACK_RESOLUTION_FAILURE,
                        callbackInput(ToolCompatibilityFailure.CALLBACK_RESOLUTION_FAILURE)),
                Arguments.of(ToolCompatibilityDiagnostic.CALLBACK_BINDING_FAILURE,
                        callbackInput(ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE)),
                Arguments.of(ToolCompatibilityDiagnostic.CALLBACK_INVOCATION_FAILURE,
                        callbackInput(ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE)),
                Arguments.of(ToolCompatibilityDiagnostic.CALLBACK_FAILURE,
                        callbackInput(ToolCompatibilityFailure.CALLBACK_FAILURE)),
                Arguments.of(ToolCompatibilityDiagnostic.EXPECTED_CALL_SEQUENCE_MISMATCH,
                        input(false, false, true, null,
                                List.of(), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.EXPECTED_ARGUMENT_MISMATCH,
                        input(false, true, true, null,
                                List.of(expectedArgumentMismatchCall()), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.FINAL_RESPONSE_EMPTY,
                        input(false, true, false, null,
                                List.of(), List.of(), List.of(), none())),
                Arguments.of(ToolCompatibilityDiagnostic.EXPECTED_TOOL_RESPONSE_MISMATCH,
                        input(false, true, true, null,
                                List.of(), List.of(), List.of(failed("tool_response_contains")), none())),
                Arguments.of(ToolCompatibilityDiagnostic.FINAL_RESPONSE_CONTRACT_MISMATCH,
                        input(false, true, true, null,
                                List.of(), List.of(), List.of(failed("output_contains")), none())),
                Arguments.of(ToolCompatibilityDiagnostic.VISIBLE_REASONING_TEXT,
                        input(true, true, true, null,
                                List.of(), List.of(), List.of(), thinkTag())));
    }

    private static Stream<Arguments> precedenceEdges() {
        return Stream.of(
                Arguments.of("timeout before malformed JSON",
                        ToolCompatibilityDiagnostic.ROW_TIMEOUT,
                        input(false, false, false, ToolCompatibilityFailure.ROW_TIMEOUT,
                                List.of(malformedCall()), List.of(), List.of(), thinkTag())),
                Arguments.of("provider failure before schema mismatch",
                        ToolCompatibilityDiagnostic.PROVIDER_FAILURE,
                        input(false, false, false, ToolCompatibilityFailure.PROVIDER_FAILURE,
                                List.of(schemaMismatchCall(ToolCompatibilitySchemaIssue.UNKNOWN_ARGUMENT)),
                                List.of(), List.of(), none())),
                Arguments.of("malformed JSON before schema and callback evidence",
                        ToolCompatibilityDiagnostic.MALFORMED_JSON,
                        input(false, false, true, null,
                                List.of(
                                        malformedCall(),
                                        schemaMismatchCall(ToolCompatibilitySchemaIssue.SCHEMA_TYPE_MISMATCH)),
                                List.of(failedResponse(ToolCompatibilityFailure.CALLBACK_BINDING_FAILURE)),
                                List.of(), none())),
                Arguments.of("schema mismatch before callback failure",
                        ToolCompatibilityDiagnostic.MISSING_REQUIRED_ARGUMENT,
                        input(false, false, true, null,
                                List.of(schemaMismatchCall(
                                        ToolCompatibilitySchemaIssue.MISSING_REQUIRED_ARGUMENT)),
                                List.of(failedResponse(ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE)),
                                List.of(), none())),
                Arguments.of("callback failure before call-sequence mismatch",
                        ToolCompatibilityDiagnostic.CALLBACK_INVOCATION_FAILURE,
                        input(false, false, true, null,
                                List.of(),
                                List.of(failedResponse(
                                        ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE)),
                                List.of(), none())),
                Arguments.of("call-sequence mismatch before argument mismatch",
                        ToolCompatibilityDiagnostic.EXPECTED_CALL_SEQUENCE_MISMATCH,
                        input(false, false, true, null,
                                List.of(expectedArgumentMismatchCall()), List.of(), List.of(), none())),
                Arguments.of("argument mismatch before empty final response",
                        ToolCompatibilityDiagnostic.EXPECTED_ARGUMENT_MISMATCH,
                        input(false, true, false, null,
                                List.of(expectedArgumentMismatchCall()), List.of(), List.of(), none())),
                Arguments.of("empty final response before final assertions",
                        ToolCompatibilityDiagnostic.FINAL_RESPONSE_EMPTY,
                        input(false, true, false, null,
                                List.of(), List.of(),
                                List.of(failed("tool_response_contains"), failed("output_contains")), none())),
                Arguments.of("tool-response contract before final-output contract",
                        ToolCompatibilityDiagnostic.EXPECTED_TOOL_RESPONSE_MISMATCH,
                        input(false, true, true, null,
                                List.of(), List.of(),
                                List.of(failed("tool_response_contains"), failed("output_contains")), none())),
                Arguments.of("failed contract category before visible reasoning observation",
                        ToolCompatibilityDiagnostic.PROVIDER_FAILURE,
                        input(false, true, false, ToolCompatibilityFailure.PROVIDER_FAILURE,
                                List.of(), List.of(), List.of(), thinkTag())),
                Arguments.of("expected deterministic callback failure does not mask sequence mismatch",
                        ToolCompatibilityDiagnostic.EXPECTED_CALL_SEQUENCE_MISMATCH,
                        inputForCase(
                                "deterministic-tool-failure",
                                false,
                                false,
                                true,
                                null,
                                List.of(expectedFailureCall()),
                                List.of(expectedFailureResponse()),
                                List.of(),
                                none())));
    }

    private static ToolCompatibilityDiagnosticClassifier.ClassificationInput schemaInput(
            ToolCompatibilitySchemaIssue issue
    ) {
        return input(false, true, true, null,
                List.of(schemaMismatchCall(issue)), List.of(), List.of(), none());
    }

    private static ToolCompatibilityDiagnosticClassifier.ClassificationInput callbackInput(
            String category
    ) {
        return input(false, true, true, null,
                List.of(), List.of(failedResponse(category)), List.of(), none());
    }

    private static ToolCompatibilityDiagnosticClassifier.ClassificationInput input(
            boolean contractPassed,
            boolean exactSequence,
            boolean finalResponsePresent,
            String failureCategory,
            List<ToolCompatibilityToolCallEvidence> calls,
            List<ToolCompatibilityToolResponseEvidence> responses,
            List<ToolBenchmarkAssertion> assertions,
            ToolCompatibilityVisibleReasoningEvidence visibleReasoning
    ) {
        return inputForCase(
                "arithmetic-add",
                contractPassed,
                exactSequence,
                finalResponsePresent,
                failureCategory,
                calls,
                responses,
                assertions,
                visibleReasoning);
    }

    private static ToolCompatibilityDiagnosticClassifier.ClassificationInput inputForCase(
            String caseId,
            boolean contractPassed,
            boolean exactSequence,
            boolean finalResponsePresent,
            String failureCategory,
            List<ToolCompatibilityToolCallEvidence> calls,
            List<ToolCompatibilityToolResponseEvidence> responses,
            List<ToolBenchmarkAssertion> assertions,
            ToolCompatibilityVisibleReasoningEvidence visibleReasoning
    ) {
        return new ToolCompatibilityDiagnosticClassifier.ClassificationInput(
                caseId,
                contractPassed,
                exactSequence,
                finalResponsePresent,
                failureCategory,
                calls,
                responses,
                assertions,
                visibleReasoning);
    }

    private static ToolCompatibilityToolCallEvidence expectedFailureCall() {
        return new ToolCompatibilityToolCallEvidence(
                1,
                1,
                "call-expected-failure",
                "function",
                "lab_fail_fixture",
                "{}",
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                null,
                1,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.FAILED,
                1);
    }

    private static ToolCompatibilityToolResponseEvidence expectedFailureResponse() {
        return new ToolCompatibilityToolResponseEvidence(
                1,
                1,
                "call-expected-failure",
                "lab_fail_fixture",
                ToolCompatibilityEvidenceState.FAILED,
                "fixture-tool-failure",
                ToolCompatibilityFailure.of(ToolCompatibilityFailure.CALLBACK_INVOCATION_FAILURE));
    }

    private static ToolCompatibilityToolCallEvidence malformedCall() {
        return new ToolCompatibilityToolCallEvidence(
                1,
                1,
                "call-malformed",
                "function",
                "lab_add_numbers",
                "{",
                ToolCompatibilityEvidenceState.FAILED,
                ToolCompatibilityEvidenceState.NOT_REACHED,
                ToolCompatibilitySchemaIssue.MALFORMED_JSON,
                1,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.NOT_REACHED,
                ToolCompatibilityEvidenceState.NOT_REACHED,
                ToolCompatibilityEvidenceState.NOT_REACHED,
                null);
    }

    private static ToolCompatibilityToolCallEvidence schemaMismatchCall(
            ToolCompatibilitySchemaIssue issue
    ) {
        return new ToolCompatibilityToolCallEvidence(
                1,
                1,
                "call-schema",
                "function",
                "lab_add_numbers",
                "{}",
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.FAILED,
                issue,
                1,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.FAILED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                1);
    }

    private static ToolCompatibilityToolCallEvidence expectedArgumentMismatchCall() {
        return new ToolCompatibilityToolCallEvidence(
                1,
                1,
                "call-arguments",
                "function",
                "lab_add_numbers",
                "{}",
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                null,
                1,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.FAILED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                ToolCompatibilityEvidenceState.SUCCEEDED,
                1);
    }

    private static ToolCompatibilityToolResponseEvidence failedResponse(String category) {
        return new ToolCompatibilityToolResponseEvidence(
                1,
                1,
                "call-response",
                "lab_add_numbers",
                ToolCompatibilityEvidenceState.FAILED,
                null,
                ToolCompatibilityFailure.of(category));
    }

    private static ToolBenchmarkAssertion failed(String check) {
        return new ToolBenchmarkAssertion(check, "fixture", false, "failed fixture assertion");
    }

    private static ToolCompatibilityVisibleReasoningEvidence none() {
        return new ToolCompatibilityVisibleReasoningEvidence(false, false, false, false, false, false);
    }

    private static ToolCompatibilityVisibleReasoningEvidence thinkTag() {
        return new ToolCompatibilityVisibleReasoningEvidence(true, true, false, false, false, true);
    }
}
