package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Standalone provider-free verification and deterministic reanalysis entry point. */
public final class ToolCompatibilityOfflineRunner {

    private ToolCompatibilityOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = resolveRunDirectory(Path.of(""), parsed.runDirectory());
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ToolCompatibilityEvidence evidence = new ToolCompatibilityEvidence(objectMapper);
        ToolCompatibilityEvidence.OfflineResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verify(runDirectory)
                : evidence.reanalyze(runDirectory);

        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException(
                    "Tool compatibility evidence " + parsed.mode().label + " failed with "
                            + result.failures().size() + " issue(s).");
        }
        System.out.println("Tool compatibility evidence " + parsed.mode().label + " complete: "
                + runDirectory.resolve(ToolCompatibilityEvidence.SUMMARY_FILENAME));
    }

    static Path resolveRunDirectory(Path projectDirectory, String value) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("--run-dir must be nonblank and trimmed");
        }
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path evidenceRoot = project.resolve("build/tool-compatibility").normalize();
        Path runDirectory = project.resolve(value).normalize();
        if (!evidenceRoot.equals(runDirectory.getParent())) {
            throw new IllegalArgumentException(
                    "Run directory must be directly under build/tool-compatibility/.");
        }
        ToolCompatibilityPreflight.requireNoSymbolicLinks(project, runDirectory);
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
                default -> throw usage();
            };
        }
    }

    private record Arguments(Mode mode, String runDirectory) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of("--mode", "--run-dir");
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
                    value(values, "--run-dir"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1 || args.get(index + 1).isBlank()) {
                throw usage();
            }
            return args.get(index + 1);
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException(
                "Expected --mode <verify|reanalyze> --run-dir <saved-build-directory>");
    }
}
