package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Creates one new provider-free retrieval-only evidence run from the approved public fixtures. */
public final class RetrievalEvaluationRunner {

    private RetrievalEvaluationRunner() {}

    public static void main(String[] args) {
        String outputDirectoryArgument = parseOutputDirectory(args);
        Inputs inputs = loadInputs();
        Path outputDirectory = resolveNewOutputDirectory(outputDirectoryArgument);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        System.out.println("Starting provider-free retrieval-only evaluation");
        System.out.println("  Corpus: " + inputs.corpus().catalogId() + " v" + inputs.corpus().catalogVersion());
        System.out.println("  Query fixtures: " + inputs.catalog().fixtures().size());
        System.out.println("  Method: " + DeterministicLexicalRetriever.METHOD_ID
                + " v" + DeterministicLexicalRetriever.METHOD_VERSION);
        System.out.println("  Execution: sequential with immediate repeatability check");
        System.out.println("  Output: " + outputDirectory);

        RetrievalEvaluationResult result = new RetrievalEvaluationExecutor(new DeterministicLexicalRetriever())
                .execute(inputs.corpus(), inputs.catalog());
        RetrievalEvaluationAnalyzer.Analysis analysis = new RetrievalEvaluationAnalyzer(
                inputs.corpus(),
                inputs.catalog(),
                new DeterministicLexicalRetriever()).analyze(result);
        if (!analysis.valid()) {
            throw new IllegalStateException("Retrieval evaluation failed integrity checks: "
                    + String.join(" ", analysis.integrityFailures()));
        }

        EvidenceRunDirectory.createNamed(outputDirectory.getParent(), outputDirectory.getFileName().toString());
        new RetrievalEvaluationEvidence(objectMapper, inputs.corpus(), inputs.catalog())
                .write(outputDirectory, result);
        System.out.println("Retrieval-only evaluation complete: "
                + outputDirectory.resolve(RetrievalEvaluationEvidence.SUMMARY_FILENAME));
    }

    static Inputs loadInputs() {
        try {
            RetrievalCorpus corpus = new RetrievalCorpusLoader().loadApproved(
                    resourceDirectory("retrieval/corpus-v1", "Packaged approved retrieval corpus resource is missing"));
            RetrievalQueryCatalog catalog = new RetrievalQueryCatalogLoader().loadConfirmed(
                    resourceDirectory("retrieval/query-fixtures-v1", "Packaged confirmed query catalog resource is missing"),
                    corpus);
            return new Inputs(corpus, catalog);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Failed to load approved retrieval evaluation inputs", exception);
        }
    }

    static Path resolveNewOutputDirectory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Output directory must not be blank.");
        }
        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        Path evidenceRoot = projectDirectory.resolve("build/retrieval-evaluation").normalize();
        Path outputDirectory = projectDirectory.resolve(value).normalize();
        if (!evidenceRoot.equals(outputDirectory.getParent())) {
            throw new IllegalArgumentException(
                    "Output directory must be directly under build/retrieval-evaluation/.");
        }
        Path fileName = outputDirectory.getFileName();
        if (fileName == null || !fileName.toString().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory must contain a YYYY-MM-DD date.");
        }
        return outputDirectory;
    }

    private static String parseOutputDirectory(String[] args) {
        if (args == null || args.length != 2 || !"--output-dir".equals(args[0]) || args[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Expected --output-dir <new-directory-under-build/retrieval-evaluation>");
        }
        return args[1];
    }

    private static Path resourceDirectory(String resourceName, String missingMessage) throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                RetrievalEvaluationRunner.class.getClassLoader().getResource(resourceName),
                missingMessage);
        return Path.of(resource.toURI());
    }

    record Inputs(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {}
}
