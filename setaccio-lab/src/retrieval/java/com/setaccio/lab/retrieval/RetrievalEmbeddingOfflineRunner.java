package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Verifies or deterministically regenerates saved R4 evidence without a provider or Spring startup. */
public final class RetrievalEmbeddingOfflineRunner {

    private RetrievalEmbeddingOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = resolveRunDirectory(parsed.runDirectory());
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RetrievalEmbeddingEvidence evidence = new RetrievalEmbeddingEvidence(
                objectMapper,
                inputs.corpus(),
                inputs.catalog());
        RetrievalEmbeddingEvidence.OfflineResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verify(runDirectory)
                : evidence.reanalyze(runDirectory);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException("Retrieval embedding evidence " + parsed.mode().label
                    + " failed with " + result.failures().size() + " issue(s).");
        }
        System.out.println("Retrieval embedding evidence " + parsed.mode().label + " complete: "
                + runDirectory.resolve(RetrievalEmbeddingEvidence.SUMMARY_FILENAME));
    }

    static Path resolveRunDirectory(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/retrieval-embedding").normalize();
        Path runDirectory = projectDirectory.resolve(value).normalize();
        if (!evidenceRoot.equals(runDirectory.getParent())) {
            throw new IllegalArgumentException(
                    "Run directory must be directly under build/retrieval-embedding/.");
        }
        if (Files.isSymbolicLink(runDirectory)
                || !Files.isDirectory(runDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Run directory does not exist or is unsafe.");
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

    private record Arguments(Mode mode, String runDirectory) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = List.of(args);
            int modeIndex = values.indexOf("--mode");
            int runDirectoryIndex = values.indexOf("--run-dir");
            if (modeIndex < 0 || runDirectoryIndex < 0
                    || modeIndex == values.size() - 1 || runDirectoryIndex == values.size() - 1
                    || values.get(modeIndex + 1).isBlank() || values.get(runDirectoryIndex + 1).isBlank()) {
                throw usage();
            }
            return new Arguments(
                    Mode.parse(values.get(modeIndex + 1)),
                    values.get(runDirectoryIndex + 1));
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --mode <verify|reanalyze> --run-dir <saved-build-directory>");
        }
    }
}
