package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.lab.LabApplication;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import com.setaccio.lab.service.VisionModelInvoker;
import com.setaccio.lab.service.VisionPromptCatalog;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class VisionMatrixRunner {

    private static final String PULL_STRATEGY_PROPERTY = "spring.ai.ollama.init.pull-model-strategy";

    private VisionMatrixRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        Path corpusDirectory = resolveCorpusDirectory(parsed.corpusDirectory());
        Path outputDirectory = resolveNewOutputDirectory(parsed.outputDirectory());
        VisionMatrixRunSettings settings = VisionMatrixProtocol.settings(
                parseModels(parsed.models()),
                parseMaxTokens(parsed.maxTokens()));
        ObjectMapper offlineObjectMapper = JsonMapper.builder().findAndAddModules().build();
        LoadedVisionCorpus corpus = new VisionCorpusReader(
                offlineObjectMapper,
                new ApacheCommonsBlake3HashingServiceImpl())
                .read(corpusDirectory);

        System.out.println("Starting sequential vision matrix");
        System.out.println("  Models: " + settings.models());
        System.out.println("  Cases: " + corpus.cases().stream()
                .map(loadedCase -> loadedCase.metadata().caseId())
                .toList());
        System.out.println("  Repetitions/seeds: 2 / [42, 43]");
        System.out.println("  Temperature: 0.0");
        System.out.println("  Token policy: "
                + (settings.maxTokens() == null ? "no explicit limit" : settings.maxTokens()));
        System.out.println("  Prompt version: " + parsed.promptVersion());
        System.out.println("  Execution: sequential");
        System.out.println("  Output: " + outputDirectory);
        System.out.println("  Ollama pull strategy: never");

        try {
            VisionMatrixResult result;
            ObjectMapper objectMapper;
            VisionPromptDefinition promptDefinition;
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LabApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("local")
                    .run(
                            "--" + PULL_STRATEGY_PROPERTY + "=never",
                            "--spring.ai.model.chat=ollama")) {
                String pullStrategy = context.getEnvironment().getProperty(PULL_STRATEGY_PROPERTY);
                if (!VisionMatrixProtocol.PULL_MODEL_STRATEGY.equalsIgnoreCase(pullStrategy)) {
                    throw new IllegalStateException(
                            "Effective Ollama pull strategy is not never: " + pullStrategy);
                }
                objectMapper = context.getBean(ObjectMapper.class);
                promptDefinition = context.getBean(VisionPromptCatalog.class).require(parsed.promptVersion());
                List<VisionMatrixModelIdentity> modelIdentities =
                        requireInstalledModels(context.getBean(OllamaApi.class), settings.models());
                EvidenceRunDirectory.createNamed(
                        outputDirectory.getParent(),
                        outputDirectory.getFileName().toString());
                VisionModelInvoker invoker = context.getBean(VisionModelInvoker.class);
                result = new VisionMatrixExecutor(
                                (image, invocationSettings) -> invoker.invoke(
                                        image, invocationSettings, promptDefinition),
                                promptDefinition)
                        .execute(corpus, settings, modelIdentities);
            }

            VisionMatrixAnalyzer.MatrixAnalysis analysis =
                    new VisionMatrixAnalyzer(promptDefinition).analyze(result);
            new VisionMatrixEvidence(objectMapper, promptDefinition)
                    .write(outputDirectory, result, analysis);
            if (!analysis.valid()) {
                analysis.integrityFailures()
                        .forEach(failure -> System.err.println("INTEGRITY: " + failure));
                throw new IllegalStateException(
                        "Vision matrix failed " + analysis.integrityFailures().size()
                                + " integrity check(s); artifacts were retained for diagnosis.");
            }
            System.out.println("Vision matrix complete: "
                    + outputDirectory.resolve(VisionMatrixEvidence.SUMMARY_FILENAME));
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Vision matrix failed: " + e.getMessage(), e);
        }
    }

    static List<String> parseModels(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Models must not be blank.");
        }
        List<String> models = List.of(value.split(",", -1)).stream()
                .map(String::trim)
                .toList();
        if (models.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Models must not contain blank entries.");
        }
        if (new LinkedHashSet<>(models).size() != models.size()) {
            throw new IllegalArgumentException("Models must not contain duplicates.");
        }
        return models;
    }

    static Integer parseMaxTokens(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token policy must be none or an integer.");
        }
        if ("none".equals(value.toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            int maxTokens = Integer.parseInt(value);
            if (maxTokens < 1 || maxTokens > 32768) {
                throw new IllegalArgumentException("max-tokens must be between 1 and 32768.");
            }
            return maxTokens;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Token policy must be none or an integer.", e);
        }
    }

    static List<VisionMatrixModelIdentity> requireInstalledModels(
            OllamaApi ollamaApi,
            List<String> requestedModels) {
        OllamaApi.ListModelResponse response = ollamaApi.listModels();
        Map<String, OllamaApi.Model> installed = new LinkedHashMap<>();
        if (response != null && response.models() != null) {
            for (OllamaApi.Model model : response.models()) {
                if (model != null && model.name() != null) {
                    installed.put(normalizeModelTag(model.name()), model);
                }
            }
        }
        List<String> missing = requestedModels.stream()
                .map(VisionMatrixRunner::normalizeModelTag)
                .filter(model -> !installed.containsKey(model))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested Ollama models are not installed: " + missing);
        }
        List<VisionMatrixModelIdentity> identities = requestedModels.stream()
                .map(requestedModel -> {
                    String resolvedModel = normalizeModelTag(requestedModel);
                    OllamaApi.Model installedModel = installed.get(resolvedModel);
                    try {
                        return new VisionMatrixModelIdentity(
                                requestedModel,
                                resolvedModel,
                                installedModel.digest());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "Installed Ollama model identity is incomplete for "
                                        + resolvedModel + ": " + e.getMessage(),
                                e);
                    }
                })
                .toList();
        Set<String> digests = new HashSet<>();
        List<String> duplicateDigests = identities.stream()
                .filter(identity -> !digests.add(identity.digest()))
                .map(VisionMatrixModelIdentity::requestedModel)
                .toList();
        if (!duplicateDigests.isEmpty()) {
            throw new IllegalArgumentException(
                    "Requested Ollama model tags resolve to duplicate model digests: "
                            + duplicateDigests);
        }
        return identities;
    }

    static String normalizeModelTag(String model) {
        return VisionMatrixProtocol.normalizeModelTag(model);
    }

    static Path resolveCorpusDirectory(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path expected = projectDirectory.resolve("local/vision-corpus").normalize();
        Path actual = projectDirectory.resolve(value).normalize();
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(
                    "Corpus directory must be the fixed local/vision-corpus directory.");
        }
        return actual;
    }

    static Path resolveNewOutputDirectory(String value) {
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path matrixRoot = projectDirectory.resolve("build/vision-matrix").normalize();
        Path output = projectDirectory.resolve(value).normalize();
        if (!matrixRoot.equals(output.getParent())) {
            throw new IllegalArgumentException(
                    "Output must be one new directory directly under build/vision-matrix/.");
        }
        String name = output.getFileName().toString();
        if (!name.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory name must contain a YYYY-MM-DD date.");
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output directory already exists: " + output);
        }
        return output;
    }

    private record Arguments(
            String corpusDirectory,
            String models,
            String maxTokens,
            String outputDirectory,
            String promptVersion
    ) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 10) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            return new Arguments(
                    value(values, "--corpus-dir"),
                    value(values, "--models"),
                    value(values, "--max-tokens"),
                    value(values, "--output-dir"),
                    value(values, "--prompt-version"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1 || args.get(index + 1).isBlank()) {
                throw usage();
            }
            return args.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --corpus-dir <local/vision-corpus> --models <tags> "
                            + "--max-tokens <none|1..32768> --output-dir <dated-build-directory> "
                            + "--prompt-version <supported-version>");
        }
    }
}
