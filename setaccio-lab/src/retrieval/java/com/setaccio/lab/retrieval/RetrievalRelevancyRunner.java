package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;

/** Runs one explicit local R6 evaluator matrix from a verified saved R5 answer run. */
public final class RetrievalRelevancyRunner {

    private RetrievalRelevancyRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        Source source = loadVerifiedSource(parsed.sourceAnswerRunDirectory(), objectMapper, inputs);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                EvidenceProvenance.captureCodeBaseline(Path.of("")));
        RetrievalRelevancyPromptDefinition prompt = RetrievalRelevancyPromptDefinition.load();
        RetrievalRelevancyRunSettings settings = RetrievalRelevancyProtocol.settings(
                parsed.seed(), parsed.maxOutputTokens(), parsed.timeout());
        OllamaChatModelFactory modelFactory = new OllamaChatModelFactory();
        OllamaApi ollamaApi = modelFactory.createApi(parsed.ollamaBaseUrl(), parsed.timeout());
        OllamaChatModelIdentity chatIdentity = RetrievalRelevancyModelInventory.requireInstalled(
                ollamaApi.listModels(), parsed.evaluatorModel());
        RetrievalRelevancyModelIdentity modelIdentity = new RetrievalRelevancyModelIdentity(
                chatIdentity.providerId(), chatIdentity.requestedModel(), chatIdentity.effectiveModel(), chatIdentity.digest());
        Path outputDirectory = resolveNewOutputDirectory(parsed.outputDirectory());
        EvidenceRunDirectory.createNamed(outputDirectory.getParent(), outputDirectory.getFileName().toString());

        printProtocol(source, prompt, modelIdentity, settings, codeBaseline, outputDirectory);
        try {
            ChatModel evaluatorModel = modelFactory.create(ollamaApi, chatIdentity, settings.chatSettings());
            RetrievalRelevancyResult result = new RetrievalRelevancyExecutor(
                    new RetrievalRelevancyEvaluatorBoundary(evaluatorModel, modelIdentity, settings, prompt))
                    .execute(source.provenance(), source.result(), prompt, modelIdentity, settings);
            OllamaChatModelIdentity postRunIdentity = RetrievalRelevancyModelInventory.requireInstalled(
                    ollamaApi.listModels(), parsed.evaluatorModel());
            if (!chatIdentity.equals(postRunIdentity)) {
                throw new IllegalStateException("Ollama evaluator model identity changed during relevance evaluation.");
            }
            requireSameCleanBaseline(codeBaseline, EvidenceProvenance.captureCodeBaseline(Path.of("")));
            new RetrievalRelevancyEvidence(objectMapper, inputs.corpus(), inputs.catalog())
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Retrieval relevancy evaluation complete: "
                    + outputDirectory.resolve(RetrievalRelevancyEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Retrieval relevancy evaluation failed after output reservation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    static Path resolveNewOutputDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Output directory must not be blank.");
        }
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/retrieval-relevancy").normalize();
        Path outputDirectory = projectDirectory.resolve(value).normalize();
        if (!evidenceRoot.equals(outputDirectory.getParent())) {
            throw new IllegalArgumentException("Output directory must be directly under build/retrieval-relevancy/.");
        }
        Path fileName = outputDirectory.getFileName();
        if (fileName == null || !fileName.toString().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory must contain a YYYY-MM-DD date.");
        }
        return outputDirectory;
    }

    static Path resolveSourceRunDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Source answer run directory must not be blank.");
        }
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path sourceRoot = projectDirectory.resolve("build/retrieval-answer").normalize();
        Path runDirectory = projectDirectory.resolve(value).normalize();
        if (!sourceRoot.equals(runDirectory.getParent())
                || Files.isSymbolicLink(runDirectory)
                || !Files.isDirectory(runDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "Source answer run directory must be an existing direct child of build/retrieval-answer/.");
        }
        return runDirectory;
    }

    private static Source loadVerifiedSource(
            String sourceRunDirectory,
            ObjectMapper objectMapper,
            RetrievalEvaluationRunner.Inputs inputs
    ) {
        Path source = resolveSourceRunDirectory(sourceRunDirectory);
        RetrievalAnswerEvidence answerEvidence = new RetrievalAnswerEvidence(
                objectMapper, inputs.corpus(), inputs.catalog());
        RetrievalAnswerEvidence.OfflineResult verification = answerEvidence.verify(source);
        if (!verification.valid()) {
            throw new IllegalArgumentException("Source R5 answer evidence did not verify: "
                    + String.join(" ", verification.failures()));
        }
        EvidenceManifest manifest = new EvidenceManifestStore(objectMapper).read(source);
        requireCleanBaseline(manifest.codeBaseline());
        EvidenceArtifact rawArtifact = manifest.artifacts().stream()
                .filter(artifact -> RetrievalAnswerEvidence.RAW_ROLE.equals(artifact.role()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Source answer manifest has no raw-result artifact."));
        Path rawPath = source.resolve(rawArtifact.path()).normalize();
        if (!rawPath.startsWith(source) || Files.isSymbolicLink(rawPath)) {
            throw new IllegalArgumentException("Source answer raw artifact is unsafe.");
        }
        try {
            RetrievalAnswerResult result = objectMapper.readerFor(RetrievalAnswerResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
            return new Source(new RetrievalRelevancySourceEvidence(
                    source.getFileName().toString(),
                    EvidenceIntegrity.sha256(rawPath),
                    EvidenceIntegrity.sha256(source.resolve(EvidenceManifestStore.MANIFEST_FILENAME)),
                    manifest.codeBaseline().gitCommit()), result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Source answer raw result could not be loaded.", exception);
        }
    }

    private static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline baseline) {
        if (baseline == null || baseline.workingTreeDirty() || baseline.gitCommit() == null
                || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new IllegalStateException(
                    "Formal retrieval relevancy evaluation requires a clean worktree at a full Git commit.");
        }
        return baseline;
    }

    private static void requireSameCleanBaseline(EvidenceCodeBaseline expected, EvidenceCodeBaseline observed) {
        if (!requireCleanBaseline(expected).gitCommit().equals(requireCleanBaseline(observed).gitCommit())) {
            throw new IllegalStateException("Git baseline changed during retrieval relevancy evaluation.");
        }
    }

    private static void printProtocol(
            Source source,
            RetrievalRelevancyPromptDefinition prompt,
            RetrievalRelevancyModelIdentity modelIdentity,
            RetrievalRelevancyRunSettings settings,
            EvidenceCodeBaseline codeBaseline,
            Path outputDirectory
    ) {
        System.out.println("Starting opt-in local retrieval relevancy evaluation");
        System.out.println("  Verified R5 source: " + source.provenance().sourceRunId());
        System.out.println("  Rows: " + source.result().rows().size()
                + " preserved answers; retrieval and answer generation are not re-run");
        System.out.println("  Prompt: " + prompt.contract().promptId() + " / " + prompt.contract().promptSha256());
        System.out.println("  Evaluator model: requested " + modelIdentity.requestedModel()
                + ", effective " + modelIdentity.effectiveModel());
        System.out.println("  Ollama digest: " + modelIdentity.digest());
        System.out.println("  Temperature / seed / max output: " + settings.temperature() + " / "
                + settings.seed() + " / " + settings.maxOutputTokens());
        System.out.println("  Timeout: " + Duration.ofMillis(settings.requestTimeoutMillis()));
        System.out.println("  Attempts: exactly one; pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Output: " + outputDirectory);
    }

    private record Source(RetrievalRelevancySourceEvidence provenance, RetrievalAnswerResult result) {}

    private record Arguments(
            String ollamaBaseUrl,
            String evaluatorModel,
            int maxOutputTokens,
            int seed,
            Duration timeout,
            String sourceAnswerRunDirectory,
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
                        required(values, "--evaluator-model"),
                        Integer.parseInt(required(values, "--max-output-tokens")),
                        Integer.parseInt(required(values, "--seed")),
                        Duration.parse(required(values, "--timeout")),
                        required(values, "--source-answer-run-dir"),
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
                    "Expected --ollama-base-url <loopback-url> --evaluator-model <installed-tag> "
                            + "--max-output-tokens <positive-integer> --seed <non-negative-integer> "
                            + "--timeout <ISO-8601-duration> --source-answer-run-dir <verified-r5-directory> "
                            + "--output-dir <new-dated-build-directory>");
        }
    }
}
