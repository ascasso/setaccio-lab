package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.LabApplication;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import com.setaccio.lab.service.ToolBenchmarkService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class ToolSearchMatrixBaselineRunner {

    static final List<String> MODELS = ToolSearchMatrixProtocol.MODELS;
    static final List<String> CASE_IDS = ToolSearchMatrixProtocol.CASE_IDS;
    static final ToolBenchmarkRunSettings SETTINGS = ToolSearchMatrixProtocol.SETTINGS;
    private static final String PULL_STRATEGY_PROPERTY = "spring.ai.ollama.init.pull-model-strategy";

    private ToolSearchMatrixBaselineRunner() {}

    public static void main(String[] args) {
        Path outputDir = resolveNewOutputDir(parseOutputDir(args));
        List<ToolBenchmarkPrompt> prompts = canonicalPrompts();
        List<String> tools = ToolSearchMatrixProtocol.toolNames();

        System.out.println("Starting locked post-fix Tool Search matrix baseline");
        System.out.println("  Models: " + MODELS);
        System.out.println("  Cases: " + CASE_IDS);
        System.out.println("  Repetitions/seeds: 2 / [42, 43]");
        System.out.println("  Temperature/order: 0.0 / alternate paired sequential");
        System.out.println("  Output: " + outputDir);
        System.out.println("  Ollama pull strategy: never");

        try {
            EvidenceRunDirectory.createNamed(outputDir.getParent(), outputDir.getFileName().toString());
            ToolBenchmarkComparisonResult result;
            ObjectMapper objectMapper;
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LabApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("local")
                    .run(
                            "--" + PULL_STRATEGY_PROPERTY + "=never",
                            "--spring.ai.chat.client.tool-search-advisor.enabled=true",
                            "--spring.ai.chat.client.tool-search-advisor.tool-index-type=regex",
                            "--spring.ai.model.chat=ollama",
                            "--setaccio.lab.results-dir=" + outputDir)) {
                String pullStrategy = context.getEnvironment().getProperty(PULL_STRATEGY_PROPERTY);
                if (!"never".equalsIgnoreCase(pullStrategy)) {
                    throw new IllegalStateException("Effective Ollama pull strategy is not never: " + pullStrategy);
                }
                objectMapper = context.getBean(ObjectMapper.class);
                result = context.getBean(ToolBenchmarkService.class).compare(MODELS, prompts, tools, SETTINGS);
            }

            ToolSearchMatrixAnalyzer.MatrixAnalysis analysis = new ToolSearchMatrixAnalyzer(objectMapper)
                    .analyze(result, MODELS, prompts, tools);
            Path rawJson = singleRawJson(outputDir);
            new ToolSearchMatrixEvidence(objectMapper)
                    .writeVersionOne(outputDir, rawJson, result, analysis);

            if (!analysis.valid()) {
                analysis.integrityFailures().forEach(failure -> System.err.println("INTEGRITY: " + failure));
                throw new IllegalStateException("Matrix baseline failed " + analysis.integrityFailures().size()
                        + " integrity check(s); artifacts were retained for diagnosis.");
            }
            System.out.println("Matrix baseline complete: " + outputDir.resolve("SUMMARY.md"));
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Matrix baseline failed: " + e.getMessage(), e);
        }
    }

    static List<ToolBenchmarkPrompt> canonicalPrompts() {
        return ToolSearchMatrixProtocol.canonicalPrompts();
    }

    private static String parseOutputDir(String[] args) {
        if (args.length != 2 || !"--output-dir".equals(args[0]) || args[1].isBlank()) {
            throw new IllegalArgumentException("Expected --output-dir <dated-build-directory>");
        }
        return args[1];
    }

    private static Path resolveNewOutputDir(String value) {
        Path projectDir = Path.of("").toAbsolutePath().normalize();
        Path matrixRoot = projectDir.resolve("build/tool-search-matrix").normalize();
        Path output = projectDir.resolve(value).normalize();
        if (!output.startsWith(matrixRoot)) {
            throw new IllegalArgumentException("Output must be under build/tool-search-matrix/");
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output directory already exists: " + output);
        }
        return output;
    }

    private static Path singleRawJson(Path outputDir) throws Exception {
        try (var paths = Files.list(outputDir)) {
            List<Path> raw = paths.filter(path -> path.getFileName().toString().endsWith("-tool-calling-comparison.json"))
                    .toList();
            if (raw.size() != 1) {
                throw new IllegalStateException("Expected exactly one raw comparison JSON, found " + raw.size());
            }
            return raw.getFirst();
        }
    }
}
