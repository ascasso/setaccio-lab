package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceVerification;
import com.setaccio.lab.evidence.EvidenceVerifier;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
        try {
            byte[] rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
            Files.write(rawPath, rawJson, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write raw vision matrix result", e);
        }

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(runDirectory, rawPath, RAW_ROLE);
        String summary = report.render(result, analysis, rawArtifact.path(), rawArtifact.sha256());
        Path summaryPath = runDirectory.resolve(SUMMARY_FILENAME);
        try {
            Files.writeString(
                    summaryPath,
                    summary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write vision matrix summary", e);
        }

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
        List<EvidenceArtifact> matches = artifacts.stream()
                .filter(artifact -> role.equals(artifact.role()))
                .toList();
        if (matches.size() != 1) {
            failures.add("Vision matrix manifest must declare exactly one " + role + " artifact.");
            return null;
        }
        return matches.getFirst();
    }

    private static Path resolveArtifact(
            Path root,
            EvidenceArtifact artifact,
            List<String> failures) {
        if (artifact == null) {
            return null;
        }
        Path resolved = root.resolve(artifact.path()).normalize();
        if (!resolved.startsWith(root)) {
            failures.add("Vision matrix raw artifact path escapes the evidence directory.");
            return null;
        }
        return resolved;
    }

    private static boolean verifyRawArtifact(
            Path rawPath,
            EvidenceArtifact artifact,
            List<String> failures) {
        if (rawPath == null || artifact == null) {
            return false;
        }
        if (Files.isSymbolicLink(rawPath)
                || !Files.isRegularFile(rawPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Raw vision matrix artifact is missing or unsafe.");
            return false;
        }
        try {
            long size = Files.size(rawPath);
            if (size == 0) {
                failures.add("Raw vision matrix artifact is empty.");
                return false;
            }
            if (size != artifact.sizeBytes()) {
                failures.add("Raw vision matrix artifact size does not match its manifest.");
            }
            if (!EvidenceIntegrity.sha256(rawPath).equals(artifact.sha256())) {
                failures.add("Raw vision matrix artifact SHA-256 does not match its manifest.");
                return false;
            }
            return true;
        } catch (Exception e) {
            failures.add("Raw vision matrix artifact could not be verified.");
            return false;
        }
    }

    private static void validateRegeneratedSummaryDescriptor(
            EvidenceArtifact summaryArtifact,
            String expectedSummary,
            List<String> failures) {
        if (expectedSummary == null) {
            return;
        }
        byte[] bytes = expectedSummary.getBytes(StandardCharsets.UTF_8);
        if (summaryArtifact.sizeBytes() != bytes.length
                || !summaryArtifact.sha256().equals(EvidenceIntegrity.sha256(bytes))) {
            failures.add("Regenerated vision matrix summary does not match the manifest.");
        }
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
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    failures.add("Unsafe symbolic link is present in saved vision evidence: " + relative + ".");
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !allowed.contains(relative)) {
                    failures.add("Unexpected artifact is present in saved vision evidence: " + relative + ".");
                }
            });
        } catch (Exception e) {
            failures.add("Saved vision evidence directory could not be inspected.");
        }
    }

    private static void verifySummary(Inspection inspection, Set<String> failures) {
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return;
        }
        Path summaryPath = inspection.summaryPath();
        if (Files.isSymbolicLink(summaryPath)
                || !Files.isRegularFile(summaryPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Vision matrix summary is missing or unsafe.");
            return;
        }
        try {
            if (Files.size(summaryPath) == 0) {
                failures.add("Vision matrix summary is empty.");
            } else if (!Files.readString(summaryPath, StandardCharsets.UTF_8)
                    .equals(inspection.expectedSummary())) {
                failures.add("Vision matrix summary differs from deterministic offline reanalysis.");
            }
        } catch (Exception e) {
            failures.add("Vision matrix summary could not be verified.");
        }
    }

    private static void writeSummaryAtomically(Path summaryPath, String summary) {
        Path root = summaryPath.getParent();
        Path temporary = null;
        try {
            if (Files.isSymbolicLink(summaryPath)) {
                throw new IllegalArgumentException("Vision matrix summary must not be a symbolic link");
            }
            temporary = Files.createTempFile(root, ".vision-summary-", ".tmp");
            Files.writeString(
                    temporary,
                    summary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        summaryPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, summaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to regenerate vision matrix summary", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // Preserve the primary failure.
                }
            }
        }
    }

    private static Path normalizedRunDirectory(Path runDirectory, List<String> failures) {
        if (runDirectory == null) {
            failures.add("Vision evidence directory must not be null.");
            return null;
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Vision evidence directory is missing or unsafe.");
            return null;
        }
        return root;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
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
