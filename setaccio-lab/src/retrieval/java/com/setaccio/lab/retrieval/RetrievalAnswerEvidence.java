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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable saved-evidence lifecycle for opt-in R5 answer generation. */
final class RetrievalAnswerEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final RetrievalAnswerAnalyzer analyzer;
    private final RetrievalAnswerReport report;

    RetrievalAnswerEvidence(ObjectMapper objectMapper, RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new RetrievalAnswerAnalyzer(corpus, catalog);
        report = new RetrievalAnswerReport();
    }

    Path write(Path runDirectory, RetrievalAnswerResult result, EvidenceCodeBaseline codeBaseline) {
        RetrievalAnswerAnalyzer.Analysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException("Retrieval answer result failed integrity checks: "
                    + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(RetrievalAnswerProtocol.RAW_FILENAME);
        try {
            EvidenceFiles.writeNewBytes(
                    rawPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),
                    "Failed to write raw retrieval answer result");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize raw retrieval answer result", exception);
        }
        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256(), codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write retrieval answer summary");
        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                RetrievalAnswerProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                RetrievalAnswerProtocol.EXECUTION_ENGINE,
                RetrievalAnswerProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        OfflineResult verification = verify(root);
        if (!verification.valid()) {
            throw new IllegalStateException("Generated retrieval answer evidence failed verification: "
                    + String.join(" ", verification.failures()));
        }
        return manifestPath;
    }

    OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspect(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.manifest() != null) {
            EvidenceVerification manifestVerification = evidenceVerifier.verify(inspection.root(), inspection.manifest());
            failures.addAll(manifestVerification.failures());
        }
        if (inspection.expectedSummary() != null) {
            EvidenceFiles.verifyText(
                    inspection.summaryPath(),
                    inspection.expectedSummary(),
                    failures,
                    "Retrieval answer summary is missing or unsafe.",
                    "Retrieval answer summary is empty.",
                    "Regenerated retrieval answer summary does not match the saved summary.",
                    "Retrieval answer summary could not be verified.");
        }
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspect(runDirectory);
        if (!inspection.failures().isEmpty() || inspection.expectedSummary() == null) {
            return new OfflineResult(inspection.failures().isEmpty()
                    ? List.of("Retrieval answer summary could not be regenerated.")
                    : inspection.failures());
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".retrieval-answer-summary-",
                "Retrieval answer summary must not be a symbolic link.",
                "Failed to regenerate retrieval answer summary");
        return verify(runDirectory);
    }

    private Inspection inspect(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Retrieval answer run directory must not be null.",
                "Retrieval answer run directory is missing or unsafe.");
        if (root == null) {
            return Inspection.invalid(failures);
        }
        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        try {
            manifest = manifestStore.read(root);
            if (!RetrievalAnswerProtocol.SUITE.equals(manifest.suite())) {
                failures.add("Retrieval answer manifest suite is not public-safe-retrieval-answer.");
            }
            if (!RetrievalAnswerProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
                failures.add("Retrieval answer manifest execution engine is not the locked R5 engine.");
            }
            rawArtifact = EvidenceFiles.singleArtifact(
                    manifest.artifacts(), RAW_ROLE, failures,
                    "Retrieval answer manifest must declare exactly one raw-result artifact.");
            summaryArtifact = EvidenceFiles.singleArtifact(
                    manifest.artifacts(), SUMMARY_ROLE, failures,
                    "Retrieval answer manifest must declare exactly one summary artifact.");
            if (manifest.artifacts().size() != 2) {
                failures.add("Retrieval answer manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Retrieval answer manifest could not be loaded: " + safeMessage(exception) + ".");
        }
        Path rawPath = EvidenceFiles.resolveArtifact(
                root, rawArtifact, failures,
                "Retrieval answer raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !RetrievalAnswerProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Retrieval answer raw artifact must be " + RetrievalAnswerProtocol.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath,
                rawArtifact,
                true,
                failures,
                "Raw retrieval answer artifact is missing or unsafe.",
                "Raw retrieval answer artifact is empty.",
                "Raw retrieval answer artifact size does not match its manifest.",
                "Raw retrieval answer artifact SHA-256 does not match its manifest.",
                "Raw retrieval answer artifact could not be verified.");
        RetrievalAnswerResult result = rawValid ? readRaw(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            try {
                RetrievalAnswerAnalyzer.Analysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (manifest != null && rawArtifact != null) {
                    JsonNode expectedSettings = objectMapper.valueToTree(RetrievalAnswerProtocol.manifestSettings(result));
                    JsonNode actualSettings = objectMapper.valueToTree(manifest.settings());
                    if (!expectedSettings.equals(actualSettings)) {
                        failures.add("Retrieval answer manifest settings differ from the raw locked protocol.");
                    }
                    expectedSummary = report.render(
                            result, analysis, rawArtifact.path(), rawArtifact.sha256(), manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw retrieval answer result could not be analyzed: " + safeMessage(exception) + ".");
            }
        }
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Retrieval answer summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated retrieval answer summary does not match the manifest.");
        }
        if (Files.isSymbolicLink(summaryPath) || !Files.isRegularFile(summaryPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Retrieval answer summary is missing or unsafe.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME, RetrievalAnswerProtocol.RAW_FILENAME, SUMMARY_FILENAME),
                failures,
                "Retrieval answer evidence contains a symbolic link: ",
                "Retrieval answer evidence contains an unexpected directory: ",
                "Unexpected retrieval answer artifact: ",
                "Retrieval answer evidence layout could not be inspected.");
        return new Inspection(root, manifest, summaryPath, expectedSummary, List.copyOf(new LinkedHashSet<>(failures)));
    }

    private RetrievalAnswerResult readRaw(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(RetrievalAnswerResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw retrieval answer artifact could not be parsed: " + safeMessage(exception) + ".");
            return null;
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName();
    }

    record OfflineResult(List<String> failures) {
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
