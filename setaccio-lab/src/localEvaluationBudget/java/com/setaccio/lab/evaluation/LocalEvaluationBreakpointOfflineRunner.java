package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone provider-free verification and reanalysis for one five-arm breakpoint study. */
public final class LocalEvaluationBreakpointOfflineRunner {

    private LocalEvaluationBreakpointOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Map<Integer, Path> directories = resolveRunDirectories(parsed.runDirectories());
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalEvaluationBreakpointEvidence evidence = new LocalEvaluationBreakpointEvidence(
                objectMapper, new LocalFactCheckPromptDefinition(), catalog,
                new LocalFactCheckFixtureReview(objectMapper, catalog));
        LocalEvaluationBreakpointEvidence.OfflineStudyResult result = parsed.mode() == Mode.VERIFY
                ? evidence.verifyStudy(directories)
                : evidence.reanalyzeStudy(directories);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException("Breakpoint study " + parsed.mode().label + " failed with "
                    + result.failures().size() + " issue(s).");
        }
        System.out.println("Breakpoint study " + parsed.mode().label + " complete:");
        directories.forEach((tokens, directory) -> System.out.println("  " + tokens + " tokens: "
                + directory.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME)));
    }

    static Map<Integer, Path> resolveRunDirectories(Map<Integer, String> values) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/evaluation-matrix").normalize();
        Map<Integer, Path> resolved = new LinkedHashMap<>();
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            Path directory = projectDirectory.resolve(values.get(tokens)).normalize();
            if (!evidenceRoot.equals(directory.getParent()) || Files.isSymbolicLink(directory)
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException(
                        "Breakpoint run directory must exist safely directly under build/evaluation-matrix/.");
            }
            resolved.put(tokens, directory);
        }
        return Map.copyOf(resolved);
    }

    private enum Mode {
        VERIFY("verification"), REANALYZE("reanalysis");
        private final String label;
        Mode(String label) { this.label = label; }
        private static Mode parse(String value) {
            return switch (value) {
                case "verify" -> VERIFY;
                case "reanalyze" -> REANALYZE;
                default -> throw new IllegalArgumentException("Mode must be verify or reanalyze.");
            };
        }
    }

    record Arguments(Mode mode, Map<Integer, String> runDirectories) {
        static Arguments parse(String[] args) {
            int optionCount = 1 + LocalEvaluationBreakpointProtocol.MAX_TOKENS.size();
            if (args == null || args.length != optionCount * 2) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = new ArrayList<>(List.of("--mode"));
            LocalEvaluationBreakpointProtocol.MAX_TOKENS.forEach(tokens -> supported.add("--run-dir-" + tokens));
            for (int index = 0; index < values.size(); index += 2) {
                if (!supported.contains(values.get(index))) {
                    throw usage();
                }
            }
            if (supported.stream().anyMatch(option -> values.stream().filter(option::equals).count() != 1)) {
                throw usage();
            }
            Map<Integer, String> directories = new LinkedHashMap<>();
            for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
                directories.put(tokens, value(values, "--run-dir-" + tokens));
            }
            return new Arguments(Mode.parse(value(values, "--mode")), Map.copyOf(directories));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1 || args.get(index + 1).isBlank()) {
                throw usage();
            }
            return args.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException("Expected --mode <verify|reanalyze> and one --run-dir-<tokens> "
                    + "argument for each locked arm: 64, 96, 128, 192, and 256.");
        }
    }
}
