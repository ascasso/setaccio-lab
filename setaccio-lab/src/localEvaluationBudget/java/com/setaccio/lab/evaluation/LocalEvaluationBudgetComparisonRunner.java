package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone provider-free F3 report entry point for one verified F1 pair. */
public final class LocalEvaluationBudgetComparisonRunner {

    private LocalEvaluationBudgetComparisonRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path budget64 = LocalEvaluationBudgetOfflineRunner.resolveRunDirectory(parsed.budget64RunDirectory());
        Path budget256 = LocalEvaluationBudgetOfflineRunner.resolveRunDirectory(parsed.budget256RunDirectory());
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalFactCheckPromptDefinition prompt = new LocalFactCheckPromptDefinition();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalFactCheckFixtureReview review = new LocalFactCheckFixtureReview(objectMapper, catalog);
        LocalEvaluationBudgetComparison.ComparisonResult comparison = new LocalEvaluationBudgetComparison(
                objectMapper,
                prompt,
                catalog,
                review).compare(budget64, budget256);
        System.out.print(comparison.report());
    }

    record Arguments(String budget64RunDirectory, String budget256RunDirectory) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of("--budget-64-run-dir", "--budget-256-run-dir");
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
                    value(values, "--budget-64-run-dir"),
                    value(values, "--budget-256-run-dir"));
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
                    "Expected --budget-64-run-dir <saved-evidence-directory> "
                            + "--budget-256-run-dir <saved-evidence-directory>");
        }
    }
}
