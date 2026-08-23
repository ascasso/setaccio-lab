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

/** Shared-v1 evidence lifecycle for one complete ordered tool-compatibility cohort. */
final class ToolCompatibilityCohortEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier verifier;
    private final ToolCompatibilityCohortReport report;

    ToolCompatibilityCohortEvidence(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        verifier = new EvidenceVerifier();
        report = new ToolCompatibilityCohortReport();
    }

    Path write(
            Path runDirectory,
            ToolCompatibilityCohortResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        if (result == null || codeBaseline == null) {
            throw new IllegalArgumentException("cohort result and codeBaseline are required");
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        requireEmpty(root);

        Path rawPath = root.resolve(ToolCompatibilityCohortResult.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize raw tool compatibility cohort result", exception);
        }
        EvidenceFiles.writeNewBytes(
                rawPath,
                rawJson,
                "Failed to write raw tool compatibility cohort result");
        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);

        String summary = report.render(
                result,
                rawArtifact.path(),
                rawArtifact.sha256(),
                codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(
                summaryPath,
                summary,
                "Failed to write tool compatibility cohort summary");
        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(
                root, summaryPath, SUMMARY_ROLE);

        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                ToolCompatibilityCohortResult.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                ToolCompatibilityProtocol.EXECUTION_ENGINE,
                ToolCompatibilityCohortResult.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        OfflineResult verification = verify(root);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated tool compatibility cohort evidence failed verification: "
                            + String.join(" ", verification.failures()));
        }
        return manifestPath;
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
                "Tool compatibility cohort summary is missing or unsafe.",
                "Tool compatibility cohort summary is empty.",
                "Tool compatibility cohort summary drifted from deterministic reanalysis.",
                "Tool compatibility cohort summary could not be read.");
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of(
                    "Tool compatibility cohort summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".tool-compatibility-cohort-summary-",
                "Tool compatibility cohort summary must not be a symbolic link",
                "Failed to regenerate tool compatibility cohort summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Tool compatibility cohort run directory must not be null.",
                "Tool compatibility cohort run directory is missing or unsafe.");
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
                failures.add(
                        "Tool compatibility cohort manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Tool compatibility cohort manifest could not be loaded: "
                    + EvidenceFiles.safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(
                root,
                rawArtifact,
                failures,
                "Tool compatibility cohort raw artifact path escapes the evidence directory.");
        if (rawArtifact != null
                && !ToolCompatibilityCohortResult.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add(
                    "Tool compatibility cohort raw artifact must use the locked filename.");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath,
                rawArtifact,
                true,
                failures,
                "Raw tool compatibility cohort artifact is missing or unsafe.",
                "Raw tool compatibility cohort artifact is empty.",
                "Raw tool compatibility cohort artifact size does not match its manifest.",
                "Raw tool compatibility cohort artifact SHA-256 does not match its manifest.",
                "Raw tool compatibility cohort artifact could not be verified.");
        ToolCompatibilityCohortResult result = rawValid
                ? readRawResult(rawPath, failures)
                : null;
        String expectedSummary = null;
        if (result != null && manifest != null && rawArtifact != null) {
            validateSettings(manifest, result, failures);
            try {
                expectedSummary = report.render(
                        result,
                        rawArtifact.path(),
                        rawArtifact.sha256(),
                        manifest.codeBaseline());
            } catch (Exception exception) {
                failures.add("Raw tool compatibility cohort result could not be analyzed: "
                        + EvidenceFiles.safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Tool compatibility cohort summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated tool compatibility cohort summary does not match the manifest.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(
                        EvidenceManifestStore.MANIFEST_FILENAME,
                        ToolCompatibilityCohortResult.RAW_FILENAME,
                        SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in tool compatibility cohort evidence: ",
                "Unexpected directory is present in tool compatibility cohort evidence: ",
                "Unexpected artifact is present in tool compatibility cohort evidence: ",
                "Tool compatibility cohort evidence directory could not be inspected.");
        return new Inspection(
                root,
                manifest,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static void validateEnvelope(
            EvidenceManifest manifest,
            List<String> failures
    ) {
        if (!ToolCompatibilityCohortResult.SUITE.equals(manifest.suite())) {
            failures.add("Tool compatibility cohort manifest suite is incorrect.");
        }
        if (!ToolCompatibilityProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Tool compatibility cohort manifest execution engine is incorrect.");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            ToolCompatibilityCohortResult result,
            List<String> failures
    ) {
        JsonNode expected = objectMapper.valueToTree(
                ToolCompatibilityCohortResult.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.toString().equals(actual.toString())) {
            failures.add(
                    "Tool compatibility cohort manifest settings differ from the raw protocol.");
        }
    }

    private ToolCompatibilityCohortResult readRawResult(
            Path rawPath,
            List<String> failures
    ) {
        try {
            return objectMapper.readerFor(ToolCompatibilityCohortResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw tool compatibility cohort JSON could not be read: "
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
                "Tool compatibility cohort manifest must declare exactly one "
                        + role + " artifact.");
    }

    private static void requireEmpty(Path root) {
        try (var paths = Files.list(root)) {
            if (paths.findAny().isPresent()) {
                throw new IllegalArgumentException(
                        "Tool compatibility cohort evidence directory must be empty");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to inspect tool compatibility cohort evidence directory",
                    exception);
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
