package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Evaluates the narrowly scoped, provider-free T3.6 capability frontier. */
final class ToolCompatibilityCohortFrontier {

    private final ToolCompatibilityCohortEvidence evidence;
    private final ToolCompatibilityCohortFrontierReport report;

    ToolCompatibilityCohortFrontier(ObjectMapper objectMapper) {
        evidence = new ToolCompatibilityCohortEvidence(objectMapper);
        report = new ToolCompatibilityCohortFrontierReport();
    }

    FrontierResult analyze(Path runDirectory) {
        ToolCompatibilityCohortEvidence.VerifiedCohort verified =
                evidence.requireVerified(runDirectory, "cohort run");
        return analyze(
                verified.manifest().runId(),
                verified.manifest().codeBaseline(),
                verified.result());
    }

    FrontierResult analyze(
            String runId,
            EvidenceCodeBaseline codeBaseline,
            ToolCompatibilityCohortResult result
    ) {
        if (runId == null || runId.isBlank() || !runId.equals(runId.strip())) {
            throw new IllegalArgumentException("cohort frontier run ID must be nonblank and trimmed");
        }
        if (codeBaseline == null || result == null) {
            throw new IllegalArgumentException(
                    "cohort frontier code baseline and result are required");
        }

        List<ModelObservation> models = result.modelRuns().stream()
                .map(ToolCompatibilityCohortFrontier::observe)
                .toList();
        Measurement measurement = measure(models);
        FrontierData data = new FrontierData(
                runId,
                codeBaseline,
                result.ollamaRuntimeVersion(),
                result.runSettings(),
                result.orderedCaseIds(),
                models,
                measurement);
        return new FrontierResult(data, report.render(data));
    }

    private static ModelObservation observe(ToolCompatibilityCohortModelRun run) {
        ToolCompatibilityCohortModelIdentity identity = run.modelIdentity();
        List<ToolCompatibilityRow> rows = run.rows();
        int passedRows = Math.toIntExact(rows.stream()
                .filter(ToolCompatibilityRow::caseContractPassed)
                .count());
        return new ModelObservation(
                identity,
                parseSizeBytes(identity.metadata().sizeBytes()),
                passedRows,
                rows.size(),
                passedRows == rows.size());
    }

    private static Long parseSizeBytes(ToolCompatibilityMetadataField field) {
        if (field.availability() != ToolCompatibilityMetadataField.Availability.AVAILABLE) {
            return null;
        }
        try {
            long value = Long.parseLong(field.value());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Measurement measure(List<ModelObservation> models) {
        if (models.isEmpty()) {
            return Measurement.notMeasurable("the verified cohort contains no model runs");
        }
        List<ModelObservation> qualifying = models.stream()
                .filter(ModelObservation::passedEveryLockedRow)
                .toList();
        if (qualifying.isEmpty()) {
            return Measurement.notMeasurable(
                    "no tested installed model passed every locked row");
        }
        List<String> missingSizes = qualifying.stream()
                .filter(model -> model.sizeBytes() == null)
                .map(model -> model.identity().effectiveInstalledTag())
                .toList();
        if (!missingSizes.isEmpty()) {
            return Measurement.notMeasurable(
                    "recorded installed-artifact size is unavailable or invalid for qualifying "
                            + "model(s): " + String.join(", ", missingSizes));
        }

        List<ModelObservation> ordered = new ArrayList<>(qualifying);
        ordered.sort(Comparator.comparingLong(ModelObservation::sizeBytes));
        ModelObservation smallest = ordered.getFirst();
        long sameMinimum = ordered.stream()
                .filter(model -> model.sizeBytes().equals(smallest.sizeBytes()))
                .count();
        if (sameMinimum != 1) {
            return Measurement.notMeasurable(
                    "multiple qualifying installed artifacts share the smallest recorded size");
        }
        return Measurement.measurable(smallest, qualifying.size());
    }

    record FrontierResult(FrontierData data, String report) {

        FrontierResult {
            if (data == null || report == null || report.isBlank()) {
                throw new IllegalArgumentException(
                        "cohort frontier data and nonblank report are required");
            }
        }
    }

    record FrontierData(
            String runId,
            EvidenceCodeBaseline codeBaseline,
            String ollamaRuntimeVersion,
            ToolCompatibilityCohortRunSettings runSettings,
            List<String> orderedCaseIds,
            List<ModelObservation> models,
            Measurement measurement
    ) {

        FrontierData {
            if (runId == null
                    || codeBaseline == null
                    || ollamaRuntimeVersion == null
                    || runSettings == null
                    || measurement == null) {
                throw new IllegalArgumentException("cohort frontier identity is incomplete");
            }
            orderedCaseIds = List.copyOf(orderedCaseIds == null ? List.of() : orderedCaseIds);
            models = List.copyOf(models == null ? List.of() : models);
            if (orderedCaseIds.isEmpty() || models.isEmpty()) {
                throw new IllegalArgumentException(
                        "cohort frontier requires locked cases and model observations");
            }
            int expectedRows = Math.multiplyExact(
                    orderedCaseIds.size(), runSettings.repetitions());
            if (models.stream().anyMatch(model -> model.totalRows() != expectedRows)) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "T3.6 frontier requires every planned row for every model");
            }
        }
    }

    record ModelObservation(
            ToolCompatibilityCohortModelIdentity identity,
            Long sizeBytes,
            int passedRows,
            int totalRows,
            boolean passedEveryLockedRow
    ) {

        ModelObservation {
            if (identity == null
                    || (sizeBytes != null && sizeBytes < 1)
                    || passedRows < 0
                    || totalRows < 1
                    || passedRows > totalRows
                    || passedEveryLockedRow != (passedRows == totalRows)) {
                throw new IllegalArgumentException("cohort frontier model observation is invalid");
            }
        }
    }

    record Measurement(
            Status status,
            ModelObservation frontier,
            int qualifyingModels,
            String reason
    ) {

        Measurement {
            if (status == null || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("cohort frontier measurement is incomplete");
            }
            if (status == Status.MEASURABLE) {
                if (frontier == null || qualifyingModels < 1) {
                    throw new IllegalArgumentException(
                            "measurable frontier requires one selected observation");
                }
            } else if (frontier != null || qualifyingModels != 0) {
                throw new IllegalArgumentException(
                        "not-measurable frontier must not select a model");
            }
        }

        static Measurement measurable(ModelObservation frontier, int qualifyingModels) {
            return new Measurement(
                    Status.MEASURABLE,
                    frontier,
                    qualifyingModels,
                    "one qualifying installed artifact has the unique smallest recorded size");
        }

        static Measurement notMeasurable(String reason) {
            return new Measurement(Status.NOT_MEASURABLE, null, 0, reason);
        }
    }

    enum Status {
        MEASURABLE,
        NOT_MEASURABLE
    }
}
