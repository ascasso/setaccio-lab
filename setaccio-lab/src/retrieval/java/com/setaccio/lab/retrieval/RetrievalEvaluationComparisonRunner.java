package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;

/** Command-line entry point for offline comparison of two verified R3 runs. */
public final class RetrievalEvaluationComparisonRunner {

    private RetrievalEvaluationComparisonRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        RetrievalEvaluationComparison.ComparisonResult comparison = new RetrievalEvaluationComparison(
                JsonMapper.builder().findAndAddModules().build(),
                inputs.corpus(),
                inputs.catalog()).compare(
                        RetrievalEvaluationOfflineRunner.resolveRunDirectory(parsed.baselineRunDirectory()),
                        RetrievalEvaluationOfflineRunner.resolveRunDirectory(parsed.candidateRunDirectory()));
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
                    "Expected --baseline-run-dir <saved-evidence-directory> "
                            + "--candidate-run-dir <saved-evidence-directory>");
        }
    }
}
