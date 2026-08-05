package com.setaccio.lab.chatmatrix;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AnthropicChatMatrixOfflineRunner {

    private AnthropicChatMatrixOfflineRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path run = resolveRunDirectory(parsed.runDirectory());
        AnthropicChatMatrixEvidence evidence = new AnthropicChatMatrixEvidence(JsonMapper.builder().findAndAddModules().build());
        AnthropicChatMatrixEvidence.OfflineResult result = parsed.reanalyze()
                ? evidence.reanalyze(run) : evidence.verify(run);
        if (!result.valid()) {
            result.failures().forEach(failure -> System.err.println("EVIDENCE: " + failure));
            throw new IllegalStateException("Anthropic evidence verification failed with " + result.failures().size() + " issue(s).");
        }
        System.out.println("Anthropic evidence " + (parsed.reanalyze() ? "reanalysis" : "verification")
                + " complete: " + run.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME));
    }

    private static Path resolveRunDirectory(String value) {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path root = project.resolve("build/anthropic-chat-matrix").normalize();
        Path run = project.resolve(value).normalize();
        if (!root.equals(run.getParent()) || !Files.isDirectory(run) || Files.isSymbolicLink(run)) {
            throw new IllegalArgumentException("Run directory must be an existing safe direct child of build/anthropic-chat-matrix/");
        }
        return run;
    }

    private record Arguments(boolean reanalyze, String runDirectory) {
        private static Arguments parse(String[] args) {
            if (args == null || args.length != 4) {
                throw usage();
            }
            List<String> values = List.of(args);
            int mode = values.indexOf("--mode");
            int run = values.indexOf("--run-dir");
            if (mode < 0 || run < 0 || mode == 3 || run == 3) {
                throw usage();
            }
            return switch (values.get(mode + 1)) {
                case "verify" -> new Arguments(false, values.get(run + 1));
                case "reanalyze" -> new Arguments(true, values.get(run + 1));
                default -> throw usage();
            };
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException("Expected --mode <verify|reanalyze> --run-dir <saved-build-directory>");
        }
    }
}
