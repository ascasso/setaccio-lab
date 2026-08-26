package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.Map;

/** Standalone provider-free breakpoint aggregate-report entry point. */
public final class LocalEvaluationBreakpointComparisonRunner {

    private LocalEvaluationBreakpointComparisonRunner() {}

    public static void main(String[] args) {
        LocalEvaluationBreakpointOfflineRunner.Arguments parsed =
                LocalEvaluationBreakpointOfflineRunner.Arguments.parse(withVerifyMode(args));
        Map<Integer, Path> directories = LocalEvaluationBreakpointOfflineRunner.resolveRunDirectories(
                parsed.runDirectories());
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalEvaluationBreakpointComparison.ComparisonResult comparison = new LocalEvaluationBreakpointComparison(
                objectMapper, new LocalFactCheckPromptDefinition(), catalog,
                new LocalFactCheckFixtureReview(objectMapper, catalog)).compare(directories);
        System.out.print(comparison.report());
    }

    private static String[] withVerifyMode(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("Breakpoint comparison arguments are required.");
        }
        String[] result = new String[args.length + 2];
        result[0] = "--mode";
        result[1] = "verify";
        System.arraycopy(args, 0, result, 2, args.length);
        return result;
    }
}
