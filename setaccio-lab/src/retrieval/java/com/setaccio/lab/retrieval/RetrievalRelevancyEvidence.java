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

/** Immutable saved-evidence lifecycle for opt-in R6 relevance evaluation. */
final class RetrievalRelevancyEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final RetrievalRelevancyAnalyzer analyzer;
    private final RetrievalRelevancyReport report;

    RetrievalRelevancyEvidence(ObjectMapper objectMapper, RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new RetrievalRelevancyAnalyzer(corpus, catalog);
        report = new RetrievalRelevancyReport();
    }

    Path write(Path runDirectory, RetrievalRelevancyResult result, EvidenceCodeBaseline codeBaseline) {
        RetrievalRelevancyAnalyzer.Analysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException("Retrieval relevancy result failed integrity checks: "
                    + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(RetrievalRelevancyProtocol.RAW_FILENAME);
        try {
            EvidenceFiles.writeNewBytes(
                    rawPath,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),
                    "Failed to write raw retrieval relevancy result");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize raw retrieval relevancy result", exception);
        }
        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256(), codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write retrieval relevancy summary");
        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                RetrievalRelevancyProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                RetrievalRelevancyProtocol.EXECUTION_ENGINE,
                RetrievalRelevancyProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        OfflineResult verification = verify(root);
        if (!verification.valid()) {
            throw new IllegalStateException("Generated retrieval relevancy evidence failed verification: "
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
                    inspection.summaryPath(), inspection.expectedSummary(), failures,
                    "Retrieval relevancy summary is missing or unsafe.",
                    "Retrieval relevancy summary is empty.",
                    "Regenerated retrieval relevancy summary does not match the saved summary.",
                    "Retrieval relevancy summary could not be verified.");
        }
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspect(runDirectory);
        if (!inspection.failures().isEmpty() || inspection.expectedSummary() == null) {
            return new OfflineResult(inspection.failures().isEmpty()
                    ? List.of("Retrieval relevancy summary could not be regenerated.")
                    : inspection.failures());
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(), inspection.expectedSummary(), ".retrieval-relevancy-summary-",
                "Retrieval relevancy summary must not be a symbolic link.",
                "Failed to regenerate retrieval relevancy summary");
        return verify(runDirectory);
    }

    private Inspection inspect(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory, failures,
                "Retrieval relevancy run directory must not be null.",
                "Retrieval relevancy run directory is missing or unsafe.");
        if (root == null) {
            return Inspection.invalid(failures);
        }
        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        try {
            manifest = manifestStore.read(root);
            if (!RetrievalRelevancyProtocol.SUITE.equals(manifest.suite())) {
                failures.add("Retrieval relevancy manifest suite is not public-safe-retrieval-relevancy.");
            }
            if (!RetrievalRelevancyProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
                failures.add("Retrieval relevancy manifest execution engine is not the locked R6 engine.");
            }
            rawArtifact = EvidenceFiles.singleArtifact(manifest.artifacts(), RAW_ROLE, failures,
                    "Retrieval relevancy manifest must declare exactly one raw-result artifact.");
            summaryArtifact = EvidenceFiles.singleArtifact(manifest.artifacts(), SUMMARY_ROLE, failures,
                    "Retrieval relevancy manifest must declare exactly one summary artifact.");
            if (manifest.artifacts().size() != 2) {
                failures.add("Retrieval relevancy manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Retrieval relevancy manifest could not be loaded: " + safeMessage(exception) + ".");
        }
        Path rawPath = EvidenceFiles.resolveArtifact(root, rawArtifact, failures,
                "Retrieval relevancy raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !RetrievalRelevancyProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Retrieval relevancy raw artifact must be " + RetrievalRelevancyProtocol.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(rawPath, rawArtifact, true, failures,
                "Raw retrieval relevancy artifact is missing or unsafe.",
                "Raw retrieval relevancy artifact is empty.",
                "Raw retrieval relevancy artifact size does not match its manifest.",
                "Raw retrieval relevancy artifact SHA-256 does not match its manifest.",
                "Raw retrieval relevancy artifact could not be verified.");
        RetrievalRelevancyResult result = rawValid ? readRaw(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            try {
                RetrievalRelevancyAnalyzer.Analysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (manifest != null && rawArtifact != null) {
                    JsonNode expectedSettings = objectMapper.valueToTree(RetrievalRelevancyProtocol.manifestSettings(result));
                    JsonNode actualSettings = objectMapper.valueToTree(manifest.settings());
                    if (!expectedSettings.equals(actualSettings)) {
                        failures.add("Retrieval relevancy manifest settings differ from the raw locked protocol.");
                    }
                    expectedSummary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256(), manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw retrieval relevancy result could not be analyzed: " + safeMessage(exception) + ".");
            }
        }
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Retrieval relevancy summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(summaryArtifact, expectedSummary, failures,
                    "Regenerated retrieval relevancy summary does not match the manifest.");
        }
        if (Files.isSymbolicLink(summaryPath) || !Files.isRegularFile(summaryPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Retrieval relevancy summary is missing or unsafe.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME, RetrievalRelevancyProtocol.RAW_FILENAME, SUMMARY_FILENAME),
                failures,
                "Retrieval relevancy evidence contains a symbolic link: ",
                "Retrieval relevancy evidence contains an unexpected directory: ",
                "Unexpected retrieval relevancy artifact: ",
                "Retrieval relevancy evidence layout could not be inspected.");
        return new Inspection(root, manifest, summaryPath, expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private RetrievalRelevancyResult readRaw(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(RetrievalRelevancyResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw retrieval relevancy artifact could not be parsed: " + safeMessage(exception) + ".");
            return null;
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getClass().getSimpleName();
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
