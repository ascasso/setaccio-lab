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

/** Immutable saved-evidence lifecycle for the provider-free Phase 5 R3 retrieval evaluation. */
final class RetrievalEvaluationEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final RetrievalEvaluationAnalyzer analyzer;
    private final RetrievalEvaluationReport report;

    RetrievalEvaluationEvidence(
            ObjectMapper objectMapper,
            RetrievalCorpus corpus,
            RetrievalQueryCatalog catalog
    ) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new RetrievalEvaluationAnalyzer(corpus, catalog, new DeterministicLexicalRetriever());
        report = new RetrievalEvaluationReport();
    }

    Path write(Path runDirectory, RetrievalEvaluationResult result) {
        return write(runDirectory, result, EvidenceProvenance.captureCodeBaseline(Path.of("")));
    }

    Path write(
            Path runDirectory,
            RetrievalEvaluationResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        RetrievalEvaluationAnalyzer.Analysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException(
                    "Retrieval evaluation result failed integrity checks: "
                            + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(RetrievalEvaluationProtocol.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write raw retrieval evaluation result", exception);
        }
        EvidenceFiles.writeNewBytes(rawPath, rawJson, "Failed to write raw retrieval evaluation result");

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256(), codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write retrieval evaluation summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                RetrievalEvaluationProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                RetrievalEvaluationProtocol.EXECUTION_ENGINE,
                RetrievalEvaluationProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        EvidenceVerification verification = evidenceVerifier.verify(root, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated retrieval evaluation evidence failed verification: "
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
        verifySummary(inspection, failures);
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of("Retrieval evaluation summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".retrieval-evaluation-summary-",
                "Retrieval evaluation summary must not be a symbolic link.",
                "Failed to regenerate retrieval evaluation summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Retrieval evaluation run directory must not be null.",
                "Retrieval evaluation run directory is missing or unsafe.");
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
                    manifest.artifacts(),
                    RAW_ROLE,
                    failures,
                    "Retrieval evaluation manifest must declare exactly one raw-result artifact.");
            summaryArtifact = EvidenceFiles.singleArtifact(
                    manifest.artifacts(),
                    SUMMARY_ROLE,
                    failures,
                    "Retrieval evaluation manifest must declare exactly one summary artifact.");
            if (manifest.artifacts().size() != 2) {
                failures.add("Retrieval evaluation manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Retrieval evaluation manifest could not be loaded: "
                    + safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(
                root,
                rawArtifact,
                failures,
                "Retrieval evaluation raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !RetrievalEvaluationProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Retrieval evaluation raw artifact must be "
                    + RetrievalEvaluationProtocol.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath,
                rawArtifact,
                true,
                failures,
                "Raw retrieval evaluation artifact is missing or unsafe.",
                "Raw retrieval evaluation artifact is empty.",
                "Raw retrieval evaluation artifact size does not match its manifest.",
                "Raw retrieval evaluation artifact SHA-256 does not match its manifest.",
                "Raw retrieval evaluation artifact could not be verified.");
        RetrievalEvaluationResult result = rawValid ? readRawResult(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            validateSettings(manifest, result, failures);
            try {
                RetrievalEvaluationAnalyzer.Analysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null) {
                    expectedSummary = report.render(
                            result,
                            analysis,
                            rawArtifact.path(),
                            rawArtifact.sha256(),
                            manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw retrieval evaluation could not be analyzed: "
                        + safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Retrieval evaluation summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated retrieval evaluation summary does not match the manifest.");
        }
        validateLayout(root, failures);
        return new Inspection(
                root,
                manifest,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!RetrievalEvaluationProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Retrieval evaluation manifest suite is not public-safe-retrieval-evaluation.");
        }
        if (!RetrievalEvaluationProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Retrieval evaluation manifest execution engine is not the locked lexical baseline.");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            RetrievalEvaluationResult result,
            List<String> failures
    ) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(RetrievalEvaluationProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.equals(actual)) {
            failures.add("Retrieval evaluation manifest settings differ from the raw locked protocol.");
        }
    }

    private RetrievalEvaluationResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(RetrievalEvaluationResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw retrieval evaluation JSON could not be read: " + safeMessage(exception) + ".");
            return null;
        }
    }

    private static void validateLayout(Path root, List<String> failures) {
        EvidenceFiles.validateLayout(
                root,
                Set.of(
                        EvidenceManifestStore.MANIFEST_FILENAME,
                        RetrievalEvaluationProtocol.RAW_FILENAME,
                        SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in retrieval evaluation evidence: ",
                "Unexpected directory is present in retrieval evaluation evidence: ",
                "Unexpected artifact is present in retrieval evaluation evidence: ",
                "Retrieval evaluation evidence directory could not be inspected.");
    }

    private static void verifySummary(Inspection inspection, Set<String> failures) {
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Retrieval evaluation summary is missing or unsafe.",
                "Retrieval evaluation summary drifted from deterministic reanalysis.",
                "Retrieval evaluation summary drifted from deterministic reanalysis.",
                "Retrieval evaluation summary could not be read.");
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
