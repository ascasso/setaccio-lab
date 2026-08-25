package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone provider-free verification and reanalysis for one F1 pair. */
public final class LocalEvaluationBudgetOfflineRunner {

    private LocalEvaluationBudgetOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path budget64 = resolveRunDirectory(parsed.budget64RunDirectory());
        Path budget256 = resolveRunDirectory(parsed.budget256RunDirectory());
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalFactCheckPromptDefinition prompt = new LocalFactCheckPromptDefinition();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalFactCheckFixtureReview review = new LocalFactCheckFixtureReview(objectMapper, catalog);
        LocalEvaluationBudgetEvidence evidence = new LocalEvaluationBudgetEvidence(
                objectMapper,
                prompt,
                catalog,
                review);
        LocalEvaluationBudgetEvidence.OfflinePairResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verifyPair(budget64, budget256)
                : evidence.reanalyzePair(budget64, budget256);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException(
                    "F1 budget pair " + parsed.mode().label + " failed with "
                            + result.failures().size() + " issue(s).");
        }
        System.out.println("F1 budget pair " + parsed.mode().label + " complete:");
        System.out.println("  64 tokens: " + budget64.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
        System.out.println("  256 tokens: " + budget256.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
    }

    static Path resolveRunDirectory(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/evaluation-matrix").normalize();
        Path runDirectory = projectDirectory.resolve(value).normalize();
        if (!evidenceRoot.equals(runDirectory.getParent())) {
            throw new IllegalArgumentException(
                    "F1 budget run directory must be directly under build/evaluation-matrix/.");
        }
        if (Files.isSymbolicLink(runDirectory)
                || !Files.isDirectory(runDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("F1 budget run directory does not exist or is unsafe.");
        }
        return runDirectory;
    }

    private enum Mode {
        VERIFY("verification"),
        REANALYZE("reanalysis");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        private static Mode parse(String value) {
            return switch (value) {
                case "verify" -> VERIFY;
                case "reanalyze" -> REANALYZE;
                default -> throw new IllegalArgumentException("Mode must be verify or reanalyze.");
            };
        }
    }

    record Arguments(Mode mode, String budget64RunDirectory, String budget256RunDirectory) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 6) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of("--mode", "--budget-64-run-dir", "--budget-256-run-dir");
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
                    Mode.parse(value(values, "--mode")),
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
                    "Expected --mode <verify|reanalyze> "
                            + "--budget-64-run-dir <saved-build-directory> "
                            + "--budget-256-run-dir <saved-build-directory>");
        }
    }
}
