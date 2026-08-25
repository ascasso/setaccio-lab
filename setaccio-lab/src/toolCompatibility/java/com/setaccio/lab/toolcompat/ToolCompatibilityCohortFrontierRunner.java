package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;

/** Standalone provider-free T3.6 entry point that writes only to standard output. */
public final class ToolCompatibilityCohortFrontierRunner {

    private ToolCompatibilityCohortFrontierRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path runDirectory = ToolCompatibilityOfflineRunner.resolveRunDirectory(
                Path.of(""), parsed.runDirectory());
        ToolCompatibilityCohortFrontier.FrontierResult frontier =
                new ToolCompatibilityCohortFrontier(
                        JsonMapper.builder().findAndAddModules().build())
                        .analyze(runDirectory);
        System.out.print(frontier.report());
    }

    record Arguments(String runDirectory) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 2) {
                throw usage();
            }
            List<String> values = List.of(args);
            if (!"--run-dir".equals(values.getFirst())) {
                throw usage();
            }
            String value = values.getLast();
            if (value.isBlank() || !value.equals(value.strip())) {
                throw usage();
            }
            return new Arguments(value);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --run-dir <saved-build-directory>");
        }
    }
}
