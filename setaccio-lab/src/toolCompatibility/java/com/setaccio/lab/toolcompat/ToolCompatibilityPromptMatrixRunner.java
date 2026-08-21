package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceProvenance;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;

/** Explicit opt-in entry point for the one locked interleaved Phase 2 prompt matrix. */
public final class ToolCompatibilityPromptMatrixRunner {

    private ToolCompatibilityPromptMatrixRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ToolCompatibilityPromptMatrixPreflight.Prepared prepared =
                new ToolCompatibilityPromptMatrixPreflight().prepare(
                        new ToolCompatibilityPromptMatrixPreflight.Input(
                                Path.of(""),
                                parsed.ollamaBaseUrl(),
                                parsed.model(),
                                parsed.maxTokens(),
                                parsed.timeout(),
                                parsed.baselineOutputDirectory(),
                                parsed.candidateOutputDirectory()),
                        EvidenceProvenance::captureCodeBaseline,
                        LiveSession::new);

        printProtocol(prepared);
        ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs outputs =
                ToolCompatibilityPromptMatrixPreflight.allocateBoth(prepared);
        try {
            ToolCompatibilityPromptMatrixEvidence evidence =
                    new ToolCompatibilityPromptMatrixEvidence(objectMapper);
            new ToolCompatibilityPromptMatrixOrchestrator().executeAndWrite(
                    prepared, outputs, evidence);
            System.out.println("Tool compatibility prompt matrix complete:");
            System.out.println("  Untreated: " + outputs.baseline().resolve(
                    ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME));
            System.out.println("  Prompted: " + outputs.candidate().resolve(
                    ToolCompatibilityPromptMatrixEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Tool compatibility prompt matrix failed after paired output allocation; "
                            + "retained incomplete diagnostic directories: "
                            + outputs.baseline() + " and " + outputs.candidate(),
                    exception);
        }
    }

    private static void printProtocol(ToolCompatibilityPromptMatrixPreflight.Prepared prepared) {
        ToolCompatibilityCaseOracle oracle = ToolCompatibilityProtocol.caseOracle();
        ToolCompatibilityPairedSchedule schedule = prepared.pairedSchedule();
        System.out.println("Starting opt-in Ollama paired tool compatibility prompt matrix");
        System.out.println("  Model: " + prepared.modelIdentity().effectiveModel());
        System.out.println("  Model digest: " + prepared.modelIdentity().digest());
        System.out.println("  Endpoint category: local loopback");
        System.out.println("  Rows: 32 sequential interleaved (16 untreated, 16 prompted)");
        System.out.println("  Paired schedule: " + schedule.id() + " v" + schedule.version()
                + " / " + schedule.sha256());
        System.out.println("  Case oracle: " + oracle.id() + " v" + oracle.version()
                + " / " + oracle.sha256());
        System.out.println("  Prompt conditions: untreated, prompted");
        System.out.println("  Temperature/seeds: 0.0 / [42, 43]");
        System.out.println("  Maximum output tokens per provider turn: "
                + prepared.settings().maxOutputTokensPerProviderTurn());
        System.out.println("  Logical row deadline: "
                + Duration.ofMillis(prepared.settings().rowTimeoutMillis()));
        System.out.println("  Attempts: exactly 1 logical attempt per row");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + prepared.codeBaseline().gitCommit());
        System.out.println("  Baseline output: " + prepared.baselineOutputDirectory());
        System.out.println("  Candidate output: " + prepared.candidateOutputDirectory());
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
                        "Controlled model identity changed after prompt-matrix preflight");
            }
            return controlled;
        }
    }

    record Arguments(
            String ollamaBaseUrl,
            String model,
            String maxTokens,
            String timeout,
            String baselineOutputDirectory,
            String candidateOutputDirectory
    ) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 12) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of(
                    "--ollama-base-url",
                    "--model",
                    "--max-tokens",
                    "--timeout",
                    "--baseline-output-dir",
                    "--candidate-output-dir");
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
                    value(values, "--baseline-output-dir"),
                    value(values, "--candidate-output-dir"));
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
                            + "--model <locked-installed-tag> --max-tokens 512 --timeout PT2M "
                            + "--baseline-output-dir <new-dated-build-directory> "
                            + "--candidate-output-dir <new-dated-build-directory>");
        }
    }
}
