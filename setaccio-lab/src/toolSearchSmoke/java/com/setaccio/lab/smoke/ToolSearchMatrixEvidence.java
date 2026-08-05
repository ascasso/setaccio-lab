package com.setaccio.lab.smoke;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSearchMatrixEvidence {

    static final String RAW_ROLE = "raw-result";
    static final String SUMMARY_ROLE = "summary";
    static final String SUMMARY_FILENAME = "SUMMARY.md";
    private static final Set<String> SETTINGS_KEYS = Set.of(
            "issues",
            "models",
            "caseIds",
            "prompts",
            "toolNames",
            "runSettings",
            "executionStrategy",
            "toolSearchIndexType",
            "ollamaBaseUrl",
            "pullModelStrategy",
            "canonicalExpectationSha256");

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier evidenceVerifier;
    private final ToolSearchMatrixAnalyzer analyzer;
    private final ToolSearchMatrixReport report;

    ToolSearchMatrixEvidence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
        evidenceVerifier = new EvidenceVerifier();
        analyzer = new ToolSearchMatrixAnalyzer(objectMapper);
        report = new ToolSearchMatrixReport(objectMapper);
    }

    Path writeVersionOne(
            Path runDirectory,
            Path rawJson,
            ToolBenchmarkComparisonResult result,
            ToolSearchMatrixAnalyzer.MatrixAnalysis analysis) {
        List<ToolBenchmarkPrompt> prompts = ToolSearchMatrixProtocol.canonicalPrompts();
        List<String> tools = ToolSearchMatrixProtocol.toolNames();
        EvidenceArtifact rawArtifact = EvidenceIntegrity.describe(runDirectory, rawJson, RAW_ROLE);
        String summary = report.render(analysis, rawArtifact.path(), rawArtifact.sha256());
        Path summaryPath = runDirectory.resolve(SUMMARY_FILENAME);
        EvidenceFiles.writeNewText(summaryPath, summary, "Failed to write Tool Search matrix summary");

        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(runDirectory, summaryPath, SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                ToolSearchMatrixProtocol.SUITE,
                runDirectory.getFileName().toString(),
                Instant.now(),
                EvidenceProvenance.captureCodeBaseline(Path.of("")),
                EvidenceProvenance.detectFrameworkVersions(),
                ToolSearchMatrixProtocol.EXECUTION_ENGINE,
                ToolSearchMatrixProtocol.manifestSettings(objectMapper, prompts, tools, result),
                List.of(rawArtifact, summaryArtifact)
        );
        Path manifestPath = manifestStore.write(runDirectory, manifest);
        EvidenceVerification verification = evidenceVerifier.verify(runDirectory, manifest);
        if (!verification.valid()) {
            throw new IllegalStateException(
                    "Generated Tool Search evidence failed verification: "
                            + String.join(" ", verification.failures()));
        }
        return manifestPath;
    }

    OfflineResult verify(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        LinkedHashSet<String> failures = new LinkedHashSet<>(inspection.failures());
        if (inspection.manifestFormat() == ManifestFormat.VERSION_1 && inspection.manifest() != null) {
            failures.addAll(evidenceVerifier.verify(runDirectory, inspection.manifest()).failures());
        } else if (inspection.manifestFormat() == ManifestFormat.LEGACY_V0) {
            verifyLegacyLayout(inspection, failures);
        }
        verifySummary(inspection, failures);
        return new OfflineResult(inspection.manifestFormat(), List.copyOf(failures));
    }

    OfflineResult reanalyze(Path runDirectory) {
        Inspection inspection = inspectInputs(runDirectory);
        if (!inspection.failures().isEmpty()) {
            return new OfflineResult(inspection.manifestFormat(), inspection.failures());
        }
        if (inspection.expectedSummary() == null || inspection.summaryPath() == null) {
            return new OfflineResult(
                    inspection.manifestFormat(),
                    List.of("Tool Search summary could not be regenerated."));
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

        JsonNode manifestNode = readManifestNode(root, failures);
        if (manifestNode == null) {
            return Inspection.invalid(failures);
        }

        ManifestFormat format = manifestNode.has("manifestVersion")
                ? ManifestFormat.VERSION_1
                : ManifestFormat.LEGACY_V0;
        EvidenceManifest manifest = null;
        EvidenceArtifact rawArtifact = null;
        EvidenceArtifact summaryArtifact = null;
        Map<String, Object> settings = Map.of();

        if (format == ManifestFormat.VERSION_1) {
            try {
                manifest = manifestStore.read(root);
                validateVersionOneEnvelope(manifest, failures);
                settings = manifest.settings();
                rawArtifact = singleArtifact(manifest.artifacts(), RAW_ROLE, failures);
                summaryArtifact = singleArtifact(manifest.artifacts(), SUMMARY_ROLE, failures);
                if (manifest.artifacts().size() != 2) {
                    failures.add("Version 1 Tool Search manifest must declare exactly two artifacts.");
                }
            } catch (Exception e) {
                failures.add("Version 1 Tool Search manifest could not be loaded: " + safeMessage(e) + ".");
            }
        } else {
            LegacyManifest legacy = readLegacyManifest(manifestNode, root, failures);
            if (legacy != null) {
                rawArtifact = legacy.rawArtifact();
                settings = legacy.settings();
            }
        }

        Path rawPath = resolveArtifact(root, rawArtifact, failures);
        boolean rawValid = verifyRawArtifact(rawPath, rawArtifact, failures);
        ToolBenchmarkComparisonResult result = rawValid ? readRawResult(rawPath, failures) : null;
        validateSettings(settings, result, failures);

        ToolSearchMatrixAnalyzer.MatrixAnalysis analysis = null;
        String expectedSummary = null;
        if (result != null) {
            try {
                analysis = analyzer.analyze(
                        result,
                        ToolSearchMatrixProtocol.MODELS,
                        ToolSearchMatrixProtocol.canonicalPrompts(),
                        ToolSearchMatrixProtocol.toolNames());
                failures.addAll(analysis.integrityFailures());
                if (rawArtifact != null) {
                    expectedSummary = report.render(analysis, rawArtifact.path(), rawArtifact.sha256());
                }
            } catch (Exception e) {
                failures.add("Raw Tool Search matrix could not be analyzed: " + safeMessage(e) + ".");
            }
        }

        Path summaryPath = root.resolve(SUMMARY_FILENAME);
        if (format == ManifestFormat.VERSION_1 && summaryArtifact != null) {
            if (!SUMMARY_FILENAME.equals(summaryArtifact.path())) {
                failures.add("Version 1 summary artifact must be SUMMARY.md.");
            }
            validateRegeneratedSummaryDescriptor(summaryArtifact, expectedSummary, failures);
        }
        validateInputLayout(root, rawArtifact, failures);
        return new Inspection(
                format,
                manifest,
                rawArtifact,
                summaryArtifact,
                rawPath,
                summaryPath,
                expectedSummary,
                List.copyOf(new LinkedHashSet<>(failures))
        );
    }

    private void validateVersionOneEnvelope(EvidenceManifest manifest, List<String> failures) {
        if (!ToolSearchMatrixProtocol.SUITE.equals(manifest.suite())) {
            failures.add("Version 1 manifest suite is not tool-search-matrix.");
        }
        if (!ToolSearchMatrixProtocol.EXECUTION_ENGINE.equals(manifest.executionEngine())) {
            failures.add("Version 1 manifest execution engine is not spring-ai-direct.");
        }
    }

    private LegacyManifest readLegacyManifest(JsonNode node, Path root, List<String> failures) {
        String rawFile = text(node, "rawJson", failures);
        String rawSha256 = text(node, "rawJsonSha256", failures);
        if (!node.path("workingTreeDirty").isBoolean()) {
            failures.add("Legacy manifest workingTreeDirty is missing or malformed.");
        }
        text(node, "generatedAt", failures);
        text(node, "gitCommit", failures);
        text(node, "springAiVersion", failures);

        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        for (String key : SETTINGS_KEYS) {
            JsonNode value = node.get(key);
            if (value == null) {
                failures.add("Legacy manifest setting is missing: " + key + ".");
            } else {
                settings.put(key, objectMapper.convertValue(value, Object.class));
            }
        }
        if (rawFile == null || rawSha256 == null) {
            return null;
        }

        try {
            EvidenceArtifact safePath = new EvidenceArtifact(rawFile, RAW_ROLE, 0, rawSha256);
            Path rawPath = root.resolve(safePath.path()).normalize();
            long size = Files.isRegularFile(rawPath, LinkOption.NOFOLLOW_LINKS) ? Files.size(rawPath) : 0;
            return new LegacyManifest(
                    new EvidenceArtifact(rawFile, RAW_ROLE, size, rawSha256),
                    Map.copyOf(settings));
        } catch (Exception e) {
            failures.add("Legacy raw artifact declaration is malformed: " + safeMessage(e) + ".");
            return null;
        }
    }

    private void validateSettings(
            Map<String, Object> settings,
            ToolBenchmarkComparisonResult result,
            List<String> failures) {
        if (!settings.keySet().equals(SETTINGS_KEYS)) {
            failures.add("Tool Search manifest settings differ from the locked protocol fields.");
        }

        List<ToolBenchmarkPrompt> canonicalPrompts = ToolSearchMatrixProtocol.canonicalPrompts();
        compareSetting(settings, "issues", ToolSearchMatrixProtocol.ISSUES,
                new TypeReference<List<Map<String, Object>>>() {}, failures);
        compareSetting(settings, "models", ToolSearchMatrixProtocol.MODELS,
                new TypeReference<List<String>>() {}, failures);
        compareSetting(settings, "caseIds", ToolSearchMatrixProtocol.CASE_IDS,
                new TypeReference<List<String>>() {}, failures);
        compareSetting(settings, "prompts", canonicalPrompts,
                new TypeReference<List<ToolBenchmarkPrompt>>() {}, failures);
        compareSetting(settings, "toolNames", ToolSearchMatrixProtocol.toolNames(),
                new TypeReference<List<String>>() {}, failures);
        compareSetting(settings, "runSettings", ToolSearchMatrixProtocol.SETTINGS,
                new TypeReference<ToolBenchmarkRunSettings>() {}, failures);
        compareText(settings, "executionStrategy", ToolSearchMatrixProtocol.EXECUTION_STRATEGY, failures);
        compareText(settings, "toolSearchIndexType", ToolSearchMatrixProtocol.INDEX_TYPE, failures);
        compareText(settings, "pullModelStrategy", ToolSearchMatrixProtocol.PULL_MODEL_STRATEGY, failures);
        compareText(
                settings,
                "canonicalExpectationSha256",
                ToolSearchMatrixProtocol.canonicalExpectationSha256(objectMapper, canonicalPrompts),
                failures);
        if (result != null) {
            compareText(settings, "ollamaBaseUrl", result.ollamaBaseUrl(), failures);
            if (!ToolSearchMatrixProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())) {
                failures.add("Raw result execution strategy drifted from the locked protocol.");
            }
            if (!ToolSearchMatrixProtocol.INDEX_TYPE.equals(result.toolSearchIndexType())) {
                failures.add("Raw result Tool Search index drifted from the locked protocol.");
            }
        }
    }

    private <T> void compareSetting(
            Map<String, Object> settings,
            String key,
            T expected,
            TypeReference<T> type,
            List<String> failures) {
        try {
            T actual = objectMapper.convertValue(settings.get(key), type);
            if (!expected.equals(actual)) {
                failures.add("Tool Search manifest setting drifted: " + key + ".");
            }
        } catch (Exception e) {
            failures.add("Tool Search manifest setting is malformed: " + key + ".");
        }
    }

    private void compareText(
            Map<String, Object> settings,
            String key,
            String expected,
            List<String> failures) {
        Object value = settings.get(key);
        if (!(value instanceof String actual) || !expected.equals(actual)) {
            failures.add("Tool Search manifest setting drifted: " + key + ".");
        }
    }

    private ToolBenchmarkComparisonResult readRawResult(Path rawPath, List<String> failures) {
        try {
            return objectMapper.readValue(rawPath.toFile(), ToolBenchmarkComparisonResult.class);
        } catch (Exception e) {
            failures.add("Raw Tool Search matrix JSON could not be read: " + safeMessage(e) + ".");
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
                "Version 1 manifest must declare exactly one " + role + " artifact.");
    }

    private static Path resolveArtifact(
            Path root,
            EvidenceArtifact artifact,
            List<String> failures) {
        return EvidenceFiles.resolveArtifact(
                root,
                artifact,
                failures,
                "Raw artifact path escapes the evidence directory.");
    }

    private static boolean verifyRawArtifact(
            Path rawPath,
            EvidenceArtifact artifact,
            List<String> failures) {
        return EvidenceFiles.verifyArtifact(
                rawPath,
                artifact,
                artifact != null && artifact.sizeBytes() > 0,
                failures,
                "Raw Tool Search artifact is missing or unsafe.",
                "Raw Tool Search artifact is empty.",
                "Raw Tool Search artifact size does not match its manifest.",
                "Raw Tool Search artifact SHA-256 does not match its manifest.",
                "Raw Tool Search artifact could not be verified.");
    }

    private static void validateRegeneratedSummaryDescriptor(
            EvidenceArtifact summaryArtifact,
            String expectedSummary,
            List<String> failures) {
        EvidenceFiles.validateTextDescriptor(
                summaryArtifact,
                expectedSummary,
                failures,
                "Regenerated Tool Search summary does not match the version 1 manifest.");
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
                "Unsafe symbolic link is present in saved Tool Search evidence: ",
                "Unexpected artifact is present in saved Tool Search evidence: ",
                "Saved Tool Search evidence directory could not be inspected.");
    }

    private static void verifyLegacyLayout(Inspection inspection, Set<String> failures) {
        if (inspection.rawArtifact() == null) {
            failures.add("Legacy manifest does not identify a raw Tool Search artifact.");
        }
        if (Files.isSymbolicLink(inspection.summaryPath())
                || !Files.isRegularFile(inspection.summaryPath(), LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Legacy Tool Search summary is missing or unsafe.");
        }
    }

    private static void verifySummary(Inspection inspection, Set<String> failures) {
        EvidenceFiles.verifyText(
                inspection.summaryPath(),
                inspection.expectedSummary(),
                failures,
                "Tool Search summary is missing or unsafe.",
                "Tool Search summary is empty.",
                "Tool Search summary differs from deterministic offline reanalysis.",
                "Tool Search summary could not be verified.");
    }

    private static void writeSummaryAtomically(Path summaryPath, String summary) {
        EvidenceFiles.replaceTextAtomically(
                summaryPath,
                summary,
                ".tool-search-summary-",
                "Tool Search summary must not be a symbolic link",
                "Failed to regenerate Tool Search matrix summary");
    }

    private static Path normalizedRunDirectory(Path runDirectory, List<String> failures) {
        return EvidenceFiles.inspectRunDirectory(
                runDirectory,
                failures,
                "Tool Search evidence directory must not be null.",
                "Tool Search evidence directory is missing or unsafe.");
    }

    private JsonNode readManifestNode(Path root, List<String> failures) {
        Path manifestPath = root.resolve(EvidenceManifestStore.MANIFEST_FILENAME);
        if (Files.isSymbolicLink(manifestPath)
                || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Tool Search evidence manifest is missing or unsafe.");
            return null;
        }
        try {
            return objectMapper.readTree(manifestPath.toFile());
        } catch (Exception e) {
            failures.add("Tool Search evidence manifest could not be read: " + safeMessage(e) + ".");
            return null;
        }
    }

    private static String text(JsonNode node, String field, List<String> failures) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            failures.add("Legacy manifest field is missing or malformed: " + field + ".");
            return null;
        }
        return value.asText();
    }

    private static String safeMessage(Exception exception) {
        return EvidenceFiles.safeMessage(exception);
    }

    enum ManifestFormat {
        VERSION_1("v1"),
        LEGACY_V0("legacy-v0"),
        UNKNOWN("unknown");

        private final String label;

        ManifestFormat(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record OfflineResult(ManifestFormat manifestFormat, List<String> failures) {

        OfflineResult {
            manifestFormat = manifestFormat == null ? ManifestFormat.UNKNOWN : manifestFormat;
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        boolean valid() {
            return failures.isEmpty();
        }
    }

    private record Inspection(
            ManifestFormat manifestFormat,
            EvidenceManifest manifest,
            EvidenceArtifact rawArtifact,
            EvidenceArtifact summaryArtifact,
            Path rawPath,
            Path summaryPath,
            String expectedSummary,
            List<String> failures
    ) {

        private static Inspection invalid(List<String> failures) {
            return new Inspection(
                    ManifestFormat.UNKNOWN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.copyOf(failures));
        }
    }

    private record LegacyManifest(
            EvidenceArtifact rawArtifact,
            Map<String, Object> settings
    ) {}
}
