package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;

public final class VisionMatrixComparisonRunner {

    private VisionMatrixComparisonRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        VisionMatrixComparison.ComparisonResult comparison = new VisionMatrixComparison(
                JsonMapper.builder().findAndAddModules().build())
                .compare(
                        VisionMatrixOfflineRunner.resolveRunDirectory(parsed.baselineRunDirectory()),
                        VisionMatrixOfflineRunner.resolveRunDirectory(parsed.candidateRunDirectory()));
        System.out.print(comparison.report());
    }

    private record Arguments(String baselineRunDirectory, String candidateRunDirectory) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = List.of(args);
            return new Arguments(
                    value(values, "--baseline-run-dir"),
                    value(values, "--candidate-run-dir"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1 || args.get(index + 1).isBlank()) {
                throw usage();
            }
            return args.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --baseline-run-dir <saved-build-directory> "
                            + "--candidate-run-dir <saved-build-directory>");
        }
    }
}
