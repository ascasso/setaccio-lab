package com.setaccio.lab.fixture;

import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.tool.ArithmeticBenchmarkTools;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import com.setaccio.lab.tool.FixtureCatalogTools;
import com.setaccio.lab.tool.FixtureTimeTools;
import java.util.List;

public final class ToolBenchmarkCases {

    private static final List<String> TOOL_NAMES = List.of(
            ArithmeticBenchmarkTools.ADD_TOOL_NAME,
            ArithmeticBenchmarkTools.MULTIPLY_TOOL_NAME,
            FixtureTimeTools.FIXED_UTC_NOW_TOOL_NAME,
            FixtureTimeTools.FIXED_TIME_FOR_ZONE_TOOL_NAME,
            FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME,
            FixtureCatalogTools.LIST_ITEMS_TOOL_NAME,
            FailureBenchmarkTools.FAIL_TOOL_NAME
    );

    private static final List<ToolBenchmarkPrompt> DEFAULT_CASES = List.of(
            new ToolBenchmarkPrompt(
                    "arithmetic-add",
                    "Use the available tools to add 17.25 and 4.75, then answer with the result.",
                    expectation(
                            List.of(ArithmeticBenchmarkTools.ADD_TOOL_NAME),
                            List.of(),
                            List.of("22"),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "fixed-utc-time",
                    "Use the available tools to report the fixed benchmark UTC timestamp.",
                    expectation(
                            List.of(FixtureTimeTools.FIXED_UTC_NOW_TOOL_NAME),
                            List.of(),
                            List.of(),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "fixed-zone-time",
                    "Use the available tools to convert the fixed benchmark timestamp to America/Los_Angeles.",
                    expectation(
                            List.of(FixtureTimeTools.FIXED_TIME_FOR_ZONE_TOOL_NAME),
                            List.of(),
                            List.of("America/Los_Angeles"),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "catalog-lookup",
                    "Use the available tools to look up the catalog fixture fixture-policy-faq and summarize it.",
                    expectation(
                            List.of(FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME),
                            List.of(),
                            List.of("Policy FAQ"),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "catalog-multi-step",
                    "Use the available tools to look up fixture-invoice-sample, then list the document category and confirm whether that fixture appears in the list.",
                    expectation(
                            List.of(FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME, FixtureCatalogTools.LIST_ITEMS_TOOL_NAME),
                            List.of(),
                            List.of("fixture-invoice-sample"),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "catalog-no-match",
                    "Use the available tools to look up catalog fixture fixture-does-not-exist and report the deterministic lookup result.",
                    expectation(
                            List.of(FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME),
                            List.of(),
                            List.of(),
                            List.of("No catalog fixture matched"))),
            new ToolBenchmarkPrompt(
                    "no-applicable-domain-tool",
                    "Use tool discovery to determine whether a domain tool is needed to repeat the marker BENCHMARK_NO_TOOL. Do not invoke a domain callback; answer exactly BENCHMARK_NO_TOOL.",
                    expectation(
                            List.of(),
                            TOOL_NAMES,
                            List.of("BENCHMARK_NO_TOOL"),
                            List.of())),
            new ToolBenchmarkPrompt(
                    "deterministic-tool-failure",
                    "Use the deterministic failure fixture tool and then report that the tool returned an error.",
                    expectation(
                            List.of(FailureBenchmarkTools.FAIL_TOOL_NAME),
                            List.of(),
                            List.of(),
                            List.of(FailureBenchmarkTools.FAILURE_MARKER)))
    );

    private ToolBenchmarkCases() {}

    public static List<ToolBenchmarkPrompt> defaults() {
        return DEFAULT_CASES;
    }

    public static List<String> toolNames() {
        return TOOL_NAMES;
    }

    private static ToolBenchmarkExpectation expectation(
            List<String> requiredExecutedTools,
            List<String> forbiddenExecutedTools,
            List<String> requiredOutputTerms,
            List<String> requiredToolResponseTerms) {
        return new ToolBenchmarkExpectation(
                requiredExecutedTools,
                forbiddenExecutedTools,
                requiredOutputTerms,
                requiredToolResponseTerms
        );
    }
}
