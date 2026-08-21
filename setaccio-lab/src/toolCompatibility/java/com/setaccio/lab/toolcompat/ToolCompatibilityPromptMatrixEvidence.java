package com.setaccio.lab.toolcompat;

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
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared-v1 evidence lifecycle for one complete paired-execution prompt condition. */
final class ToolCompatibilityPromptMatrixEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier verifier;
    private final ToolCompatibilityAnalyzer analyzer;
    private final ToolCompatibilityReport report;

    ToolCompatibilityPromptMatrixEvidence(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        verifier = new EvidenceVerifier();
        analyzer = new ToolCompatibilityAnalyzer();
        report = new ToolCompatibilityReport();
    }

    Path write(
            Path runDirectory,
            ToolCompatibilityPromptMatrixResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        StagedCondition staged = stage(runDirectory, result, codeBaseline);
        try {
            return finalize(staged);
        } catch (RuntimeException exception) {
            invalidate(staged);
            throw exception;
        }
    }

    StagedCondition stage(
            Path runDirectory,
            ToolCompatibilityPromptMatrixResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        if (result == null || codeBaseline == null) {
            throw new IllegalArgumentException("result and codeBaseline are required");
        }
        ToolCompatibilityAnalysis analysis = analyzer.analyzePromptMatrix(result);
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        requireEmpty(root);

        Path rawPath = root.resolve(ToolCompatibilityPromptMatrixResult.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize raw tool compatibility prompt-matrix result", exception);
        }
        EvidenceFiles.writeNewBytes(
                rawPath,
                rawJson,
                "Failed to write raw tool compatibility prompt-matrix result");

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.renderPromptMatrix(
                result,
                analysis,
                rawArtifact.path(),
                rawArtifact.sha256(),
                codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(
                summaryPath,
                summary,
                "Failed to write tool compatibility prompt-matrix summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                ToolCompatibilityPromptMatrixResult.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                ToolCompatibilityProtocol.EXECUTION_ENGINE,
                ToolCompatibilityPromptMatrixResult.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        return new StagedCondition(root, manifest);
    }

    Path finalize(StagedCondition staged) {
        if (staged == null) {
            throw new IllegalArgumentException("staged prompt-matrix evidence must not be null");
        }
        boolean manifestWritten = false;
        try {
            Path manifestPath = manifestStore.write(staged.root(), staged.manifest());
            manifestWritten = true;
            OfflineResult verification = verify(staged.root());
            if (!verification.valid()) {
                throw new IllegalStateException(
                        "Generated tool compatibility prompt-matrix evidence failed verification: "
                                + String.join(" ", verification.failures()));
            }
            return manifestPath;
        } catch (RuntimeException exception) {
            if (manifestWritten) {
                invalidate(staged);
            }
            throw exception;
        }
    }

    void invalidate(StagedCondition staged) {
        if (staged == null) {
            return;
        }
        Path manifestPath = staged.root().resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        try {
            if (Files.isSymbolicLink(manifestPath)) {
                return;
            }
            if (Files.isRegularFile(manifestPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(manifestPath);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to invalidate generated tool compatibility prompt-matrix manifest", exception);
        }
    }

    OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.root() != null && inspection.manifest() != null) {
            EvidenceVerification verification = verifier.verify(
                    inspection.root(), inspection.manifest());
            failures.addAll(verification.failures());
        }
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Tool compatibility prompt-matrix summary is missing or unsafe.",
                "Tool compatibility prompt-matrix summary is empty.",
                "Tool compatibility prompt-matrix summary drifted from deterministic reanalysis.",
                "Tool compatibility prompt-matrix summary could not be read.");
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of(
                    "Tool compatibility prompt-matrix summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".tool-compatibility-prompt-matrix-summary-",
                "Tool compatibility prompt-matrix summary must not be a symbolic link",
                "Failed to regenerate tool compatibility prompt-matrix summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Tool compatibility prompt-matrix run directory must not be null.",
                "Tool compatibility prompt-matrix run directory is missing or unsafe.");
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
                failures.add("Tool compatibility prompt-matrix manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Tool compatibility prompt-matrix manifest could not be loaded: "
                    + EvidenceFiles.safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(
                root,
                rawArtifact,
                failures,
                "Tool compatibility prompt-matrix raw artifact path escapes the evidence directory.");
        if (rawArtifact != null
                && !ToolCompatibilityPromptMatrixResult.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Tool compatibility prompt-matrix raw artifact must be "
                    + ToolCompatibilityPromptMatrixResult.RAW_FILENAME + ".");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath,
                rawArtifact,
                true,
                failures,
                "Raw tool compatibility prompt-matrix artifact is missing or unsafe.",
                "Raw tool compatibility prompt-matrix artifact is empty.",
                "Raw tool compatibility prompt-matrix artifact size does not match its manifest.",
                "Raw tool compatibility prompt-matrix artifact SHA-256 does not match its manifest.",
                "Raw tool compatibility prompt-matrix artifact could not be verified.");
        ToolCompatibilityPromptMatrixResult result = rawValid
                ? readRawResult(rawPath, failures)
                : null;
        String expectedSummary = null;
        if (result != null && manifest != null && rawArtifact != null) {
            validateSettings(manifest, result, failures);
            try {
                ToolCompatibilityAnalysis analysis = analyzer.analyzePromptMatrix(result);
                expectedSummary = report.renderPromptMatrix(
                        result,
                        analysis,
                        rawArtifact.path(),
                        rawArtifact.sha256(),
                        manifest.codeBaseline());
            } catch (Exception exception) {
                failures.add("Raw tool compatibility prompt-matrix result could not be analyzed: "
                        + EvidenceFiles.safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Tool compatibility prompt-matrix summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated tool compatibility prompt-matrix summary does not match the manifest.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(
                        EvidenceManifestStore.MANIFEST_FILENAME,
                        ToolCompatibilityPromptMatrixResult.RAW_FILENAME,
                        SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in tool compatibility prompt-matrix evidence: ",
                "Unexpected directory is present in tool compatibility prompt-matrix evidence: ",
                "Unexpected artifact is present in tool compatibility prompt-matrix evidence: ",
                "Tool compatibility prompt-matrix evidence directory could not be inspected.");
        return new Inspection(
                root,
                manifest,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!ToolCompatibilityPromptMatrixResult.SUITE.equals(manifest.suite())) {
            failures.add("Tool compatibility prompt-matrix manifest suite is not "
                    + ToolCompatibilityPromptMatrixResult.SUITE + ".");
        }
        if (!ToolCompatibilityProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Tool compatibility prompt-matrix manifest execution engine is not "
                    + ToolCompatibilityProtocol.EXECUTION_ENGINE + ".");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            ToolCompatibilityPromptMatrixResult result,
            List<String> failures
    ) {
        JsonNode expected = objectMapper.valueToTree(
                ToolCompatibilityPromptMatrixResult.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.toString().equals(actual.toString())) {
            failures.add("Tool compatibility prompt-matrix manifest settings differ from the raw locked protocol.");
        }
    }

    private ToolCompatibilityPromptMatrixResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(ToolCompatibilityPromptMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw tool compatibility prompt-matrix JSON could not be read: "
                    + EvidenceFiles.safeMessage(exception) + ".");
            return null;
        }
    }

    private static EvidenceArtifact singleArtifact(
            List<EvidenceArtifact> artifacts,
            String role,
            List<String> failures
    ) {
        return EvidenceFiles.singleArtifact(
                artifacts,
                role,
                failures,
                "Tool compatibility prompt-matrix manifest must declare exactly one " + role + " artifact.");
    }

    private static void requireEmpty(Path root) {
        try (var paths = Files.list(root)) {
            if (paths.findAny().isPresent()) {
                throw new IllegalArgumentException(
                        "Tool compatibility prompt-matrix evidence directory must be empty");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to inspect tool compatibility prompt-matrix evidence directory", exception);
        }
    }

    record OfflineResult(List<String> failures) {

        OfflineResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        boolean valid() {
            return failures.isEmpty();
        }
    }

    record StagedCondition(Path root, EvidenceManifest manifest) {

        StagedCondition {
            if (root == null || manifest == null) {
                throw new IllegalArgumentException("staged prompt-matrix root and manifest are required");
            }
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
