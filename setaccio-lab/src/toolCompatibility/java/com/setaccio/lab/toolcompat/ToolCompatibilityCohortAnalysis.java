package com.setaccio.lab.toolcompat;

import java.util.LinkedHashSet;
import java.util.List;

/** T3.4 per-model observations kept separate without an aggregate score or rank. */
record ToolCompatibilityCohortAnalysis(
        List<ModelAnalysis> models,
        List<String> observedArtifactRuntimeFormats,
        boolean artifactRuntimeFormatCoverageComplete,
        boolean mixedArtifactRuntimeFormats
) {

    ToolCompatibilityCohortAnalysis {
        models = List.copyOf(models == null ? List.of() : models);
        observedArtifactRuntimeFormats = List.copyOf(
                observedArtifactRuntimeFormats == null
                        ? List.of()
                        : observedArtifactRuntimeFormats);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("cohort analysis requires per-model observations");
        }
        LinkedHashSet<String> expectedFormats = new LinkedHashSet<>();
        boolean expectedCoverageComplete = true;
        for (ModelAnalysis model : models) {
            ToolCompatibilityMetadataField format =
                    model.modelIdentity().metadata().artifactRuntimeFormat();
            if (format.availability() == ToolCompatibilityMetadataField.Availability.AVAILABLE) {
                expectedFormats.add(format.value());
            } else {
                expectedCoverageComplete = false;
            }
        }
        if (!List.copyOf(expectedFormats).equals(observedArtifactRuntimeFormats)
                || expectedCoverageComplete != artifactRuntimeFormatCoverageComplete
                || mixedArtifactRuntimeFormats != (expectedFormats.size() > 1)) {
            throw new IllegalArgumentException(
                    "artifact/runtime format observations contradict model metadata");
        }
    }

    record ModelAnalysis(
            ToolCompatibilityCohortModelIdentity modelIdentity,
            ToolCompatibilityAnalysis compatibility,
            Discipline discipline,
            ArgumentDetails arguments,
            MultiStepBehavior multiStepBehavior,
            FailureRecovery failureRecovery,
            OutputBehavior outputBehavior,
            Efficiency efficiency
    ) {

        ModelAnalysis {
            if (modelIdentity == null
                    || compatibility == null
                    || discipline == null
                    || arguments == null
                    || multiStepBehavior == null
                    || failureRecovery == null
                    || outputBehavior == null
                    || efficiency == null) {
                throw new IllegalArgumentException("every cohort analysis dimension is required");
            }
            int plannedRows = compatibility.invocation().plannedRows();
            int observedCalls = compatibility.toolArguments().observedToolCalls();
            if (discipline.noMatchPlannedRows() != ToolCompatibilityProtocol.REPETITIONS
                    || multiStepBehavior.plannedRows() != ToolCompatibilityProtocol.REPETITIONS
                    || failureRecovery.plannedRows() != ToolCompatibilityProtocol.REPETITIONS
                    || outputBehavior.finalResponsesPresent()
                                    + outputBehavior.finalResponsesEmpty()
                            != plannedRows
                    || efficiency.passingRows()
                            != compatibility.completion().finalContractsPassed()
                    || arguments.missingRequiredArgumentCalls() > observedCalls
                    || arguments.unknownArgumentCalls() > observedCalls
                    || arguments.expectedValueMismatchCalls() > observedCalls
                    || failureRecovery.deterministicCallbackFailuresRetained()
                            != compatibility.toolExecution()
                                    .expectedDeterministicCallbackFailuresRetained()) {
                throw new IllegalArgumentException(
                        "cohort analysis dimensions contradict canonical protocol observations");
            }
        }
    }

    record Discipline(
            int noMatchPlannedRows,
            int noMatchRowsWithExpectedCallAndArguments,
            int noMatchResponsesRetained,
            int noMatchContractsPassed
    ) {

        Discipline {
            requireBounded(noMatchRowsWithExpectedCallAndArguments, noMatchPlannedRows,
                    "no-match call and argument observations");
            requireBounded(noMatchResponsesRetained, noMatchPlannedRows,
                    "no-match retained responses");
            requireBounded(noMatchContractsPassed, noMatchPlannedRows,
                    "no-match passing contracts");
        }
    }

    record ArgumentDetails(
            int missingRequiredArgumentCalls,
            int unknownArgumentCalls,
            int expectedValueMismatchCalls
    ) {

        ArgumentDetails {
            requireNonNegative(missingRequiredArgumentCalls, "missing required arguments");
            requireNonNegative(unknownArgumentCalls, "unknown arguments");
            requireNonNegative(expectedValueMismatchCalls, "expected-value mismatches");
        }
    }

    record MultiStepBehavior(
            int plannedRows,
            int firstExpectedCallCorrect,
            int secondExpectedCallCorrect,
            int dependencyOrderCorrect,
            int continuedAfterFirstCallback,
            int prematureFinalResponses,
            int rowsWithDuplicateCalls
    ) {

        MultiStepBehavior {
            requireBounded(firstExpectedCallCorrect, plannedRows, "correct first calls");
            requireBounded(secondExpectedCallCorrect, plannedRows, "correct second calls");
            requireBounded(dependencyOrderCorrect, plannedRows, "correct dependency order");
            requireBounded(continuedAfterFirstCallback, plannedRows, "post-callback continuations");
            requireBounded(prematureFinalResponses, plannedRows, "premature final responses");
            requireBounded(rowsWithDuplicateCalls, plannedRows, "multi-step duplicate calls");
        }
    }

    record FailureRecovery(
            int plannedRows,
            int deterministicCallbackFailuresRetained,
            int errorReportingMarkersPresent,
            int successClaimMarkersAfterFailure,
            int emptyFinalResponses
    ) {

        FailureRecovery {
            requireBounded(
                    deterministicCallbackFailuresRetained,
                    plannedRows,
                    "retained deterministic failures");
            requireBounded(errorReportingMarkersPresent, plannedRows, "error-reporting markers");
            requireBounded(successClaimMarkersAfterFailure, plannedRows, "success-claim markers");
            requireBounded(emptyFinalResponses, plannedRows, "empty failure-case responses");
        }
    }

    record OutputBehavior(
            int finalResponsesPresent,
            int finalResponsesEmpty,
            int rowsWithVisibleReasoningMarkers,
            int rowsReachingOutputLimit,
            int finalResponsesWithFormatPollutionMarkers,
            Double medianFinalResponseCharacters,
            Integer minimumFinalResponseCharacters,
            Integer maximumFinalResponseCharacters
    ) {

        OutputBehavior {
            requireNonNegative(finalResponsesPresent, "present final responses");
            requireNonNegative(finalResponsesEmpty, "empty final responses");
            requireBounded(
                    rowsWithVisibleReasoningMarkers,
                    finalResponsesPresent + finalResponsesEmpty,
                    "visible-reasoning rows");
            requireBounded(
                    rowsReachingOutputLimit,
                    finalResponsesPresent + finalResponsesEmpty,
                    "output-limit rows");
            requireBounded(
                    finalResponsesWithFormatPollutionMarkers,
                    finalResponsesPresent,
                    "format-polluted final responses");
            boolean absent = medianFinalResponseCharacters == null
                    && minimumFinalResponseCharacters == null
                    && maximumFinalResponseCharacters == null;
            boolean complete = medianFinalResponseCharacters != null
                    && minimumFinalResponseCharacters != null
                    && maximumFinalResponseCharacters != null;
            if ((!absent && !complete)
                    || (complete
                            && (!Double.isFinite(medianFinalResponseCharacters)
                                    || minimumFinalResponseCharacters < 0
                                    || maximumFinalResponseCharacters < minimumFinalResponseCharacters
                                    || medianFinalResponseCharacters < minimumFinalResponseCharacters
                                    || medianFinalResponseCharacters > maximumFinalResponseCharacters))) {
                throw new IllegalArgumentException("final-response length observations are inconsistent");
            }
            if (absent != (finalResponsesPresent == 0)) {
                throw new IllegalArgumentException(
                        "final-response length availability must track present responses");
            }
        }
    }

    record Efficiency(
            int passingRows,
            Double medianSuccessfulRowLatencyMillis,
            Long minimumSuccessfulRowLatencyMillis,
            Long maximumSuccessfulRowLatencyMillis,
            TokenObservation promptTokens,
            TokenObservation completionTokens,
            TokenObservation totalTokens,
            Double totalTokensPerPassingRow
    ) {

        Efficiency {
            requireNonNegative(passingRows, "passing rows");
            if (promptTokens == null || completionTokens == null || totalTokens == null) {
                throw new IllegalArgumentException("all token observations are required");
            }
            boolean latencyAbsent = medianSuccessfulRowLatencyMillis == null
                    && minimumSuccessfulRowLatencyMillis == null
                    && maximumSuccessfulRowLatencyMillis == null;
            boolean latencyComplete = medianSuccessfulRowLatencyMillis != null
                    && minimumSuccessfulRowLatencyMillis != null
                    && maximumSuccessfulRowLatencyMillis != null;
            if ((!latencyAbsent && !latencyComplete) || latencyAbsent != (passingRows == 0)) {
                throw new IllegalArgumentException(
                        "successful-row latency availability must track passing rows");
            }
            if (latencyComplete
                    && (!Double.isFinite(medianSuccessfulRowLatencyMillis)
                            || medianSuccessfulRowLatencyMillis < 0
                            || minimumSuccessfulRowLatencyMillis < 0
                            || maximumSuccessfulRowLatencyMillis
                                    < minimumSuccessfulRowLatencyMillis
                            || medianSuccessfulRowLatencyMillis
                                    < minimumSuccessfulRowLatencyMillis
                            || medianSuccessfulRowLatencyMillis
                                    > maximumSuccessfulRowLatencyMillis)) {
                throw new IllegalArgumentException(
                        "successful-row latency observations are inconsistent");
            }
            if (totalTokensPerPassingRow != null
                    && (!Double.isFinite(totalTokensPerPassingRow)
                            || totalTokensPerPassingRow < 0
                            || passingRows == 0)) {
                throw new IllegalArgumentException("tokens per passing row are inconsistent");
            }
        }
    }

    record TokenObservation(Long observedTotal, int observedProviderTurns, int providerTurns) {

        TokenObservation {
            requireNonNegative(observedProviderTurns, "provider turns with token values");
            requireNonNegative(providerTurns, "provider turns");
            if (observedProviderTurns > providerTurns
                    || (observedProviderTurns == 0) != (observedTotal == null)
                    || (observedTotal != null && observedTotal < 0)) {
                throw new IllegalArgumentException("token observation coverage is inconsistent");
            }
        }

        boolean complete() {
            return providerTurns > 0 && observedProviderTurns == providerTurns;
        }
    }

    private static void requireBounded(int value, int upperBound, String field) {
        requireNonNegative(upperBound, field + " upper bound");
        if (value < 0 || value > upperBound) {
            throw new IllegalArgumentException(field + " must be within its planned rows");
        }
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
