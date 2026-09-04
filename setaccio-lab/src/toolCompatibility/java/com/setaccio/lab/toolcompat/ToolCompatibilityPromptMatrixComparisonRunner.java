package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone offline entry point for the strict Phase 2 paired-comparison gate. */
public final class ToolCompatibilityPromptMatrixComparisonRunner {

    private ToolCompatibilityPromptMatrixComparisonRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path baseline = ToolCompatibilityOfflineRunner.resolveRunDirectory(
                Path.of(""), parsed.baselineRun());
        Path candidate = ToolCompatibilityOfflineRunner.resolveRunDirectory(
                Path.of(""), parsed.candidateRun());
        ToolCompatibilityPromptMatrixComparison.ComparisonResult comparison =
                new ToolCompatibilityPromptMatrixComparison(
                        JsonMapper.builder().findAndAddModules().build())
                        .compare(baseline, candidate);

        System.out.print(comparison.report());
    }

    record Arguments(String baselineRun, String candidateRun) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of("--baseline-run", "--candidate-run");
            for (int index = 0; index < values.size(); index += 2) {
                if (!supported.contains(values.get(index))) {
                    throw usage();
                }
            }
            if (supported.stream().anyMatch(option ->
                    values.stream().filter(option::equals).count() != 1)) {
                throw usage();
            }
            return new Arguments(
                    value(values, "--baseline-run"),
                    value(values, "--candidate-run"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1) {
                throw usage();
            }
            String value = args.get(index + 1);
            if (value.isBlank() || !value.equals(value.strip())) {
                throw usage();
            }
            return value;
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --baseline-run <saved-evidence-directory> "
                            + "--candidate-run <saved-evidence-directory>");
        }
    }
}
