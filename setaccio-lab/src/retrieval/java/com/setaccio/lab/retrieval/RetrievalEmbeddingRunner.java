package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.ollama.api.OllamaApi;

/**
 * Runs one explicit local R4 embedding request after locking its clean code baseline and
 * installed-model identity. This runner is opt-in and is never attached to the default lifecycle.
 */
public final class RetrievalEmbeddingRunner {

    private RetrievalEmbeddingRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                EvidenceProvenance.captureCodeBaseline(Path.of("")));
        RetrievalEvaluationRunner.Inputs inputs = RetrievalEvaluationRunner.loadInputs();
        RetrievalEmbeddingRunSettings settings = RetrievalEmbeddingProtocol.settings(parsed.topK());
        if (settings.topK() > inputs.corpus().documents().size()) {
            throw new IllegalArgumentException("--top-k must not exceed the approved corpus document count.");
        }
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        OllamaApi ollamaApi = OllamaRetrievalEmbeddingClient.createApi(
                parsed.ollamaBaseUrl(), Duration.ofMillis(settings.requestTimeoutMillis()));
        RetrievalEmbeddingModelIdentity modelIdentity = RetrievalEmbeddingModelInventory.requireInstalled(
                ollamaApi.listModels(), parsed.embeddingModel());
        RetrievalEmbeddingModelInventory.requireEmbeddingCapability(
                ollamaApi.showModel(new OllamaApi.ShowModelRequest(modelIdentity.effectiveModel())),
                modelIdentity);
        Path outputDirectory = resolveNewOutputDirectory(parsed.outputDirectory());
        reserveOutputDirectory(outputDirectory);

        printProtocol(inputs, settings, modelIdentity, codeBaseline, outputDirectory);
        try {
            RetrievalEmbeddingResult result = new RetrievalEmbeddingExecutor(
                    new OllamaRetrievalEmbeddingClient(ollamaApi))
                    .execute(inputs.corpus(), inputs.catalog(), settings, modelIdentity);
            requireUnchangedModelIdentity(modelIdentity, RetrievalEmbeddingModelInventory.requireInstalled(
                    ollamaApi.listModels(), parsed.embeddingModel()));
            RetrievalEmbeddingAnalyzer.Analysis analysis = new RetrievalEmbeddingAnalyzer(
                    inputs.corpus(), inputs.catalog()).analyze(result);
            if (!analysis.valid()) {
                throw new IllegalStateException("Retrieval embedding result failed integrity checks: "
                        + String.join(" ", analysis.integrityFailures()));
            }

            requireSameCleanBaseline(
                    codeBaseline,
                    EvidenceProvenance.captureCodeBaseline(Path.of("")));
            new RetrievalEmbeddingEvidence(objectMapper, inputs.corpus(), inputs.catalog())
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Embedding retrieval complete: "
                    + outputDirectory.resolve(RetrievalEmbeddingEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Retrieval embedding generation failed after output reservation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    static Path resolveNewOutputDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Output directory must not be blank.");
        }
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/retrieval-embedding").normalize();
        Path outputDirectory = projectDirectory.resolve(value).normalize();
        if (!evidenceRoot.equals(outputDirectory.getParent())) {
            throw new IllegalArgumentException(
                    "Output directory must be directly under build/retrieval-embedding/.");
        }
        Path fileName = outputDirectory.getFileName();
        if (fileName == null || !fileName.toString().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory must contain a YYYY-MM-DD date.");
        }
        return outputDirectory;
    }

    static Path reserveOutputDirectory(Path outputDirectory) {
        if (outputDirectory == null || outputDirectory.getParent() == null || outputDirectory.getFileName() == null) {
            throw new IllegalArgumentException("Output directory must identify a named run directory.");
        }
        return EvidenceRunDirectory.createNamed(outputDirectory.getParent(), outputDirectory.getFileName().toString());
    }

    static void requireUnchangedModelIdentity(
            RetrievalEmbeddingModelIdentity expected,
            RetrievalEmbeddingModelIdentity observed
    ) {
        if (!expected.equals(observed)) {
            throw new IllegalStateException(
                    "Ollama embedding model identity changed during generation; refusing to write evidence. "
                            + "Expected " + expected + " but observed " + observed + ".");
        }
    }

    private static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline codeBaseline) {
        if (codeBaseline == null
                || codeBaseline.workingTreeDirty()
                || codeBaseline.gitCommit() == null
                || !codeBaseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new IllegalStateException(
                    "Formal retrieval embedding generation requires a clean worktree at a full Git commit.");
        }
        return codeBaseline;
    }

    static void requireSameCleanBaseline(
            EvidenceCodeBaseline expected,
            EvidenceCodeBaseline observed
    ) {
        EvidenceCodeBaseline initial = requireCleanBaseline(expected);
        EvidenceCodeBaseline current = requireCleanBaseline(observed);
        if (!initial.gitCommit().equals(current.gitCommit())) {
            throw new IllegalStateException("Git baseline changed during retrieval embedding generation.");
        }
    }

    private static void printProtocol(
            RetrievalEvaluationRunner.Inputs inputs,
            RetrievalEmbeddingRunSettings settings,
            RetrievalEmbeddingModelIdentity modelIdentity,
            EvidenceCodeBaseline codeBaseline,
            Path outputDirectory
    ) {
        System.out.println("Starting opt-in local embedding retrieval generation");
        System.out.println("  Corpus: " + inputs.corpus().catalogId() + " v" + inputs.corpus().catalogVersion());
        System.out.println("  Query fixtures: " + inputs.catalog().fixtures().size());
        System.out.println("  Provider / endpoint category: " + settings.provider() + " / "
                + settings.endpointCategory());
        System.out.println("  Embedding model: requested " + modelIdentity.requestedModel()
                + ", effective " + modelIdentity.effectiveModel());
        System.out.println("  Ollama digest: " + modelIdentity.digest());
        System.out.println("  Inputs: " + inputs.corpus().documents().size() + " documents + "
                + inputs.catalog().fixtures().size() + " queries in one batch");
        System.out.println("  Chunking / normalization / ranking: " + settings.chunkingPolicy() + " / "
                + settings.normalizationPolicy() + " / " + settings.distanceMetric());
        System.out.println("  Top K: " + settings.topK());
        System.out.println("  Timeout: " + Duration.ofMillis(settings.requestTimeoutMillis()));
        System.out.println("  Attempts: exactly " + settings.maxAttempts() + "; pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Output: " + outputDirectory);
    }

    private record Arguments(String ollamaBaseUrl, String embeddingModel, int topK, String outputDirectory) {

        private static Arguments parse(String[] args) {
            if (args == null || args.length != 8) {
                throw usage();
            }
            List<String> values = List.of(args);
            String baseUrl = required(values, "--ollama-base-url");
            String model = required(values, "--embedding-model");
            String topK = required(values, "--top-k");
            String outputDirectory = required(values, "--output-dir");
            try {
                return new Arguments(baseUrl, model, Integer.parseInt(topK), outputDirectory);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("--top-k must be a positive integer.", exception);
            }
        }

        private static String required(List<String> values, String option) {
            int index = values.indexOf(option);
            if (index < 0 || index == values.size() - 1 || values.get(index + 1).isBlank()) {
                throw usage();
            }
            return values.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --ollama-base-url <loopback-url> --embedding-model <installed-tag> "
                            + "--top-k <positive-integer> --output-dir <new-dated-build-directory>");
        }
    }
}
