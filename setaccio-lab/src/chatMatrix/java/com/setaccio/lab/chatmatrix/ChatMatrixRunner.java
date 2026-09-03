package com.setaccio.lab.chatmatrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatInvocationRequest;
import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

public final class ChatMatrixRunner {

    private ChatMatrixRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ChatMatrixPreflight preflight = new ChatMatrixPreflight();
        ChatMatrixPreflight.Prepared prepared = preflight.prepare(
                new ChatMatrixPreflight.Input(
                        Path.of(""),
                        parsed.ollamaBaseUrl(),
                        parsed.model(),
                        parsed.maxTokens(),
                        parsed.timeout(),
                        parsed.outputDirectory()),
                () -> ChatPromptCatalog.load(objectMapper),
                LiveSession::new);

        EvidenceCodeBaseline codeBaseline = EvidenceProvenance.captureCodeBaseline(Path.of(""));
        printProtocol(prepared, codeBaseline);
        Path outputDirectory = ChatMatrixPreflight.allocate(prepared);
        try {
            ChatMatrixResult result = new ChatMatrixExecutor().execute(prepared);
            new ChatMatrixEvidence(objectMapper, prepared.catalog())
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Ollama chat matrix complete: "
                    + outputDirectory.resolve(ChatMatrixEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Ollama chat matrix failed after output allocation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    private static void printProtocol(
            ChatMatrixPreflight.Prepared prepared,
            EvidenceCodeBaseline codeBaseline
    ) {
        System.out.println("Starting opt-in Ollama chat matrix");
        System.out.println("  Model: " + prepared.modelIdentity().effectiveModel());
        System.out.println("  Model digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Rows: 6 sequential (3 prompts x 2 repetitions)");
        System.out.println("  Prompt catalog: " + prepared.catalog().id()
                + " v" + prepared.catalog().version()
                + " / " + prepared.catalog().sha256());
        System.out.println("  Temperature/seeds: 0.0 / [42, 43]");
        System.out.println("  Max output tokens: " + prepared.settings().maxOutputTokens());
        System.out.println("  Timeout: " + Duration.ofMillis(prepared.settings().timeoutMillis()));
        System.out.println("  Attempts: exactly 1 per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Evidence status: " + (codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final (dirty working tree)"
                : "clean-baseline candidate"));
        System.out.println("  Output: " + prepared.outputDirectory());
    }

    private static final class LiveSession implements ChatMatrixPreflight.Session {

        private final OllamaChatModelFactory modelFactory = new OllamaChatModelFactory();
        private final OllamaApi ollamaApi;
        private final Map<ChatGenerationSettings, ChatInvocation> invocations = new LinkedHashMap<>();

        private LiveSession(String baseUrl, Duration timeout) {
            ollamaApi = modelFactory.createApi(baseUrl, timeout);
        }

        @Override
        public OllamaChatModelIdentity requireInstalled(String requestedModel) {
            return ChatMatrixModelInventory.requireInstalled(ollamaApi.listModels(), requestedModel);
        }

        @Override
        public ChatInvocationOutcome invoke(
                ChatPromptCase prompt,
                OllamaChatModelIdentity modelIdentity,
                ChatGenerationSettings settings
        ) {
            ChatInvocation invocation = invocations.computeIfAbsent(
                    settings,
                    key -> modelFactory.createInvocation(ollamaApi, modelIdentity, key));
            return invocation.invoke(new ChatInvocationRequest(
                    modelIdentity,
                    prompt.invocationPrompt(),
                    settings));
        }
    }

    record Arguments(
            String ollamaBaseUrl,
            String model,
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
                    "--model",
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
                    value(values, "--model"),
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
                    "Expected --ollama-base-url <explicit-loopback-url> --model <installed-tag> "
                            + "--max-tokens <1..32768> --timeout <ISO-8601-duration-up-to-PT10M> "
                            + "--output-dir <new-dated-evidence-directory>");
        }
    }
}
