package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.LabApplication;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import com.setaccio.lab.service.ToolBenchmarkService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public final class ToolSearchSmokeRunner {

    private static final String PULL_STRATEGY_PROPERTY = "spring.ai.ollama.init.pull-model-strategy";

    private ToolSearchSmokeRunner() {}

    public static void main(String[] args) {
        Arguments arguments = Arguments.parse(args);
        List<ToolBenchmarkPrompt> prompts = selectCases(arguments.caseIds());
        List<String> toolNames = ToolBenchmarkCases.toolNames();

        System.out.println("Starting opt-in Tool Search smoke diagnostic");
        System.out.println("  Model: " + arguments.model());
        System.out.println("  Cases: " + prompts.stream().map(ToolBenchmarkPrompt::id).toList());
        System.out.println("  Ollama pull strategy: never");

        ToolSearchSmokeSummary summary;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LabApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .run(
                        "--" + PULL_STRATEGY_PROPERTY + "=never",
                        "--spring.ai.chat.client.tool-search-advisor.enabled=true",
                        "--spring.ai.chat.client.tool-search-advisor.tool-index-type=regex",
                        "--spring.ai.model.chat=ollama",
                        "--setaccio.lab.results-dir=build/lab-results/tool-search-smoke")) {
            assertPullStrategy(context.getEnvironment());
            ToolBenchmarkComparisonResult result = context.getBean(ToolBenchmarkService.class).compare(
                    List.of(arguments.model()),
                    prompts,
                    toolNames,
                    new ToolBenchmarkRunSettings(
                            1,
                            ToolBenchmarkRunSettings.DEFAULT_TEMPERATURE,
                            ToolBenchmarkRunSettings.DEFAULT_BASE_SEED,
                            null,
                            ToolBenchmarkComparisonOrder.STANDARD_FIRST));
            summary = new ToolSearchSmokeAnalyzer(context.getBean(ObjectMapper.class))
                    .analyze(result, arguments.model(), prompts, toolNames);
        } catch (Exception e) {
            summary = new ToolSearchSmokeSummary();
            summary.hardFailure("Startup or invocation failed: " + errorDetail(e));
        }
        summary.printTo(System.out);
        if (summary.hasHardFailures()) {
            throw new IllegalStateException("Tool Search smoke detected "
                    + summary.hardFailures().size() + " infrastructure/integrity failure(s).");
        }
    }

    static List<ToolBenchmarkPrompt> selectCases(String caseIds) {
        List<ToolBenchmarkPrompt> defaults = ToolBenchmarkCases.defaults();
        if (caseIds == null) {
            return defaults;
        }
        if (caseIds.isBlank()) {
            throw new IllegalArgumentException("--case-ids must not be blank");
        }
        Map<String, ToolBenchmarkPrompt> byId = new LinkedHashMap<>();
        for (ToolBenchmarkPrompt prompt : defaults) {
            byId.put(prompt.id(), prompt);
        }
        List<ToolBenchmarkPrompt> selected = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        for (String rawSelector : caseIds.split(",", -1)) {
            String selector = rawSelector.trim();
            if (selector.isEmpty()) {
                throw new IllegalArgumentException("--case-ids contains an empty selector");
            }
            ToolBenchmarkPrompt prompt;
            if (selector.chars().allMatch(Character::isDigit)) {
                int ordinal;
                try {
                    ordinal = Integer.parseInt(selector);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid case ordinal: " + selector, e);
                }
                if (ordinal < 1 || ordinal > defaults.size()) {
                    throw new IllegalArgumentException("Case ordinal " + selector
                            + " is outside the valid range 1-" + defaults.size());
                }
                prompt = defaults.get(ordinal - 1);
            } else {
                prompt = byId.get(selector);
                if (prompt == null) {
                    throw new IllegalArgumentException("Unknown case ID: " + selector);
                }
            }
            if (!selectedIds.add(prompt.id())) {
                throw new IllegalArgumentException("Duplicate case selection: " + prompt.id());
            }
            selected.add(prompt);
        }
        return List.copyOf(selected);
    }

    private static void assertPullStrategy(Environment environment) {
        String strategy = environment.getProperty(PULL_STRATEGY_PROPERTY);
        if (!"never".equalsIgnoreCase(strategy)) {
            throw new IllegalStateException("Effective Ollama pull strategy must be never, but was " + strategy);
        }
    }

    private static String errorDetail(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private record Arguments(String model, String caseIds) {

        private static Arguments parse(String[] args) {
            String model = null;
            String caseIds = null;
            for (int index = 0; index < args.length; index++) {
                String option = args[index];
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + option);
                }
                String value = args[++index];
                switch (option) {
                    case "--model" -> {
                        if (model != null) {
                            throw new IllegalArgumentException("--model may be supplied only once");
                        }
                        model = value;
                    }
                    case "--case-ids" -> {
                        if (caseIds != null) {
                            throw new IllegalArgumentException("--case-ids may be supplied only once");
                        }
                        caseIds = value;
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + option);
                }
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("--model must identify an already-installed Ollama model");
            }
            return new Arguments(model.trim(), caseIds);
        }
    }
}
