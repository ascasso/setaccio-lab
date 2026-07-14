package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.LabApplication;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import com.setaccio.lab.service.ToolBenchmarkService;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.toolsearch.ToolSearchTool;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class ToolSearchMatrixBaselineRunner {

    static final List<String> MODELS = List.of("gemma4:e2b", "granite4.1:3b", "qwen3.5:0.8b");
    static final List<String> CASE_IDS = List.of(
            "arithmetic-add",
            "catalog-lookup",
            "catalog-multi-step",
            "no-applicable-domain-tool",
            "deterministic-tool-failure");
    static final ToolBenchmarkRunSettings SETTINGS = new ToolBenchmarkRunSettings(
            2, 0.0, 42, null, ToolBenchmarkComparisonOrder.ALTERNATE);
    private static final String PULL_STRATEGY_PROPERTY = "spring.ai.ollama.init.pull-model-strategy";
    private static final String BASELINE_RESOURCE = "/baselines/2026-07-12-tool-search-matrix.json";

    private ToolSearchMatrixBaselineRunner() {}

    public static void main(String[] args) {
        Path outputDir = resolveNewOutputDir(parseOutputDir(args));
        List<ToolBenchmarkPrompt> prompts = canonicalPrompts();
        List<String> tools = ToolBenchmarkCases.toolNames();
        validateCanonicalFailureMarker(prompts);

        System.out.println("Starting locked post-fix Tool Search matrix baseline");
        System.out.println("  Models: " + MODELS);
        System.out.println("  Cases: " + CASE_IDS);
        System.out.println("  Repetitions/seeds: 2 / [42, 43]");
        System.out.println("  Temperature/order: 0.0 / alternate paired sequential");
        System.out.println("  Output: " + outputDir);
        System.out.println("  Ollama pull strategy: never");

        try {
            Files.createDirectories(outputDir);
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
            JsonNode july12 = loadJuly12Baseline(objectMapper);
            String rawSha256 = sha256(Files.readAllBytes(rawJson));
            String expectationSha256 = sha256(objectMapper.writeValueAsBytes(prompts));
            writeManifest(objectMapper, outputDir, rawJson, rawSha256, expectationSha256, prompts, tools, result);
            Files.writeString(outputDir.resolve("SUMMARY.md"),
                    renderSummary(july12, analysis, rawJson.getFileName().toString(), rawSha256),
                    StandardCharsets.UTF_8);

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
        Map<String, ToolBenchmarkPrompt> byId = new LinkedHashMap<>();
        ToolBenchmarkCases.defaults().forEach(prompt -> byId.put(prompt.id(), prompt));
        List<ToolBenchmarkPrompt> prompts = new ArrayList<>();
        for (String caseId : CASE_IDS) {
            ToolBenchmarkPrompt prompt = byId.get(caseId);
            if (prompt == null) {
                throw new IllegalStateException("Canonical case is missing: " + caseId);
            }
            prompts.add(prompt);
        }
        return List.copyOf(prompts);
    }

    private static void validateCanonicalFailureMarker(List<ToolBenchmarkPrompt> prompts) {
        ToolBenchmarkPrompt failure = prompts.stream()
                .filter(prompt -> "deterministic-tool-failure".equals(prompt.id()))
                .findFirst().orElseThrow();
        if (!failure.expectation().requiredToolResponseTerms().equals(List.of(FailureBenchmarkTools.FAILURE_MARKER))) {
            throw new IllegalStateException("Deterministic failure expectation is not canonical.");
        }
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

    private static JsonNode loadJuly12Baseline(ObjectMapper objectMapper) throws Exception {
        try (InputStream input = ToolSearchMatrixBaselineRunner.class.getResourceAsStream(BASELINE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing July 12 baseline resource");
            }
            return objectMapper.readTree(input);
        }
    }

    private static void writeManifest(ObjectMapper objectMapper, Path outputDir, Path rawJson,
                                      String rawSha256, String expectationSha256,
                                      List<ToolBenchmarkPrompt> prompts, List<String> tools,
                                      ToolBenchmarkComparisonResult result) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", Instant.now().toString());
        manifest.put("gitCommit", gitCommit());
        manifest.put("workingTreeDirty", workingTreeDirty());
        manifest.put("springAiVersion", springAiVersion());
        manifest.put("issues", List.of(
                Map.of("number", 20, "effect", "ToolSearchResponse.toolReferences normalization fix"),
                Map.of("number", 21, "effect", "chat no-result correctness fix; not a direct tool scoring change")));
        manifest.put("models", MODELS);
        manifest.put("caseIds", CASE_IDS);
        manifest.put("prompts", prompts);
        manifest.put("toolNames", tools);
        manifest.put("runSettings", SETTINGS);
        manifest.put("executionStrategy", result.executionStrategy());
        manifest.put("toolSearchIndexType", result.toolSearchIndexType());
        manifest.put("ollamaBaseUrl", result.ollamaBaseUrl());
        manifest.put("pullModelStrategy", "never");
        manifest.put("canonicalExpectationSha256", expectationSha256);
        manifest.put("rawJson", rawJson.getFileName().toString());
        manifest.put("rawJsonSha256", rawSha256);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputDir.resolve("manifest.json").toFile(), manifest);
    }

    private static String renderSummary(JsonNode july12, ToolSearchMatrixAnalyzer.MatrixAnalysis analysis,
                                        String rawFile, String rawSha256) {
        StringBuilder out = new StringBuilder();
        out.append("# Post-Fix Tool Search Diagnostic Matrix Baseline\n\n");
        out.append("> **Confounder:** Pass-rate deltas are observational. This run constructs requests from canonical Java cases, while the July 12 raw request used an incorrect deterministic-failure marker. Issue #20 also changes object-response discovery normalization. Deltas cannot be attributed solely to model behavior or the fixes. Issue #21 affects chat result correctness and is not a direct tool-matrix scoring change.\n\n");
        out.append("- Raw trace: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Protocol: 3 models × 5 canonical cases × 2 repetitions × 2 advisors = 60 rows; temperature 0.0; seeds 42/43; alternate paired order.\n\n");
        out.append("## Pass-rate comparison\n\n");
        out.append("| Model | Advisor | July 12 recorded | July 12 corrected | Post-fix | Delta vs corrected |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (JsonNode baseline : july12.get("results")) {
            String model = baseline.get("model").asText();
            AdvisorMode mode = AdvisorMode.fromJson(baseline.get("advisor").asText());
            ToolSearchMatrixAnalyzer.GroupResult group = analysis.groups()
                    .get(new ToolSearchMatrixAnalyzer.GroupKey(model, mode));
            int postFix = group == null ? 0 : group.passed();
            int corrected = baseline.get("correctedPassed").asInt();
            int total = baseline.get("total").asInt();
            out.append("| `").append(model).append("` | ").append(mode.jsonValue()).append(" | ")
                    .append(baseline.get("recordedPassed").asInt()).append('/').append(total).append(" | ")
                    .append(corrected).append('/').append(total).append(" | ")
                    .append(postFix).append('/').append(total).append(" | ")
                    .append(String.format("%+d", postFix - corrected)).append(" |\n");
        }
        out.append("\n## Failure classification\n\n");
        out.append("| Model | Advisor");
        for (ToolSearchMatrixAnalyzer.FailureCategory category : ToolSearchMatrixAnalyzer.FailureCategory.values()) {
            out.append(" | ").append(category.label());
        }
        out.append(" |\n| --- | ---");
        for (int i = 0; i < ToolSearchMatrixAnalyzer.FailureCategory.values().length; i++) {
            out.append(" | ---:");
        }
        out.append(" |\n");
        for (String model : MODELS) {
            for (AdvisorMode mode : List.of(AdvisorMode.STANDARD, AdvisorMode.TOOL_SEARCH)) {
                ToolSearchMatrixAnalyzer.GroupResult group = analysis.groups()
                        .get(new ToolSearchMatrixAnalyzer.GroupKey(model, mode));
                out.append("| `").append(model).append("` | ").append(mode.jsonValue());
                for (ToolSearchMatrixAnalyzer.FailureCategory category : ToolSearchMatrixAnalyzer.FailureCategory.values()) {
                    out.append(" | ").append(group == null ? 0 : group.failures().getOrDefault(category, 0));
                }
                out.append(" |\n");
            }
        }
        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }

    private static String gitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? value : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String springAiVersion() {
        String version = ToolSearchTool.class.getPackage().getImplementationVersion();
        return version == null ? "2.0.0" : version;
    }

    private static boolean workingTreeDirty() {
        try {
            Process process = new ProcessBuilder("git", "status", "--porcelain").redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() != 0 || !value.isBlank();
        } catch (Exception e) {
            return true;
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
