package com.setaccio.lab.chatmatrix;

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

final class ChatMatrixEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier verifier;
    private final ChatMatrixAnalyzer analyzer;
    private final ChatMatrixReport report;

    ChatMatrixEvidence(ObjectMapper objectMapper, ChatPromptCatalog catalog) {
        if (objectMapper == null || catalog == null) {
            throw new IllegalArgumentException("Chat matrix evidence dependencies must be complete");
        }
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        verifier = new EvidenceVerifier();
        analyzer = new ChatMatrixAnalyzer(catalog);
        report = new ChatMatrixReport();
    }

    Path write(Path runDirectory, ChatMatrixResult result) {
        return write(runDirectory, result, EvidenceProvenance.captureCodeBaseline(Path.of("")));
    }

    Path write(Path runDirectory, ChatMatrixResult result, EvidenceCodeBaseline codeBaseline) {
        if (codeBaseline == null) {
            throw new IllegalArgumentException("codeBaseline must not be null");
        }
        ChatMatrixAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);
        if (!analysis.valid()) {
            throw new IllegalArgumentException(
                    "Chat matrix result failed integrity checks: "
                            + String.join(" ", analysis.integrityFailures()));
        }
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        Path rawPath = root.resolve(ChatMatrixProtocol.RAW_FILENAME);
        byte[] rawJson;
        try {
            rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write raw chat matrix result", exception);
        }
        EvidenceFiles.writeNewBytes(rawPath, rawJson, "Failed to write raw chat matrix result");

        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(root, rawPath, RAW_ROLE);
        String summary = report.render(
                result,
                analysis,
                rawArtifact.path(),
                rawArtifact.sha256(),
                codeBaseline);
        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write chat matrix summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(root, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                ChatMatrixProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                EvidenceProvenance.detectFrameworkVersions(),
                ChatMatrixProtocol.EXECUTION_ENGINE,
                ChatMatrixProtocol.manifestSettings(result),
                List.of(rawArtifact, summaryArtifact));
        Path manifestPath = manifestStore.write(root, manifest);
        EvidenceVerification verification = verifier.verify(root, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated chat matrix evidence failed verification: "
                            + String.join(" ", verification.failures()));
        }
        return manifestPath;
    }

    OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.manifest() != null) {
            failures.addAll(verifier.verify(inspection.root(), inspection.manifest()).failures());
        }
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Chat matrix summary is missing or unsafe.",
                "Chat matrix summary is empty.",
                "Chat matrix summary drifted from deterministic reanalysis.",
                "Chat matrix summary could not be read.");
        return new OfflineResult(List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.failures());
        }
        if (inspection.summaryPath() == null || inspection.expectedSummary() == null) {
            return new OfflineResult(List.of("Chat matrix summary could not be regenerated."));
        }
        EvidenceFiles.replaceTextAtomically(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                ".chat-matrix-summary-",
                null,
                "Failed to regenerate chat matrix summary");
        return verify(runDirectory);
    }

    private Inspection inspectInputs(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Chat matrix run directory must not be null.",
                "Chat matrix run directory is missing or unsafe.");
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
                failures.add("Chat matrix manifest must declare exactly two artifacts.");
            }
        } catch (Exception exception) {
            failures.add("Chat matrix manifest could not be loaded: "
                    + EvidenceFiles.safeMessage(exception) + ".");
        }

        Path rawPath = EvidenceFiles.resolveArtifact(
                root,
                rawArtifact,
                failures,
                "Chat matrix raw artifact path escapes the evidence directory.");
        if (rawArtifact != null && !ChatMatrixProtocol.RAW_FILENAME.equals(rawArtifact.path())) {
            failures.add("Chat matrix raw artifact must be chat-matrix-results.json.");
        }
        boolean rawValid = EvidenceFiles.verifyArtifact(
                rawPath,
                rawArtifact,
                true,
                failures,
                "Raw chat matrix artifact is missing or unsafe.",
                "Raw chat matrix artifact is empty.",
                "Raw chat matrix artifact size does not match its manifest.",
                "Raw chat matrix artifact SHA-256 does not match its manifest.",
                "Raw chat matrix artifact could not be verified.");
        ChatMatrixResult result = rawValid ? readRawResult(rawPath, failures) : null;
        String expectedSummary = null;
        if (result != null) {
            validateSettings(manifest, result, failures);
            try {
                ChatMatrixAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null && manifest != null) {
                    expectedSummary = report.render(
                            result,
                            analysis,
                            rawArtifact.path(),
                            rawArtifact.sha256(),
                            manifest.codeBaseline());
                }
            } catch (Exception exception) {
                failures.add("Raw chat matrix could not be analyzed: "
                        + EvidenceFiles.safeMessage(exception) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Chat matrix summary artifact must be SUMMARY.md.");
            }
            EvidenceFiles.validateTextDescriptor(
                    summaryArtifact,
                    expectedSummary,
                    failures,
                    "Regenerated chat matrix summary does not match the manifest.");
        }
        EvidenceFiles.validateLayout(
                root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME, ChatMatrixProtocol.RAW_FILENAME, SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in chat matrix evidence: ",
                "Unexpected directory is present in chat matrix evidence: ",
                "Unexpected artifact is present in chat matrix evidence: ",
                "Chat matrix evidence directory could not be inspected.");
        return new Inspection(
                root,
                manifest,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures)));
    }

    private void validateEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!ChatMatrixProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Chat matrix manifest suite is not ollama-chat-matrix.");
        }
        if (!ChatMatrixProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Chat matrix manifest execution engine is not spring-ai-chat-invocation.");
        }
    }

    private void validateSettings(
            EvidenceManifest manifest,
            ChatMatrixResult result,
            List<String> failures
    ) {
        if (manifest == null) {
            return;
        }
        JsonNode expected = objectMapper.valueToTree(ChatMatrixProtocol.manifestSettings(result));
        JsonNode actual = objectMapper.valueToTree(manifest.settings());
        if (!expected.toString().equals(actual.toString())) {
            failures.add("Chat matrix manifest settings differ from the raw locked protocol.");
        }
    }

    private ChatMatrixResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readerFor(ChatMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawPath.toFile());
        } catch (Exception exception) {
            failures.add("Raw chat matrix JSON could not be read: "
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
                "Chat matrix manifest must declare exactly one " + role + " artifact.");
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
