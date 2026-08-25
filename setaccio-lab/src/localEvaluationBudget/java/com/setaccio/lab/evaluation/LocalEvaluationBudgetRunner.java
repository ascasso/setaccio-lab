package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;

/** Explicit opt-in entry point for the fresh F1 64/256-token pair. */
public final class LocalEvaluationBudgetRunner {

    private LocalEvaluationBudgetRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        LocalEvaluationBudgetPreflight.Prepared prepared = new LocalEvaluationBudgetPreflight().prepare(
                new LocalEvaluationBudgetPreflight.Input(
                        projectDirectory,
                        parsed.ollamaBaseUrl(),
                        parsed.judgeModel(),
                        parsed.outputDirectory64(),
                        parsed.outputDirectory256()),
                () -> LocalEvaluationContract.load(objectMapper),
                EvidenceProvenance::captureCodeBaseline,
                LiveJudgeSession::new);

        printProtocol(prepared);
        prepared.requireRepositoryUnchanged();
        prepared.requireModelIdentityUnchanged();
        LocalEvaluationBudgetPreflight.AllocatedOutputs outputs = prepared.allocateBoth();
        EvidenceCodeBaseline baseline = prepared.codeBaseline();
        LocalEvaluationEvidence evidence = new LocalEvaluationEvidence(
                objectMapper,
                prepared.contract().prompt(),
                prepared.contract().catalog(),
                prepared.contract().review());
        try {
            runArm(prepared, evidence, baseline, 64, outputs.budget64());
            prepared.requireRepositoryUnchanged();
            prepared.requireModelIdentityUnchanged();
            runArm(prepared, evidence, baseline, 256, outputs.budget256());
            prepared.requireRepositoryUnchanged();
            System.out.println("F1 fresh fact-check budget pair complete:");
            System.out.println("  64 tokens: "
                    + outputs.budget64().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
            System.out.println("  256 tokens: "
                    + outputs.budget256().resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "F1 budget pair failed after output allocation; retained incomplete diagnostic directories: "
                            + outputs.budget64() + " and " + outputs.budget256(),
                    exception);
        }
    }

    private static void runArm(
            LocalEvaluationBudgetPreflight.Prepared prepared,
            LocalEvaluationEvidence evidence,
            EvidenceCodeBaseline baseline,
            int maxTokens,
            Path outputDirectory
    ) {
        prepared.requireRepositoryUnchanged();
        LocalEvaluationResult result = new LocalEvaluationExecutor()
                .execute(prepared.arm(maxTokens, outputDirectory));
        prepared.requireRepositoryUnchanged();
        evidence.write(outputDirectory, result, baseline);
        prepared.requireRepositoryUnchanged();
    }

    private static void printProtocol(LocalEvaluationBudgetPreflight.Prepared prepared) {
        System.out.println("Starting opt-in F1 local fact-check output-budget pair");
        System.out.println("  Judge: " + prepared.modelIdentity().normalizedInstalledName());
        System.out.println("  Judge digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Rows per arm: " + LocalEvaluationBudgetProtocol.ROW_COUNT
                + " sequential (6 fixtures x 2 repetitions)");
        System.out.println("  Prompt/catalog/review: "
                + prepared.contract().prompt().id() + "/"
                + prepared.contract().catalog().id() + "/"
                + prepared.contract().review().id());
        System.out.println("  Temperature/seeds: " + LocalEvaluationBudgetProtocol.TEMPERATURE
                + " / " + LocalEvaluationBudgetProtocol.SEEDS);
        System.out.println("  Maximum output tokens: " + LocalEvaluationBudgetProtocol.MAX_TOKENS);
        System.out.println("  Timeout: " + LocalEvaluationBudgetProtocol.TIMEOUT);
        System.out.println("  Attempts: exactly " + LocalEvaluationBudgetProtocol.MAX_ATTEMPTS + " per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + prepared.codeBaseline().gitCommit());
        System.out.println("  64-token output: " + prepared.outputDirectory64());
        System.out.println("  256-token output: " + prepared.outputDirectory256());
    }

    private static final class LiveJudgeSession implements LocalEvaluationPreflight.JudgeSession {

        private final LocalFactCheckJudgeModelFactory modelFactory = new LocalFactCheckJudgeModelFactory();
        private final OllamaApi ollamaApi;
        private final Map<LocalFactCheckJudgeSettings, ChatModel> models = new LinkedHashMap<>();

        private LiveJudgeSession(String baseUrl, Duration timeout) {
            ollamaApi = modelFactory.createApi(baseUrl, timeout);
        }

        @Override
        public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
            return LocalEvaluationModelInventory.requireInstalled(ollamaApi.listModels(), requestedModel);
        }

        @Override
        public LocalFactCheckJudgeResult evaluate(
                LocalFactCheckFixture fixture,
                LocalFactCheckJudgeSettings settings,
                LocalFactCheckPromptDefinition prompt
        ) {
            ChatModel model = models.computeIfAbsent(
                    settings,
                    key -> modelFactory.create(ollamaApi, key));
            return new LocalFactCheckJudgeBoundary(model, settings, prompt).evaluate(fixture);
        }
    }

    record Arguments(
            String ollamaBaseUrl,
            String judgeModel,
            String outputDirectory64,
            String outputDirectory256
    ) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 8) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of(
                    "--ollama-base-url",
                    "--judge-model",
                    "--output-dir-64",
                    "--output-dir-256");
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
                    value(values, "--ollama-base-url"),
                    value(values, "--judge-model"),
                    value(values, "--output-dir-64"),
                    value(values, "--output-dir-256"));
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
                    "Expected --ollama-base-url <explicit-loopback-url> "
                            + "--judge-model <installed-tag> "
                            + "--output-dir-64 <new-dated-build-directory> "
                            + "--output-dir-256 <new-dated-build-directory>; "
                            + "F1 fixes max tokens to 64 and 256 and timeout to PT2M");
        }
    }
}
