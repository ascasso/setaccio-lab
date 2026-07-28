package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

public final class VisionHumanReviewPrepareRunner {

    private static final String SAFE_SEGMENT = "[A-Za-z0-9][A-Za-z0-9._-]*";

    private VisionHumanReviewPrepareRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path baseline = VisionMatrixOfflineRunner.resolveRunDirectory(parsed.baselineRunDirectory());
        Path candidate = VisionMatrixOfflineRunner.resolveRunDirectory(parsed.candidateRunDirectory());
        Path corpus = VisionMatrixRunner.resolveCorpusDirectory(parsed.corpusDirectory());
        Path outputRoot = resolveReviewRoot(parsed.outputRoot());
        String reviewId = reviewId(baseline, candidate);

        VisionHumanReviewPreparer.PreparationResult result = new VisionHumanReviewPreparer(
                JsonMapper.builder().findAndAddModules().build(),
                new ApacheCommonsBlake3HashingServiceImpl())
                .prepare(baseline, candidate, corpus, outputRoot, reviewId);
        System.out.println("Private human-review worksheet prepared: " + result.worksheet());
    }

    static Path resolveReviewRoot(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path expected = projectDirectory.resolve("build/vision-human-review").normalize();
        Path actual = projectDirectory.resolve(value).normalize();
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Review output root must be the fixed build/vision-human-review directory.");
        }
        if (Files.isSymbolicLink(actual)
                || (Files.exists(actual) && !Files.isDirectory(actual, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("Review output root is unsafe.");
        }
        return actual;
    }

    static String reviewId(Path baseline, Path candidate) {
        String baselineName = baseline.getFileName().toString();
        String candidateName = candidate.getFileName().toString();
        if (!baselineName.matches(SAFE_SEGMENT) || !candidateName.matches(SAFE_SEGMENT)) {
            throw new IllegalArgumentException("Vision run directory names must be safe path segments.");
        }
        return baselineName + "--vs--" + candidateName;
    }

    private record Arguments(
            String baselineRunDirectory,
            String candidateRunDirectory,
            String corpusDirectory,
            String outputRoot) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 8) {
                throw usage();
            }
            List<String> values = List.of(args);
            return new Arguments(
                    value(values, "--baseline-run-dir"),
                    value(values, "--candidate-run-dir"),
                    value(values, "--corpus-dir"),
                    value(values, "--output-root"));
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
                            + "--candidate-run-dir <saved-build-directory> "
                            + "--corpus-dir <local-vision-corpus> "
                            + "--output-root <private-review-root>");
        }
    }
}
