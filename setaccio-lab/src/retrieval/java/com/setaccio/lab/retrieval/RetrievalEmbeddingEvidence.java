package com.setaccio.lab.retrieval;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceFiles;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceVerification;
import com.setaccio.lab.evidence.EvidenceVerifier;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable saved-evidence lifecycle for opt-in local R4 embedding vectors. */
final class RetrievalEmbeddingEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final RetrievalEmbeddingAnalyzer analyzer;
    private final RetrievalEmbeddingReport report;

    RetrievalEmbeddingEvidence(ObjectMapper objectMapper, RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new RetrievalEmbeddingAnalyzer(corpus, catalog);
        report = new RetrievalEmbeddingReport();
    }

    Path write(Path runDirectory, RetrievalEmbeddingResult result) {
        return write(runDirectory, result, EvidenceProvenance.captureCodeBaseline(Path.of("")));
    }

    Path write(Path runDirectory, RetrievalEmbeddingResult result, EvidenceCodeBaseline codeBaseline) {
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        RetrievalEmbeddingAnalyzer.Analysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException("Retrieval embedding result failed integrity checks: "
                    + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(RetrievalEmbeddingProtocol.RAW_FILENAME);
        try {
            EvidenceFiles.writeNewBytes(
                    rawPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),
                    "Failed to write raw retrieval embedding result");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write raw retrieval embedding result", exception);
        }
        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256(), codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write retrieval embedding summary");
        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                RetrievalEmbeddingProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                RetrievalEmbeddingProtocol.EXECUTION_ENGINE,
                RetrievalEmbeddingProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        EvidenceVerification verification = evidenceVerifier.verify(root, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException("Generated retrieval embedding evidence failed verification: "
                    + String.join(" ", verification.failures()));
        }
        return manifestPath;
    }

    OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.manifest() != null) {
            failures.addAll(evidenceVerifier.verify(inspection.root(), inspection.manifest()).failures());
        }
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Retrieval embedding summary is missing or unsafe.",
                "Retrieval embedding summary drifted from deterministic reanalysis.",
                "Retrieval embedding summary drifted from deterministic reanalysis.",
                "Retrieval embedding summary could not be read.");
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of("Retrieval embedding summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".retrieval-embedding-summary-",
                "Retrieval embedding summary must not be a symbolic link.",
                "Failed to regenerate retrieval embedding summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Retrieval embedding run directory must not be null.",
                "Retrieval embedding run directory is missing or unsafe.");
        if (root == null) {
            return Inspection.invalid(failures);
        }
        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        try {
            manifest = manifestStore.read(root);
            validateEnvelope(manifest, failures);
            rawArtifact = EvidenceFiles.singleArtifact(
                    manifest.artifacts(), RAW_ROLE, failures,
                    "Retrieval embedding manifest must declare exactly one raw-result artifact.");
            summaryArtifact = EvidenceFiles.singleArtifact(
                    manifest.artifacts(), SUMMARY_ROLE, failures,
                    "Retrieval embedding manifest must declare exactly one summary artifact.");
            if (manifest.artifacts().size() != 2) {
                failures.add("Retrieval embedding manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Retrieval embedding manifest could not be loaded: " + safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(
                root, rawArtifact, failures,
                "Retrieval embedding raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !RetrievalEmbeddingProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Retrieval embedding raw artifact must be " + RetrievalEmbeddingProtocol.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath, rawArtifact, true, failures,
                "Raw retrieval embedding artifact is missing or unsafe.",
                "Raw retrieval embedding artifact is empty.",
                "Raw retrieval embedding artifact size does not match its manifest.",
                "Raw retrieval embedding artifact SHA-256 does not match its manifest.",
                "Raw retrieval embedding artifact could not be verified.");
        RetrievalEmbeddingResult result = rawValid ? readRawResult(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            validateSettings(manifest, result, failures);
            try {
                RetrievalEmbeddingAnalyzer.Analysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null) {
                    expectedSummary = report.render(
                            result, analysis, rawArtifact.path(), rawArtifact.sha256(), manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw retrieval embedding could not be analyzed: " + safeMessage(exception) + ".");
            }
        }
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Retrieval embedding summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated retrieval embedding summary does not match the manifest.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME, RetrievalEmbeddingProtocol.RAW_FILENAME, SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in retrieval embedding evidence: ",
                "Unexpected directory is present in retrieval embedding evidence: ",
                "Unexpected artifact is present in retrieval embedding evidence: ",
                "Retrieval embedding evidence directory could not be inspected.");
        return new Inspection(root, manifest, summaryPath, expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!RetrievalEmbeddingProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Retrieval embedding manifest suite is not public-safe-retrieval-embedding.");
        }
        if (!RetrievalEmbeddingProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Retrieval embedding manifest execution engine is not spring-ai-ollama-api-embed.");
        }
    }

    private void validateSettings(EvidenceManifest manifest, RetrievalEmbeddingResult result, List<String> failures) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(RetrievalEmbeddingProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.equals(actual)) {
            failures.add("Retrieval embedding manifest settings differ from the raw locked protocol.");
        }
    }

    private RetrievalEmbeddingResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(RetrievalEmbeddingResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw retrieval embedding JSON could not be read: " + safeMessage(exception) + ".");
            return null;
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = null;
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    record OfflineResult(List<String> failures) {

        OfflineResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        boolean valid() {
            return failures.isEmpty();
        }
    }

    private record Inspection(
            Path root,
            EvidenceManifest manifest,
            Path summaryPath,
            String expectedSummary,
            List<String> failures
    ) {

        private static Inspection invalid(List<String> failures) {
            return new Inspection(null, null, null, null, List.copyOf(failures));
        }
    }
}
