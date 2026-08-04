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

public final class LocalEvaluationRunner {

    private LocalEvaluationRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalEvaluationPreflight preflight = new LocalEvaluationPreflight();
        LocalEvaluationPreflight.Prepared prepared = preflight.prepare(
                new LocalEvaluationPreflight.Input(
                        Path.of(""),
                        parsed.ollamaBaseUrl(),
                        parsed.judgeModel(),
                        parsed.maxTokens(),
                        parsed.timeout(),
                        parsed.outputDirectory()),
                () -> LocalEvaluationContract.load(objectMapper),
                LiveJudgeSession::new);

        EvidenceCodeBaseline codeBaseline = EvidenceProvenance.captureCodeBaseline(Path.of(""));
        printProtocol(prepared, codeBaseline);
        Path outputDirectory = LocalEvaluationPreflight.allocate(prepared);
        try {
            LocalEvaluationResult result = new LocalEvaluationExecutor().execute(prepared);
            new LocalEvaluationEvidence(
                    objectMapper,
                    prepared.contract().prompt(),
                    prepared.contract().catalog(),
                    prepared.contract().review())
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Local fact-check evaluation complete: "
                    + outputDirectory.resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Local fact-check evaluation failed after output allocation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    private static void printProtocol(
            LocalEvaluationPreflight.Prepared prepared,
            EvidenceCodeBaseline codeBaseline
    ) {
        System.out.println("Starting opt-in local fact-check evaluation");
        System.out.println("  Judge: " + prepared.modelIdentity().normalizedInstalledName());
        System.out.println("  Judge digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Rows: 12 sequential (6 fixtures x 2 repetitions)");
        System.out.println("  Temperature/seeds: 0.0 / [42, 43]");
        System.out.println("  Max tokens: " + prepared.settings().maxTokens());
        System.out.println("  Timeout: " + Duration.ofMillis(prepared.settings().timeoutMillis()));
        System.out.println("  Attempts: exactly 1 per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Evidence status: " + (codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final (dirty working tree)"
                : "clean-baseline candidate"));
        System.out.println("  Output: " + prepared.outputDirectory());
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
            String maxTokens,
            String timeout,
            String outputDirectory
    ) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 10) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of(
                    "--ollama-base-url",
                    "--judge-model",
                    "--max-tokens",
                    "--timeout",
                    "--output-dir");
            for (int index = 0; index < values.size(); index += 2) {
                if (!supported.contains(values.get(index))) {
                    throw usage();
                }
            }
            if (supported.stream().anyMatch(option -> values.stream().filter(option::equals).count() != 1)) {
                throw usage();
            }
            return new Arguments(
                    value(values, "--ollama-base-url"),
                    value(values, "--judge-model"),
                    value(values, "--max-tokens"),
                    value(values, "--timeout"),
                    value(values, "--output-dir"));
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
                    "Expected --ollama-base-url <explicit-loopback-url> --judge-model <installed-tag> "
                            + "--max-tokens <1..32768> --timeout <ISO-8601-duration-up-to-PT10M> "
                            + "--output-dir <new-dated-build-directory>");
        }
    }
}
