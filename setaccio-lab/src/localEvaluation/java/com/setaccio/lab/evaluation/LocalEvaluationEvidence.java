package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceArtifact;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceVerification;
import com.setaccio.lab.evidence.EvidenceVerifier;
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

final class LocalEvaluationEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final LocalEvaluationAnalyzer analyzer;
    private final LocalEvaluationReport report;

    LocalEvaluationEvidence(
            ObjectMapper objectMapper,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new LocalEvaluationAnalyzer(prompt, catalog, review);
        report = new LocalEvaluationReport();
    }

    Path write(Path runDirectory, LocalEvaluationResult result) {
        return write(
                runDirectory,
                result,
                EvidenceProvenance.captureCodeBaseline(Path.of("")));
    }

    Path write(
            Path runDirectory,
            LocalEvaluationResult result,
            EvidenceCodeBaseline codeBaseline
    ) {
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        LocalEvaluationAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException(
                    "Local evaluation result failed integrity checks: "
                            + String.join(" ", analysis.integrityFailures()));
        }
        Path root = requireWritableRunDirectory(runDirectory);
        Path rawPath = root.resolve(LocalEvaluationProtocol.RAW_FILENAME);
        try {
            byte[] rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
            Files.write(rawPath, rawJson, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write raw local evaluation result", exception);
        }

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(
                result,
                analysis,
                rawArtifact.path(),
                rawArtifact.sha256(),
                codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        try {
            Files.writeString(
                    summaryPath,
                    summary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write local evaluation summary", exception);
        }

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                LocalEvaluationProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                LocalEvaluationProtocol.EXECUTION_ENGINE,
                LocalEvaluationProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        EvidenceVerification verification = evidenceVerifier.verify(root, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated local evaluation evidence failed verification: "
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
            return new OfflineResult(List.of("Local evaluation summary could not be regenerated."));
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
                failures.add("Local evaluation manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Local evaluation manifest could not be loaded: "
                    + safeMessage(exception) + ".");
        }

        Path rawPath = resolveArtifact(root, rawArtifact, failures);
        if (rawArtifact != null
                && !LocalEvaluationProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Local evaluation raw artifact must be local-evaluation-results.json.");
        }
        boolean rawValid = verifyRawArtifact(rawPath, rawArtifact, failures);
        LocalEvaluationResult result = rawValid ? readRawResult(rawPath, failures) : null;
        LocalEvaluationAnalyzer.MatrixAnalysis analysis = null;
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
                            rawArtifact.sha256(),
                            manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw local evaluation could not be analyzed: "
                        + safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Local evaluation summary artifact must be SUMMARY.md.");
            }
            validateRegeneratedSummaryDescriptor(summaryArtifact, expectedSummary, failures);
        }
        validateInputLayout(root, failures);
        return new Inspection(
                root,
                manifest,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private static Path requireWritableRunDirectory(Path runDirectory) {
        if (runDirectory == null) {
            throw new IllegalArgumentException("runDirectory must not be null");
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("runDirectory must be an existing regular directory");
        }
        return root;
    }

    private static Path normalizedRunDirectory(Path runDirectory, List<String> failures) {
        if (runDirectory == null) {
            failures.add("Local evaluation run directory must not be null.");
            return null;
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Local evaluation run directory is missing or unsafe.");
            return null;
        }
        return root;
    }

    private void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!LocalEvaluationProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Local evaluation manifest suite is not local-fact-check-evaluation.");
        }
        if (!LocalEvaluationProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Local evaluation manifest execution engine is not spring-ai-fact-checking-evaluator.");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            LocalEvaluationResult result,
            List<String> failures
    ) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(LocalEvaluationProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.toString().equals(actual.toString())) {
            failures.add("Local evaluation manifest settings differ from the raw locked protocol.");
        }
    }

    private LocalEvaluationResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(LocalEvaluationResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw local evaluation JSON could not be read: "
                    + safeMessage(exception) + ".");
            return null;
        }
    }

    private static EvidenceArtifact singleArtifact(
            List<EvidenceArtifact> artifacts,
            String role,
            List<String> failures
    ) {
        List<EvidenceArtifact> matches = artifacts.stream()
                .filter(artifact -> role.equals(artifact.role()))
                .toList();
        if (matches.size() != 1) {
            failures.add("Local evaluation manifest must declare exactly one " + role + " artifact.");
            return null;
        }
        return matches.getFirst();
    }

    private static Path resolveArtifact(
            Path root,
            EvidenceArtifact artifact,
            List<String> failures
    ) {
        if (artifact == null) {
            return null;
        }
        Path resolved = root.resolve(artifact.path()).normalize();
        if (!resolved.startsWith(root)) {
            failures.add("Local evaluation raw artifact path escapes the evidence directory.");
            return null;
        }
        return resolved;
    }

    private static boolean verifyRawArtifact(
            Path rawPath,
            EvidenceArtifact artifact,
            List<String> failures
    ) {
        if (rawPath == null || artifact == null) {
            return false;
        }
        if (Files.isSymbolicLink(rawPath)
                || !Files.isRegularFile(rawPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Raw local evaluation artifact is missing or unsafe.");
            return false;
        }
        try {
            long size = Files.size(rawPath);
            if (size == 0) {
                failures.add("Raw local evaluation artifact is empty.");
                return false;
            }
            if (size != artifact.sizeBytes()) {
                failures.add("Raw local evaluation artifact size does not match its manifest.");
            }
            if (!EvidenceIntegrity.sha256(rawPath).equals(artifact.sha256())) {
                failures.add("Raw local evaluation artifact SHA-256 does not match its manifest.");
                return false;
            }
            return true;
        } catch (Exception exception) {
            failures.add("Raw local evaluation artifact could not be verified.");
            return false;
        }
    }

    private static void validateRegeneratedSummaryDescriptor(
            EvidenceArtifact summaryArtifact,
            String expectedSummary,
            List<String> failures
    ) {
        if (expectedSummary == null) {
            return;
        }
        byte[] bytes = expectedSummary.getBytes(StandardCharsets.UTF_8);
        if (summaryArtifact.sizeBytes() != bytes.length
                || !summaryArtifact.sha256().equals(EvidenceIntegrity.sha256(bytes))) {
            failures.add("Regenerated local evaluation summary does not match the manifest.");
        }
    }

    private static void validateInputLayout(Path root, List<String> failures) {
        Set<String> allowed = Set.of(
                EvidenceManifestStore.MANIFEST_FILENAME,
                LocalEvaluationProtocol.RAW_FILENAME,
                SUMMARY_FILENAME);
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    failures.add("Unsafe symbolic link is present in local evaluation evidence: "
                            + relative + ".");
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    failures.add("Unexpected directory is present in local evaluation evidence: "
                            + relative + ".");
                } else if (!allowed.contains(relative)) {
                    failures.add("Unexpected artifact is present in local evaluation evidence: "
                            + relative + ".");
                }
            });
        } catch (Exception exception) {
            failures.add("Local evaluation evidence directory could not be inspected.");
        }
    }

    private static void verifySummary(
            Inspection inspection,
            Set<String> failures
    ) {
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return;
        }
        Path summary = inspection.summaryPath();
        if (Files.isSymbolicLink(summary)
                || !Files.isRegularFile(summary, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Local evaluation summary is missing or unsafe.");
            return;
        }
        try {
            String actual = Files.readString(summary, StandardCharsets.UTF_8);
            if (!inspection.expectedSummary().equals(actual)) {
                failures.add("Local evaluation summary drifted from deterministic reanalysis.");
            }
        } catch (Exception exception) {
            failures.add("Local evaluation summary could not be read.");
        }
    }

    private static void writeSummaryAtomically(Path summaryPath, String summary) {
        Path root = summaryPath.getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".local-evaluation-summary-", ".tmp");
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
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, summaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to regenerate local evaluation summary", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // The primary write or move failure remains authoritative.
                }
            }
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
