package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;

/** Verifies or reanalyzes saved R6 evidence without starting Spring or calling a provider. */
public final class RetrievalRelevancyOfflineRunner {

    private RetrievalRelevancyOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = resolveRunDirectory(parsed.runDirectory());
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RetrievalRelevancyEvidence evidence = new RetrievalRelevancyEvidence(
                objectMapper, inputs.corpus(), inputs.catalog());
        RetrievalRelevancyEvidence.OfflineResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verify(runDirectory)
                : evidence.reanalyze(runDirectory);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException("Retrieval relevancy evidence " + parsed.mode().label
                    + " failed with " + result.failures().size() + " issue(s).");
        }
        System.out.println("Retrieval relevancy evidence " + parsed.mode().label + " complete: "
                + runDirectory.resolve(RetrievalRelevancyEvidence.SUMMARY_FILENAME));
    }

    static Path resolveRunDirectory(String value) {
        return RetrievalRelevancyProtocol.EVIDENCE_ROOT.requireSavedRunDirectory(Path.of(""), value, "Run directory");
    }

    private enum Mode {
        VERIFY("verification"), REANALYZE("reanalysis");

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
            return new Arguments(Mode.parse(required(values, "--mode")), required(values, "--run-dir"));
        }

        private static String required(List<String> values, String option) {
            if (values.stream().filter(option::equals).count() != 1) {
                throw usage();
            }
            int index = values.indexOf(option);
            if (index == values.size() - 1 || values.get(index + 1).isBlank()) {
                throw usage();
            }
            return values.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --mode <verify|reanalyze> --run-dir <saved-evidence-directory-under-"
                            + RetrievalRelevancyProtocol.EVIDENCE_ROOT.durableRelativePath() + ">");
        }
    }
}
