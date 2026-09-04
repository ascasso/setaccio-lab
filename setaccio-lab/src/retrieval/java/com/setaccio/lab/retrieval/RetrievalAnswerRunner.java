package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;

/** Runs one explicit local R5 answer matrix from a verified saved R3 retrieval run. */
public final class RetrievalAnswerRunner {

    private RetrievalAnswerRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        Source source = loadVerifiedSource(parsed.sourceRetrievalRunDirectory(), objectMapper, inputs);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                EvidenceProvenance.captureCodeBaseline(Path.of("")));
        RetrievalAnswerPromptDefinition prompt = RetrievalAnswerPromptDefinition.load();
        RetrievalAnswerRunSettings settings = RetrievalAnswerProtocol.settings(
                parsed.seed(), parsed.maxOutputTokens(), parsed.timeout());
        OllamaChatModelFactory modelFactory = new OllamaChatModelFactory();
        OllamaApi ollamaApi = modelFactory.createApi(parsed.ollamaBaseUrl(), parsed.timeout());
        OllamaChatModelIdentity chatIdentity = RetrievalAnswerModelInventory.requireInstalled(
                ollamaApi.listModels(), parsed.answerModel());
        RetrievalAnswerModelIdentity modelIdentity = new RetrievalAnswerModelIdentity(
                chatIdentity.providerId(), chatIdentity.requestedModel(), chatIdentity.effectiveModel(), chatIdentity.digest());
        Path outputDirectory = resolveNewOutputDirectory(parsed.outputDirectory());
        EvidenceRunDirectory.createNamed(outputDirectory.getParent(), outputDirectory.getFileName().toString());

        printProtocol(source, prompt, modelIdentity, settings, codeBaseline, outputDirectory);
        try {
            ChatInvocation invocation = modelFactory.createInvocation(ollamaApi, chatIdentity, settings.chatSettings());
            RetrievalAnswerResult result = new RetrievalAnswerExecutor(invocation).execute(
                    source.provenance(), source.result(), prompt, modelIdentity, settings, chatIdentity);
            OllamaChatModelIdentity postRunIdentity = RetrievalAnswerModelInventory.requireInstalled(
                    ollamaApi.listModels(), parsed.answerModel());
            if (!chatIdentity.equals(postRunIdentity)) {
                throw new IllegalStateException("Ollama answer model identity changed during generation.");
            }
            requireSameCleanBaseline(codeBaseline, EvidenceProvenance.captureCodeBaseline(Path.of("")));
            new RetrievalAnswerEvidence(objectMapper, inputs.corpus(), inputs.catalog())
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Retrieval answer generation complete: "
                    + outputDirectory.resolve(RetrievalAnswerEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Retrieval answer generation failed after output reservation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    static Path resolveNewOutputDirectory(String value) {
        Path outputDirectory = RetrievalAnswerProtocol.EVIDENCE_ROOT.resolveNewRunDirectory(
                Path.of(""), value, "Output directory");
        if (!outputDirectory.getFileName().toString().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory must contain a YYYY-MM-DD date.");
        }
        return outputDirectory;
    }

    static Path resolveSourceRunDirectory(String value) {
        return RetrievalEvaluationProtocol.EVIDENCE_ROOT.requireSavedRunDirectory(
                Path.of(""), value, "Source retrieval run directory");
    }

    private static Source loadVerifiedSource(
            String sourceRunDirectory,
            ObjectMapper objectMapper,
            RetrievalEvaluationRunner.Inputs inputs
    ) {
        Path source = resolveSourceRunDirectory(sourceRunDirectory);
        RetrievalEvaluationEvidence retrievalEvidence = new RetrievalEvaluationEvidence(
                objectMapper, inputs.corpus(), inputs.catalog());
        RetrievalEvaluationEvidence.OfflineResult verification = retrievalEvidence.verify(source);
        if (!verification.valid()) {
            throw new IllegalArgumentException("Source retrieval evidence did not verify: "
                    + String.join(" ", verification.failures()));
        }
        EvidenceManifest manifest = new EvidenceManifestStore(objectMapper).read(source);
        requireCleanBaseline(manifest.codeBaseline());
        EvidenceArtifact rawArtifact = manifest.artifacts().stream()
                .filter(artifact -> RetrievalEvaluationEvidence.RAW_ROLE.equals(artifact.role()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Source retrieval manifest has no raw-result artifact."));
        Path rawPath = source.resolve(rawArtifact.path()).normalize();
        if (!rawPath.startsWith(source) || Files.isSymbolicLink(rawPath)) {
            throw new IllegalArgumentException("Source retrieval raw artifact is unsafe.");
        }
        try {
            RetrievalEvaluationResult result = objectMapper.readerFor(RetrievalEvaluationResult.class)
                    .readValue(rawPath.toFile());
            return new Source(new RetrievalAnswerSourceEvidence(
                    source.getFileName().toString(),
                    EvidenceIntegrity.sha256(rawPath),
                    EvidenceIntegrity.sha256(source.resolve(EvidenceManifestStore.MANIFEST_FILENAME)),
                    manifest.codeBaseline().gitCommit()), result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Source retrieval raw result could not be loaded.", exception);
        }
    }

    private static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline baseline) {
        if (baseline == null || baseline.workingTreeDirty() || baseline.gitCommit() == null
                || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("Formal retrieval answer generation requires a clean worktree at a full Git commit.");
        }
        return baseline;
    }

    private static void requireSameCleanBaseline(EvidenceCodeBaseline expected, EvidenceCodeBaseline observed) {
        if (!requireCleanBaseline(expected).gitCommit().equals(requireCleanBaseline(observed).gitCommit())) {
            throw new IllegalStateException("Git baseline changed during retrieval answer generation.");
        }
    }

    private static void printProtocol(
            Source source,
            RetrievalAnswerPromptDefinition prompt,
            RetrievalAnswerModelIdentity modelIdentity,
            RetrievalAnswerRunSettings settings,
            EvidenceCodeBaseline codeBaseline,
            Path outputDirectory
    ) {
        System.out.println("Starting opt-in local retrieval answer generation");
        System.out.println("  Verified R3 source: " + source.provenance().sourceRunId());
        System.out.println("  Rows: " + source.result().rows().size() + " sequential answers; retrieval is not re-run");
        System.out.println("  Prompt: " + prompt.contract().promptId() + " / " + prompt.contract().promptSha256());
        System.out.println("  Answer model: requested " + modelIdentity.requestedModel()
                + ", effective " + modelIdentity.effectiveModel());
        System.out.println("  Ollama digest: " + modelIdentity.digest());
        System.out.println("  Temperature / seed / max output: " + settings.temperature() + " / "
                + settings.seed() + " / " + settings.maxOutputTokens());
        System.out.println("  Timeout: " + Duration.ofMillis(settings.requestTimeoutMillis()));
        System.out.println("  Attempts: exactly one; pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Output: " + outputDirectory);
    }

    private record Source(RetrievalAnswerSourceEvidence provenance, RetrievalEvaluationResult result) {}

    private record Arguments(
            String ollamaBaseUrl,
            String answerModel,
            int maxOutputTokens,
            int seed,
            Duration timeout,
            String sourceRetrievalRunDirectory,
            String outputDirectory
    ) {
        private static Arguments parse(String[] args) {
            if (args == null || args.length != 14) {
                throw usage();
            }
            List<String> values = List.of(args);
            try {
                return new Arguments(
                        required(values, "--ollama-base-url"),
                        required(values, "--answer-model"),
                        Integer.parseInt(required(values, "--max-output-tokens")),
                        Integer.parseInt(required(values, "--seed")),
                        Duration.parse(required(values, "--timeout")),
                        required(values, "--source-retrieval-run-dir"),
                        required(values, "--output-dir"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("--max-output-tokens and --seed must be integers.", exception);
            }
        }

        private static String required(List<String> values, String option) {
            if (values.stream().filter(option::equals).count() != 1) {
                throw usage();
            }
            int index = values.indexOf(option);
            if (index == values.size() - 1 || values.get(index + 1).isBlank()) {
                throw usage();
            }
            return values.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --ollama-base-url <loopback-url> --answer-model <installed-tag> "
                            + "--max-output-tokens <positive-integer> --seed <non-negative-integer> "
                            + "--timeout <ISO-8601-duration> --source-retrieval-run-dir <verified-r3-directory> "
                            + "--output-dir <new-dated-evidence-directory>");
        }
    }
}
