package com.setaccio.lab.thinking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
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

/** Writes, verifies, and deterministically reanalyzes one saved diagnostic run. */
public final class ThinkingDiagnosticEvidence {

    public static final String RAW_ROLE = "raw-result";
    public static final String SUMMARY_ROLE = "summary";
    public static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier verifier;
    private final ThinkingDiagnosticResultCodec resultCodec;
    private final ThinkingDiagnosticAnalyzer analyzer;
    private final ThinkingDiagnosticReport report;

    public ThinkingDiagnosticEvidence(ObjectMapper objectMapper, LocalFactCheckFixtureCatalog catalog) {
        if (objectMapper == null || catalog == null) {
            throw new IllegalArgumentException("Thinking diagnostic evidence dependencies must be complete");
        }
        this.objectMapper = objectMapper;
        this.manifestStore = new EvidenceManifestStore(objectMapper);
        this.verifier = new EvidenceVerifier();
        this.resultCodec = new ThinkingDiagnosticResultCodec(objectMapper);
        this.analyzer = new ThinkingDiagnosticAnalyzer(catalog);
        this.report = new ThinkingDiagnosticReport();
    }

    public Path write(Path runDirectory, ThinkingDiagnosticResult result, EvidenceCodeBaseline codeBaseline) {
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException("Thinking diagnostic result failed integrity checks: "
                    + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(ThinkingDiagnosticProtocol.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = resultCodec.write(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write raw thinking diagnostic result", exception);
        }
        EvidenceFiles.writeNewBytes(rawPath, rawJson, "Failed to write raw thinking diagnostic result");

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(
                result, analysis, rawArtifact.path(), rawArtifact.sha256(), codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write thinking diagnostic summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                ThinkingDiagnosticProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                ThinkingDiagnosticProtocol.manifestExecutionEngine(result.protocolVersion()),
                ThinkingDiagnosticProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        EvidenceVerification verification = verifier.verify(root, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException("Generated thinking diagnostic evidence failed verification: "
                    + String.join(" ", verification.failures()));
        }
        return manifestPath;
    }

    public OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.manifest() != null) {
            failures.addAll(verifier.verify(inspection.root(), inspection.manifest()).failures());
        }
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Thinking diagnostic summary is missing or unsafe.",
                "Thinking diagnostic summary is empty.",
                "Thinking diagnostic summary drifted from deterministic reanalysis.",
                "Thinking diagnostic summary could not be read.");
        return new OfflineResult(List.copyOf(failures));
    }

    public OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of("Thinking diagnostic summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".thinking-diagnostic-summary-",
                null,
                "Failed to regenerate thinking diagnostic summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Thinking diagnostic run directory must not be null.",
                "Thinking diagnostic run directory is missing or unsafe.");
        if (root == null) {
            return Inspection.invalid(failures);
        }

        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        try {
            manifest = manifestStore.read(root);
            if (!ThinkingDiagnosticProtocol.SUITE.equals(manifest.suite())) {
                failures.add("Thinking diagnostic manifest suite is not "
                        + ThinkingDiagnosticProtocol.SUITE + ".");
            }
            rawArtifact = EvidenceFiles.singleArtifact(manifest.artifacts(), RAW_ROLE, failures,
                    "Thinking diagnostic manifest must declare exactly one raw-result artifact.");
            summaryArtifact = EvidenceFiles.singleArtifact(manifest.artifacts(), SUMMARY_ROLE, failures,
                    "Thinking diagnostic manifest must declare exactly one summary artifact.");
            if (manifest.artifacts().size() != 2) {
                failures.add("Thinking diagnostic manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Thinking diagnostic manifest could not be loaded: "
                    + EvidenceFiles.safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(root, rawArtifact, failures,
                "Thinking diagnostic raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !ThinkingDiagnosticProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Thinking diagnostic raw artifact must be "
                    + ThinkingDiagnosticProtocol.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath, rawArtifact, true, failures,
                "Raw thinking diagnostic artifact is missing or unsafe.",
                "Raw thinking diagnostic artifact is empty.",
                "Raw thinking diagnostic artifact size does not match its manifest.",
                "Raw thinking diagnostic artifact SHA-256 does not match its manifest.",
                "Raw thinking diagnostic artifact could not be verified.");
        ThinkingDiagnosticResult result = rawValid ? readRawResult(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            if (manifest != null && !ThinkingDiagnosticProtocol.manifestExecutionEngine(
                    result.protocolVersion()).equals(manifest.executionEngine())) {
                failures.add("Thinking diagnostic manifest execution engine is unexpected for protocol v"
                        + result.protocolVersion() + ".");
            }
            validateSettings(manifest, result, failures);
            try {
                ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null && manifest != null) {
                    expectedSummary = report.render(
                            result, analysis, rawArtifact.path(), rawArtifact.sha256(),
                            manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw thinking diagnostic could not be analyzed: "
                        + EvidenceFiles.safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Thinking diagnostic summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(summaryArtifact, expectedSummary, failures,
                    "Regenerated thinking diagnostic summary does not match the manifest.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME,
                        ThinkingDiagnosticProtocol.RAW_FILENAME, SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in thinking diagnostic evidence: ",
                "Unexpected directory is present in thinking diagnostic evidence: ",
                "Unexpected artifact is present in thinking diagnostic evidence: ",
                "Thinking diagnostic evidence directory could not be inspected.");
        return new Inspection(root, manifest, summaryPath, expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateSettings(
            EvidenceManifest manifest,
            ThinkingDiagnosticResult result,
            List<String> failures
    ) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(ThinkingDiagnosticProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.toString().equals(actual.toString())) {
            failures.add("Thinking diagnostic manifest settings differ from the raw locked protocol.");
        }
    }

    private ThinkingDiagnosticResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return resultCodec.read(rawPath);
        } catch (Exception exception) {
            failures.add("Raw thinking diagnostic JSON could not be read: "
                    + EvidenceFiles.safeMessage(exception) + ".");
            return null;
        }
    }

    public record OfflineResult(List<String> failures) {
        public OfflineResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean valid() {
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
