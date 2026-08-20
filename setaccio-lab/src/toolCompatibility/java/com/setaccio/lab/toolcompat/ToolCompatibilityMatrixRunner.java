package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;

/** Explicit opt-in entry point for the locked local Phase 1 matrix. */
public final class ToolCompatibilityMatrixRunner {

    private ToolCompatibilityMatrixRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ToolCompatibilityPreflight.Prepared prepared = new ToolCompatibilityPreflight().prepare(
                new ToolCompatibilityPreflight.Input(
                        Path.of(""),
                        parsed.ollamaBaseUrl(),
                        parsed.model(),
                        parsed.maxTokens(),
                        parsed.timeout(),
                        parsed.outputDirectory()),
                LiveSession::new);

        EvidenceCodeBaseline codeBaseline = EvidenceProvenance.captureCodeBaseline(Path.of(""));
        printProtocol(prepared, codeBaseline);
        Path outputDirectory = ToolCompatibilityPreflight.allocate(prepared.outputDirectory());
        try {
            ToolCompatibilityResult result = new ToolCompatibilityMatrixExecutor().execute(prepared);
            new ToolCompatibilityEvidence(objectMapper).write(
                    outputDirectory, result, codeBaseline);
            System.out.println("Tool compatibility matrix complete: "
                    + outputDirectory.resolve(ToolCompatibilityEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Tool compatibility matrix failed after output allocation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    private static void printProtocol(
            ToolCompatibilityPreflight.Prepared prepared,
            EvidenceCodeBaseline codeBaseline
    ) {
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        System.out.println("Starting opt-in Ollama tool compatibility matrix");
        System.out.println("  Model: " + prepared.modelIdentity().effectiveModel());
        System.out.println("  Model digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Rows: 16 sequential (8 cases x 2 repetitions)");
        System.out.println("  Case oracle: " + oracle.id() + " v" + oracle.version()
                + " / " + oracle.sha256());
        System.out.println("  System prompt: untreated empty prompt");
        System.out.println("  Temperature/seeds: 0.0 / [42, 43]");
        System.out.println("  Maximum output tokens per provider turn: "
                + prepared.settings().maxOutputTokensPerProviderTurn());
        System.out.println("  Logical row deadline: "
                + Duration.ofMillis(prepared.settings().rowTimeoutMillis()));
        System.out.println("  Attempts: exactly 1 logical attempt per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Evidence status: " + (codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final (dirty working tree)"
                : "clean-baseline candidate"));
        System.out.println("  Output: " + prepared.outputDirectory());
    }

    private static final class LiveSession implements ToolCompatibilityPreflight.Session {

        private final OllamaApi ollamaApi;
        private OllamaApi.ListModelResponse installedModels;
        private ToolCompatibilityModelIdentity modelIdentity;

        private LiveSession(String baseUrl, Duration timeout) {
            if (!ToolCompatibilityProtocol.ROW_TIMEOUT.equals(timeout)) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Live session timeout drifted from the locked row deadline");
            }
            ollamaApi = ToolCompatibilityInvocationBoundary.createControlledOllamaApi(baseUrl);
        }

        @Override
        public ToolCompatibilityModelIdentity requireInstalled(String requestedModel) {
            installedModels = ollamaApi.listModels();
            modelIdentity = ToolCompatibilityModelInventory.requireInstalled(
                    installedModels, requestedModel);
            return modelIdentity;
        }

        @Override
        public ToolCompatibilityControlledOllamaModel controlledModel(int seed) {
            if (installedModels == null || modelIdentity == null) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Installed model identity must be resolved before row execution");
            }
            ToolCompatibilityControlledOllamaModel controlled =
                    ToolCompatibilityInvocationBoundary.createControlledOllamaModel(
                            ollamaApi, installedModels, seed);
            if (!modelIdentity.equals(controlled.modelIdentity())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Controlled model identity changed after preflight");
            }
            return controlled;
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
            if (supported.stream().anyMatch(option ->
                    values.stream().filter(option::equals).count() != 1)) {
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
                    "Expected --ollama-base-url <explicit-loopback-url> "
                            + "--model <locked-installed-tag> --max-tokens 512 "
                            + "--timeout PT2M --output-dir <new-dated-build-directory>");
        }
    }
}
