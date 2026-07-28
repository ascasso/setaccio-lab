package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

public final class ToolSearchMatrixOfflineRunner {

    private ToolSearchMatrixOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = resolveRunDirectory(parsed.runDirectory());
        ToolSearchMatrixEvidence evidence = new ToolSearchMatrixEvidence(
                JsonMapper.builder().findAndAddModules().build());
        ToolSearchMatrixEvidence.OfflineResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verify(runDirectory)
                : evidence.reanalyze(runDirectory);

        System.out.println("Tool Search evidence format: " + result.manifestFormat().label());
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException(
                    "Tool Search evidence " + parsed.mode().label + " failed with "
                            + result.failures().size() + " issue(s).");
        }
        System.out.println("Tool Search evidence " + parsed.mode().label + " complete: "
                + runDirectory.resolve(ToolSearchMatrixEvidence.SUMMARY_FILENAME));
    }

    private static Path resolveRunDirectory(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path matrixRoot = projectDirectory.resolve("build/tool-search-matrix").normalize();
        Path runDirectory = projectDirectory.resolve(value).normalize();
        if (!runDirectory.startsWith(matrixRoot)) {
            throw new IllegalArgumentException("Run directory must be under build/tool-search-matrix/");
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
            if (args.length != 4) {
                throw new IllegalArgumentException(
                        "Expected --mode <verify|reanalyze> --run-dir <saved-build-directory>");
            }
            List<String> values = List.of(args);
            int modeIndex = values.indexOf("--mode");
            int runDirectoryIndex = values.indexOf("--run-dir");
            if (modeIndex < 0 || runDirectoryIndex < 0
                    || modeIndex == values.size() - 1 || runDirectoryIndex == values.size() - 1) {
                throw new IllegalArgumentException(
                        "Expected --mode <verify|reanalyze> --run-dir <saved-build-directory>");
            }
            String runDirectory = values.get(runDirectoryIndex + 1);
            if (runDirectory.isBlank()) {
                throw new IllegalArgumentException("Run directory must not be blank.");
            }
            return new Arguments(Mode.parse(values.get(modeIndex + 1)), runDirectory);
        }
    }
}
