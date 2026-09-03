package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;

/** Verifies or deterministically regenerates saved R3 evidence without a provider or Spring startup. */
public final class RetrievalEvaluationOfflineRunner {

    private RetrievalEvaluationOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = parsed.mode() == Mode.REANALYZE
                ? RetrievalEvaluationProtocol.EVIDENCE_ROOT.requireReanalyzableRunDirectory(
                        Path.of(""), parsed.runDirectory(), "Run directory")
                : resolveRunDirectory(parsed.runDirectory());
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RetrievalEvaluationEvidence evidence = new RetrievalEvaluationEvidence(
                objectMapper,
                inputs.corpus(),
                inputs.catalog());
        RetrievalEvaluationEvidence.OfflineResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verify(runDirectory)
                : evidence.reanalyze(runDirectory);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException("Retrieval evaluation evidence " + parsed.mode().label
                    + " failed with " + result.failures().size() + " issue(s).");
        }
        System.out.println("Retrieval evaluation evidence " + parsed.mode().label + " complete: "
                + runDirectory.resolve(RetrievalEvaluationEvidence.SUMMARY_FILENAME));
    }

    static Path resolveRunDirectory(String value) {
        return RetrievalEvaluationProtocol.EVIDENCE_ROOT.requireSavedRunDirectory(Path.of(""), value, "Run directory");
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
                    "Expected --mode <verify|reanalyze> --run-dir <saved-evidence-directory-under-"
                            + RetrievalEvaluationProtocol.EVIDENCE_ROOT.durableRelativePath() + ">");
        }
    }
}
