package com.setaccio.lab.vision;

import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.model.VisionStructuralCheck;
import com.setaccio.lab.service.VisionPromptDefinition;
import com.setaccio.lab.util.ImageMimeTypes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class VisionMatrixAnalyzer {

    private final VisionPromptDefinition promptDefinition;

    VisionMatrixAnalyzer(VisionPromptDefinition promptDefinition) {
        if (promptDefinition == null) {
            throw new IllegalArgumentException("promptDefinition must not be null");
        }
        VisionMatrixProtocol.requirePrompt(promptDefinition);
        this.promptDefinition = promptDefinition;
    }

    MatrixAnalysis analyze(VisionMatrixResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Vision matrix result must not be null");
        }
        List<String> failures = new ArrayList<>();
        validateTopLevel(result, failures);

        Map<GroupKey, MutableGroup> groups = new LinkedHashMap<>();
        List<VisionMatrixInput> inputs = safe(result.inputs());
        VisionMatrixRunSettings settings = result.runSettings();
        List<VisionMatrixRow> rows = safe(result.rows());
        if (settings != null) {
            int rowIndex = 0;
            for (String model : settings.models()) {
                for (VisionMatrixInput input : inputs) {
                    if (input == null) {
                        continue;
                    }
                    for (int repetition = 1; repetition <= settings.repetitions(); repetition++) {
                        if (rowIndex >= rows.size()) {
                            break;
                        }
                        VisionMatrixRow row = rows.get(rowIndex);
                        validateRow(
                                row,
                                rowIndex + 1,
                                model,
                                input,
                                repetition,
                                settings,
                                failures);
                        if (row != null) {
                            groups.computeIfAbsent(
                                            new GroupKey(model, input.caseId()),
                                            ignored -> new MutableGroup())
                                    .add(row);
                        }
                        rowIndex++;
                    }
                }
            }
        }

        Map<GroupKey, GroupAnalysis> completed = new LinkedHashMap<>();
        groups.forEach((key, group) -> completed.put(key, group.complete()));
        return new MatrixAnalysis(
                Map.copyOf(completed),
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateTopLevel(VisionMatrixResult result, List<String> failures) {
        if (!VisionMatrixProtocol.SUITE.equals(result.suite())) {
            failures.add("Raw vision matrix suite is not vision-matrix.");
        }
        if (!VisionMatrixProtocol.PROVIDER.equals(result.provider())) {
            failures.add("Raw vision matrix provider is not ollama.");
        }
        if (!VisionMatrixProtocol.HOST.equals(result.host())) {
            failures.add("Raw vision matrix host is not the neutral local value.");
        }
        if (result.startedAt() == null || result.finishedAt() == null
                || result.finishedAt().isBefore(result.startedAt())) {
            failures.add("Raw vision matrix timestamps are missing or invalid.");
        }
        if (!VisionMatrixProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
            failures.add("Raw vision matrix execution strategy is not sequential.");
        }
        if (!VisionMatrixProtocol.PULL_MODEL_STRATEGY.equals(result.pullModelStrategy())) {
            failures.add("Raw vision matrix pull strategy is not never.");
        }
        if (!promptDefinition.id().equals(result.promptId())
                || !promptDefinition.version().equals(result.promptVersion())
                || !promptDefinition.sha256().equals(result.promptSha256())) {
            failures.add("Raw vision matrix prompt identity drifted from the tracked contract.");
        }

        VisionMatrixRunSettings settings = result.runSettings();
        if (settings == null
                || settings.repetitions() != VisionMatrixProtocol.REPETITIONS
                || settings.temperature() != VisionMatrixProtocol.TEMPERATURE
                || settings.baseSeed() != VisionMatrixProtocol.BASE_SEED) {
            failures.add("Raw vision matrix settings drifted from the locked protocol.");
        }
        validateInputs(result.inputs(), failures);
        if (settings != null) {
            int expectedRows = settings.models().size()
                    * safe(result.inputs()).size()
                    * settings.repetitions();
            if (safe(result.rows()).size() != expectedRows) {
                failures.add("Raw vision matrix expected " + expectedRows + " rows but found "
                        + safe(result.rows()).size() + ".");
            }
        }
    }

    private void validateInputs(List<VisionMatrixInput> inputs, List<String> failures) {
        if (inputs == null || inputs.isEmpty()) {
            failures.add("Raw vision matrix contains no input identities.");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (VisionMatrixInput input : inputs) {
            if (input == null
                    || input.caseId() == null
                    || !input.caseId().matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || input.mimeType() == null
                    || !ImageMimeTypes.isSupported(input.mimeType())
                    || "image/jpg".equals(input.mimeType())
                    || input.blake3() == null
                    || !input.blake3().matches("[0-9a-f]{64}")) {
                failures.add("Raw vision matrix input identity is malformed.");
                continue;
            }
            if (!ids.add(input.caseId())) {
                failures.add("Raw vision matrix contains duplicate case ID " + input.caseId() + ".");
            }
        }
    }

    private void validateRow(
            VisionMatrixRow row,
            int expectedSequence,
            String expectedModel,
            VisionMatrixInput expectedInput,
            int expectedRepetition,
            VisionMatrixRunSettings settings,
            List<String> failures) {
        String key = expectedModel + "/" + expectedInput.caseId() + "/" + expectedRepetition;
        if (row == null) {
            failures.add("Raw vision matrix row is null at sequence " + expectedSequence + ".");
            return;
        }
        if (row.sequence() != expectedSequence
                || !expectedModel.equals(row.model())
                || !expectedInput.caseId().equals(row.caseId())
                || row.repetition() != expectedRepetition) {
            failures.add("Raw vision matrix row order or identity drifted at " + key + ".");
        }

        VisionInvocationSettings expectedSettings = new VisionInvocationSettings(
                expectedModel,
                settings.temperature(),
                settings.seedFor(expectedRepetition),
                settings.maxTokens());
        if (!expectedSettings.equals(row.invocationSettings())) {
            failures.add("Raw vision matrix invocation settings drifted at " + key + ".");
        }
        if (!expectedInput.mimeType().equals(row.mimeType())
                || !expectedInput.blake3().equals(row.inputBlake3())) {
            failures.add("Raw vision matrix input metadata drifted at " + key + ".");
        }
        if (!promptDefinition.id().equals(row.promptId())
                || !promptDefinition.version().equals(row.promptVersion())
                || !promptDefinition.sha256().equals(row.promptSha256())) {
            failures.add("Raw vision matrix row prompt identity drifted at " + key + ".");
        }
        if (row.latencyMs() < 0 || negative(row.tokensIn()) || negative(row.tokensOut())) {
            failures.add("Raw vision matrix latency or token metadata is invalid at " + key + ".");
        }

        if (row.invocationSuccess()) {
            if (row.outputText() == null || row.outputText().isBlank()
                    || row.errorCategory() != null || row.error() != null) {
                failures.add("Successful vision row has incoherent output or error metadata at " + key + ".");
            }
            validateStructuralChecks(row, key, failures);
        } else {
            if (row.errorCategory() == null || row.error() == null || row.error().isBlank()
                    || row.outputText() != null
                    || !safe(row.structuralChecks()).isEmpty()
                    || row.structureComplete()) {
                failures.add("Failed vision row has incoherent failure metadata at " + key + ".");
            }
            if (row.errorCategory() == VisionErrorCategory.INVALID_INPUT) {
                failures.add("Prevalidated vision corpus produced invalid-input failure at " + key + ".");
            }
        }
    }

    private void validateStructuralChecks(
            VisionMatrixRow row,
            String key,
            List<String> failures) {
        List<VisionStructuralCheck> checks = safe(row.structuralChecks());
        if (checks.stream().anyMatch(Objects::isNull)) {
            failures.add("Vision structural checks contain null entries at " + key + ".");
            return;
        }
        List<String> sections = checks.stream().map(VisionStructuralCheck::section).toList();
        if (!sections.equals(promptDefinition.requiredSections())) {
            failures.add("Vision structural checks drifted from required sections at " + key + ".");
            return;
        }
        boolean complete = checks.stream().allMatch(VisionStructuralCheck::present);
        if (row.structureComplete() != complete) {
            failures.add("Vision structureComplete disagrees with section checks at " + key + ".");
        }
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record GroupKey(String model, String caseId) {}

    record GroupAnalysis(
            int invocationSuccesses,
            int structuralCompletions,
            String expectedObservationReview,
            String unsupportedDetailReview,
            String repetitionStatus,
            boolean structuralAgreement,
            boolean exactOutputMatch,
            int tokensInAvailable,
            int tokensOutAvailable,
            long tokensInTotal,
            long tokensOutTotal,
            int latencySamples,
            double medianLatencyMs,
            long minimumLatencyMs,
            long maximumLatencyMs,
            Map<VisionErrorCategory, Integer> failures
    ) {}

    record MatrixAnalysis(
            Map<GroupKey, GroupAnalysis> groups,
            List<String> integrityFailures
    ) {

        boolean valid() {
            return integrityFailures.isEmpty();
        }
    }

    private static final class MutableGroup {

        private final List<VisionMatrixRow> rows = new ArrayList<>();

        private void add(VisionMatrixRow row) {
            rows.add(row);
        }

        private GroupAnalysis complete() {
            int successes = (int) rows.stream().filter(VisionMatrixRow::invocationSuccess).count();
            int structures = (int) rows.stream().filter(VisionMatrixRow::structureComplete).count();
            boolean hasTwoRows = rows.size() == VisionMatrixProtocol.REPETITIONS;
            boolean structuralAgreement = hasTwoRows
                    && rows.stream().map(VisionMatrixRow::structureComplete).distinct().count() == 1;
            boolean exactOutputMatch = hasTwoRows
                    && rows.getFirst().outputText() != null
                    && Objects.equals(rows.getFirst().outputText(), rows.getLast().outputText());
            String repetitionStatus = hasTwoRows && successes == VisionMatrixProtocol.REPETITIONS
                    ? "ready_for_human_review"
                    : "incomplete";

            int tokensInAvailable = (int) rows.stream().filter(row -> row.tokensIn() != null).count();
            int tokensOutAvailable = (int) rows.stream().filter(row -> row.tokensOut() != null).count();
            long tokensInTotal = rows.stream()
                    .filter(row -> row.tokensIn() != null)
                    .mapToLong(VisionMatrixRow::tokensIn)
                    .sum();
            long tokensOutTotal = rows.stream()
                    .filter(row -> row.tokensOut() != null)
                    .mapToLong(VisionMatrixRow::tokensOut)
                    .sum();

            List<Long> latencies = rows.stream()
                    .filter(VisionMatrixRow::invocationSuccess)
                    .map(VisionMatrixRow::latencyMs)
                    .sorted()
                    .toList();
            double median = median(latencies);
            long minimum = latencies.isEmpty() ? 0 : latencies.getFirst();
            long maximum = latencies.isEmpty() ? 0 : latencies.getLast();
            Map<VisionErrorCategory, Integer> failures = new EnumMap<>(VisionErrorCategory.class);
            rows.stream()
                    .filter(row -> row.errorCategory() != null)
                    .forEach(row -> failures.merge(row.errorCategory(), 1, Integer::sum));

            return new GroupAnalysis(
                    successes,
                    structures,
                    "not_performed",
                    "not_performed",
                    repetitionStatus,
                    structuralAgreement,
                    exactOutputMatch,
                    tokensInAvailable,
                    tokensOutAvailable,
                    tokensInTotal,
                    tokensOutTotal,
                    latencies.size(),
                    median,
                    minimum,
                    maximum,
                    Map.copyOf(failures));
        }

        private static double median(List<Long> sorted) {
            if (sorted.isEmpty()) {
                return 0.0;
            }
            int middle = sorted.size() / 2;
            if (sorted.size() % 2 == 1) {
                return sorted.get(middle);
            }
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        }
    }
}
