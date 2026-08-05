package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceFiles;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceVerification;
import com.setaccio.lab.evidence.EvidenceVerifier;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VisionMatrixEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final VisionMatrixAnalyzer analyzer;
    private final VisionMatrixReport report;

    VisionMatrixEvidence(
            ObjectMapper objectMapper,
            VisionPromptDefinition promptDefinition) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new VisionMatrixAnalyzer(promptDefinition);
        report = new VisionMatrixReport();
    }

    Path write(
            Path runDirectory,
            VisionMatrixResult result,
            VisionMatrixAnalyzer.MatrixAnalysis analysis) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (analysis == null) {
            throw new IllegalArgumentException("analysis must not be null");
        }
        Path rawPath = runDirectory.resolve(VisionMatrixProtocol.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write raw vision matrix result", e);
        }
        EvidenceFiles.writeNewBytes(rawPath, rawJson, "Failed to write raw vision matrix result");

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(runDirectory, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256());
        Path summaryPath = runDirectory.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write vision matrix summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(runDirectory, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                VisionMatrixProtocol.SUITE,
                runDirectory.getFileName().toString(),
                Instant.now(),
                EvidenceProvenance.captureCodeBaseline(Path.of("")),
                EvidenceProvenance.detectFrameworkVersions(),
                VisionMatrixProtocol.EXECUTION_ENGINE,
                VisionMatrixProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(runDirectory, manifest);
        EvidenceVerification verification = evidenceVerifier.verify(runDirectory, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated vision matrix evidence failed verification: "
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
            return new OfflineResult(List.of("Vision matrix summary could not be regenerated."));
        }
        writeSummaryAtomically(inspection.summaryPath(), inspection.expectedSummary());
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = normalizedRunDirectory(runDirectory, failures);
        if (root == null) {
            return Inspection.invalid(failures);
        }

        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        try {
            manifest = manifestStore.read(root);
            validateEnvelope(manifest, failures);
            rawArtifact = singleArtifact(manifest.artifacts(), RAW_ROLE, failures);
            summaryArtifact = singleArtifact(manifest.artifacts(), SUMMARY_ROLE, failures);
            if (manifest.artifacts().size() != 2) {
                failures.add("Vision matrix manifest must declare exactly two artifacts.");
            }
        } catch (Exception e) {
            failures.add("Vision matrix manifest could not be loaded: " + safeMessage(e) + ".");
        }

        Path rawPath = resolveArtifact(root, rawArtifact, failures);
        if (rawArtifact != null && !VisionMatrixProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Vision matrix raw artifact must be vision-matrix-results.json.");
        }
        boolean rawValid = verifyRawArtifact(rawPath, rawArtifact, failures);
        VisionMatrixResult result = rawValid ? readRawResult(rawPath, failures) : null;
        VisionMatrixAnalyzer.MatrixAnalysis analysis = null;
        String expectedSummary = null;
        if (result != null) {
            validateSettings(manifest, result, failures);
            try {
                analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null) {
                    expectedSummary = report.render(
                            result,
                            analysis,
                            rawArtifact.path(),
                            rawArtifact.sha256());
                }
            } catch (Exception e) {
                failures.add("Raw vision matrix could not be analyzed: " + safeMessage(e) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Vision matrix summary artifact must be SUMMARY.md.");
            }
            validateRegeneratedSummaryDescriptor(summaryArtifact, expectedSummary, failures);
        }
        validateInputLayout(root, rawArtifact, failures);
        return new Inspection(
                root,
                manifest,
                summaryArtifact,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!VisionMatrixProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Vision matrix manifest suite is not vision-matrix.");
        }
        if (!VisionMatrixProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Vision matrix manifest execution engine is not spring-ai-direct.");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            VisionMatrixResult result,
            List<String> failures) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(VisionMatrixProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.equals(actual)) {
            failures.add("Vision matrix manifest settings differ from the raw locked protocol.");
        }
    }

    private VisionMatrixResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(VisionMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception e) {
            failures.add("Raw vision matrix JSON could not be read: " + safeMessage(e) + ".");
            return null;
        }
    }

    private static EvidenceArtifact singleArtifact(
            List<EvidenceArtifact> artifacts,
            String role,
            List<String> failures) {
        return EvidenceFiles.singleArtifact(
                artifacts,
                role,
                failures,
                "Vision matrix manifest must declare exactly one " + role + " artifact.");
    }

    private static Path resolveArtifact(
            Path root,
            EvidenceArtifact artifact,
            List<String> failures) {
        if (artifact == null) {
            return null;
        }
        return EvidenceFiles.resolveArtifact(
                root,
                artifact,
                failures,
                "Vision matrix raw artifact path escapes the evidence directory.");
    }

    private static boolean verifyRawArtifact(
            Path rawPath,
            EvidenceArtifact artifact,
            List<String> failures) {
        return EvidenceFiles.verifyArtifact(
                rawPath,
                artifact,
                true,
                failures,
                "Raw vision matrix artifact is missing or unsafe.",
                "Raw vision matrix artifact is empty.",
                "Raw vision matrix artifact size does not match its manifest.",
                "Raw vision matrix artifact SHA-256 does not match its manifest.",
                "Raw vision matrix artifact could not be verified.");
    }

    private static void validateRegeneratedSummaryDescriptor(
            EvidenceArtifact summaryArtifact,
            String expectedSummary,
            List<String> failures) {
        EvidenceFiles.validateTextDescriptor(
                summaryArtifact,
                expectedSummary,
                failures,
                "Regenerated vision matrix summary does not match the manifest.");
    }

    private static void validateInputLayout(
            Path root,
            EvidenceArtifact rawArtifact,
            List<String> failures) {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(EvidenceManifestStore.MANIFEST_FILENAME);
        allowed.add(SUMMARY_FILENAME);
        if (rawArtifact != null) {
            allowed.add(rawArtifact.path());
        }
        EvidenceFiles.validateLayout(
                root,
                allowed,
                failures,
                "Unsafe symbolic link is present in saved vision evidence: ",
                "Unexpected artifact is present in saved vision evidence: ",
                "Saved vision evidence directory could not be inspected.");
    }

    private static void verifySummary(Inspection inspection, Set<String> failures) {
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Vision matrix summary is missing or unsafe.",
                "Vision matrix summary is empty.",
                "Vision matrix summary differs from deterministic offline reanalysis.",
                "Vision matrix summary could not be verified.");
    }

    private static void writeSummaryAtomically(Path summaryPath, String summary) {
        EvidenceFiles.replaceTextAtomically(
                summaryPath,
                summary,
                ".vision-summary-",
                "Vision matrix summary must not be a symbolic link",
                "Failed to regenerate vision matrix summary");
    }

    private static Path normalizedRunDirectory(Path runDirectory, List<String> failures) {
        return EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Vision evidence directory must not be null.",
                "Vision evidence directory is missing or unsafe.");
    }

    private static String safeMessage(Exception exception) {
        return EvidenceFiles.safeMessage(exception);
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
            EvidenceArtifact summaryArtifact,
            Path summaryPath,
            String expectedSummary,
            List<String> failures
    ) {

        private static Inspection invalid(List<String> failures) {
            return new Inspection(null, null, null, null, null, List.copyOf(failures));
        }
    }

}
