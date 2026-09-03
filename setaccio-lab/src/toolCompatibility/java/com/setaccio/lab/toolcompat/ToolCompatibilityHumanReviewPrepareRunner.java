package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Standalone offline entry point for one private, owner-completed Phase 2 review worksheet. */
public final class ToolCompatibilityHumanReviewPrepareRunner {

    static final String OUTPUT_ROOT =
            ToolCompatibilityProtocol.HUMAN_REVIEW_ROOT.durableRelativePath();
    private static final String SAFE_SEGMENT = "[A-Za-z0-9][A-Za-z0-9._-]*";

    private ToolCompatibilityHumanReviewPrepareRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path projectDirectory = Path.of("");
        Path baseline = ToolCompatibilityOfflineRunner.resolveRunDirectory(
                projectDirectory, parsed.baselineRun());
        Path candidate = ToolCompatibilityOfflineRunner.resolveRunDirectory(
                projectDirectory, parsed.candidateRun());
        Path outputRoot = resolveReviewRoot(projectDirectory, parsed.outputRoot());
        LocalDate reviewDate = parseReviewDate(parsed.reviewDate());
        String reviewId = reviewId(baseline, candidate, reviewDate);

        ToolCompatibilityHumanReviewPreparer.PreparationResult prepared =
                new ToolCompatibilityHumanReviewPreparer(JsonMapper.builder().findAndAddModules().build())
                        .prepare(baseline, candidate, outputRoot, reviewDate, reviewId);
        System.out.println("Private tool compatibility human-review worksheet prepared: "
                + prepared.worksheet());
    }

    static Path resolveReviewRoot(Path projectDirectory, String value) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path actual = ToolCompatibilityProtocol.HUMAN_REVIEW_ROOT.resolveFixedDurableRoot(
                project, value, "Review output root");
        ToolCompatibilityPreflight.requireNoSymbolicLinks(project, actual);
        return actual;
    }

    static String reviewId(Path baseline, Path candidate, LocalDate reviewDate) {
        if (baseline == null || candidate == null || reviewDate == null) {
            throw new IllegalArgumentException("baseline, candidate, and reviewDate are required");
        }
        String baselineName = baseline.getFileName().toString();
        String candidateName = candidate.getFileName().toString();
        if (!baselineName.matches(SAFE_SEGMENT) || !candidateName.matches(SAFE_SEGMENT)) {
            throw new IllegalArgumentException("Tool compatibility run directory names must be safe path segments.");
        }
        return baselineName + "--vs--" + candidateName + "--review-" + reviewDate;
    }

    static LocalDate parseReviewDate(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("--review-date must be a trimmed ISO-8601 date");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("--review-date must be a valid ISO-8601 date", exception);
        }
    }

    record Arguments(String baselineRun, String candidateRun, String reviewDate, String outputRoot) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 8) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of(
                    "--baseline-run", "--candidate-run", "--review-date", "--output-root");
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
                    value(values, "--candidate-run"),
                    value(values, "--review-date"),
                    value(values, "--output-root"));
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
                            + "--candidate-run <saved-evidence-directory> "
                            + "--review-date <YYYY-MM-DD> "
                            + "--output-root <private-review-root>");
        }
    }
}
