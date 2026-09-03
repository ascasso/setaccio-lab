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

/** Explicit opt-in entry point for one fresh five-arm Phase 4 breakpoint study. */
public final class LocalEvaluationBreakpointRunner {

    private LocalEvaluationBreakpointRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        LocalEvaluationBreakpointPreflight.Prepared prepared = new LocalEvaluationBreakpointPreflight().prepare(
                new LocalEvaluationBreakpointPreflight.Input(
                        projectDirectory, parsed.ollamaBaseUrl(), parsed.judgeModel(), parsed.outputDirectories()),
                () -> LocalEvaluationContract.load(objectMapper),
                EvidenceProvenance::captureCodeBaseline,
                LiveJudgeSession::new);
        printProtocol(prepared);
        prepared.requireRepositoryUnchanged();
        prepared.requireModelIdentityUnchanged();
        Map<Integer, Path> outputs = prepared.allocateAll();
        LocalEvaluationBreakpointEvidence evidence = new LocalEvaluationBreakpointEvidence(
                objectMapper, prepared.contract().prompt(), prepared.contract().catalog(), prepared.contract().review());
        try {
            for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
                prepared.requireRepositoryUnchanged();
                prepared.requireModelIdentityUnchanged();
                LocalEvaluationResult result = new LocalEvaluationExecutor().execute(prepared.arm(
                        maxTokens, outputs.get(maxTokens)));
                prepared.requireRepositoryUnchanged();
                evidence.writeArm(outputs.get(maxTokens), result, prepared.codeBaseline());
                prepared.requireRepositoryUnchanged();
            }
            prepared.requireModelIdentityUnchanged();
            LocalEvaluationBreakpointEvidence.OfflineStudyResult verification = evidence.verifyStudy(outputs);
            if (!verification.valid()) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Generated breakpoint study failed verification: " + String.join(" ", verification.failures()));
            }
            System.out.println("Phase 4 breakpoint study complete:");
            for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
                System.out.println("  " + maxTokens + " tokens: "
                        + outputs.get(maxTokens).resolve(LocalEvaluationEvidence.SUMMARY_FILENAME));
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Breakpoint study failed after output allocation; retained diagnostic directories: " + outputs.values(),
                    exception);
        }
    }

    private static void printProtocol(LocalEvaluationBreakpointPreflight.Prepared prepared) {
        System.out.println("Starting opt-in Phase 4 fact-check output-budget breakpoint study");
        System.out.println("  Judge: " + prepared.modelIdentity().normalizedInstalledName());
        System.out.println("  Judge digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Token arms: " + LocalEvaluationBreakpointProtocol.MAX_TOKENS);
        System.out.println("  Rows per arm: " + LocalEvaluationBreakpointProtocol.ROW_COUNT
                + " sequential (6 fixtures x 2 repetitions); total 60 rows");
        System.out.println("  Temperature/seeds: " + LocalEvaluationBreakpointProtocol.TEMPERATURE
                + " / " + LocalEvaluationBreakpointProtocol.SEEDS);
        System.out.println("  Timeout: " + LocalEvaluationBreakpointProtocol.TIMEOUT);
        System.out.println("  Attempts: exactly " + LocalEvaluationBreakpointProtocol.MAX_ATTEMPTS + " per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + prepared.codeBaseline().gitCommit());
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
            ChatModel model = models.computeIfAbsent(settings, key -> modelFactory.create(ollamaApi, key));
            return new LocalFactCheckJudgeBoundary(model, settings, prompt).evaluate(fixture);
        }
    }

    record Arguments(String ollamaBaseUrl, String judgeModel, Map<Integer, String> outputDirectories) {

        static Arguments parse(String[] args) {
            int optionCount = 2 + LocalEvaluationBreakpointProtocol.MAX_TOKENS.size();
            if (args == null || args.length != optionCount * 2) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = new ArrayList<>(List.of("--ollama-base-url", "--judge-model"));
            LocalEvaluationBreakpointProtocol.MAX_TOKENS.forEach(tokens -> supported.add("--output-dir-" + tokens));
            for (int index = 0; index < values.size(); index += 2) {
                if (!supported.contains(values.get(index))) {
                    throw usage();
                }
            }
            if (supported.stream().anyMatch(option -> values.stream().filter(option::equals).count() != 1)) {
                throw usage();
            }
            Map<Integer, String> outputs = new LinkedHashMap<>();
            for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
                outputs.put(tokens, value(values, "--output-dir-" + tokens));
            }
            return new Arguments(value(values, "--ollama-base-url"), value(values, "--judge-model"), Map.copyOf(outputs));
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
                            + "--output-dir-64 <new-dated-evidence-directory> "
                            + "--output-dir-96 <new-dated-evidence-directory> "
                            + "--output-dir-128 <new-dated-evidence-directory> "
                            + "--output-dir-192 <new-dated-evidence-directory> "
                            + "--output-dir-256 <new-dated-evidence-directory>");
        }
    }
}
