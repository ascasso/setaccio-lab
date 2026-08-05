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
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes and verifies ignored Anthropic run evidence without ever serializing credentials. */
final class AnthropicChatMatrixEvidence {

    private static final String RAW_ROLE = "raw-result";
    private static final String SNAPSHOT_ROLE = "portability-snapshot";
    private static final String SUMMARY_ROLE = "summary";

    private final ObjectMapper objectMapper;
    private final EvidenceManifestStore manifestStore;
    private final EvidenceVerifier verifier = new EvidenceVerifier();

    AnthropicChatMatrixEvidence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        manifestStore = new EvidenceManifestStore(objectMapper);
    }

    Path write(Path runDirectory, AnthropicChatMatrixResult result, EvidenceCodeBaseline codeBaseline) {
        validate(result);
        Path root = EvidenceFiles.requireWritableRunDirectory(
                runDirectory,
                "runDirectory must not be null",
                "runDirectory must be an existing regular directory");
        ChatPortabilitySnapshot snapshot = AnthropicChatMatrixSnapshotFactory.fromResult(
                root.getFileName().toString(), result, EvidenceProvenance.detectFrameworkVersions(), codeBaseline);
        writeJson(root.resolve(AnthropicChatMatrixProtocol.RAW_FILENAME), result, "raw Anthropic chat matrix result");
        writeJson(root.resolve(AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME), snapshot, "Anthropic portability snapshot");
        EvidenceArtifact raw = EvidenceIntegrity.describe(root, root.resolve(AnthropicChatMatrixProtocol.RAW_FILENAME), RAW_ROLE);
        EvidenceArtifact snapshotArtifact = EvidenceIntegrity.describe(
                root, root.resolve(AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME), SNAPSHOT_ROLE);
        String summary = renderSummary(result, snapshot, raw);
        EvidenceFiles.writeNewText(root.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME), summary,
                "Failed to write Anthropic chat matrix summary");
        EvidenceArtifact summaryArtifact = EvidenceIntegrity.describe(
                root, root.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME), SUMMARY_ROLE);
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                AnthropicChatMatrixProtocol.SUITE,
                root.getFileName().toString(),
                Instant.now(),
                codeBaseline,
                snapshot.frameworkVersions(),
                "spring-ai-anthropic-chat-invocation",
                manifestSettings(result),
                List.of(raw, snapshotArtifact, summaryArtifact));
        manifestStore.write(root, manifest);
        OfflineResult verification = verify(root);
        if (!verification.valid()) {
            throw new IllegalStateException("Generated Anthropic evidence failed verification: "
                    + String.join(" ", verification.failures()));
        }
        return root.resolve(EvidenceManifestStore.MANIFEST_FILENAME);
    }

    OfflineResult verify(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(
                runDirectory, failures, "Anthropic run directory must not be null.",
                "Anthropic run directory is missing or unsafe.");
        if (root == null) {
            return new OfflineResult(failures);
        }
        EvidenceManifest manifest = null;
        try {
            manifest = manifestStore.read(root);
            if (!AnthropicChatMatrixProtocol.SUITE.equals(manifest.suite())) {
                failures.add("Anthropic manifest suite drifted.");
            }
            if (manifest.artifacts().size() != 3) {
                failures.add("Anthropic manifest must declare exactly three artifacts.");
            }
            failures.addAll(verifier.verify(root, manifest).failures());
        } catch (Exception exception) {
            failures.add("Anthropic manifest could not be loaded: " + EvidenceFiles.safeMessage(exception) + ".");
        }
        AnthropicChatMatrixResult result = read(root.resolve(AnthropicChatMatrixProtocol.RAW_FILENAME), failures);
        if (result != null) {
            try {
                validate(result);
                if (manifest != null) {
                    JsonNode expectedSettings = objectMapper.valueToTree(manifestSettings(result));
                    JsonNode actualSettings = objectMapper.valueToTree(manifest.settings());
                    if (!sameJsonValue(expectedSettings, actualSettings)) {
                        failures.add("Anthropic manifest settings differ from raw locked protocol.");
                    }
                    ChatPortabilitySnapshot expectedSnapshot = AnthropicChatMatrixSnapshotFactory.fromResult(
                            root.getFileName().toString(), result, manifest.frameworkVersions(), manifest.codeBaseline());
                    verifySnapshot(root.resolve(AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME), expectedSnapshot, failures);
                    EvidenceArtifact raw = artifact(manifest, RAW_ROLE, failures);
                    if (raw != null && AnthropicChatMatrixProtocol.RAW_FILENAME.equals(raw.path())) {
                        String expectedSummary = renderSummary(result, expectedSnapshot, raw);
                        LinkedHashSet<String> summaryFailures = new LinkedHashSet<>();
                        EvidenceFiles.verifyText(
                                root.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME), expectedSummary, summaryFailures,
                                "Anthropic summary is missing or unsafe.", "Anthropic summary is empty.",
                                "Anthropic summary drifted from deterministic reanalysis.",
                                "Anthropic summary could not be read.");
                        failures.addAll(summaryFailures);
                    }
                }
            } catch (Exception exception) {
                failures.add("Anthropic raw evidence failed protocol validation: " + EvidenceFiles.safeMessage(exception) + ".");
            }
        }
        EvidenceFiles.validateLayout(root,
                Set.of(EvidenceManifestStore.MANIFEST_FILENAME, AnthropicChatMatrixProtocol.RAW_FILENAME,
                        AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME, AnthropicChatMatrixProtocol.SUMMARY_FILENAME),
                failures,
                "Unsafe symbolic link is present in Anthropic evidence: ",
                "Unexpected directory is present in Anthropic evidence: ",
                "Unexpected artifact is present in Anthropic evidence: ",
                "Anthropic evidence directory could not be inspected.");
        return new OfflineResult(List.copyOf(new LinkedHashSet<>(failures)));
    }

    OfflineResult reanalyze(Path runDirectory) {
        List<String> failures = new ArrayList<>();
        Path root = EvidenceFiles.inspectRunDirectory(runDirectory, failures,
                "Anthropic run directory must not be null.", "Anthropic run directory is missing or unsafe.");
        if (root == null) {
            return new OfflineResult(failures);
        }
        try {
            EvidenceManifest manifest = manifestStore.read(root);
            EvidenceArtifact raw = artifact(manifest, RAW_ROLE, failures);
            EvidenceArtifact snapshotArtifact = artifact(manifest, SNAPSHOT_ROLE, failures);
            verifyRequiredArtifact(root, raw, "raw Anthropic result", failures);
            verifyRequiredArtifact(root, snapshotArtifact, "Anthropic portability snapshot", failures);
            EvidenceFiles.validateLayout(root,
                    Set.of(EvidenceManifestStore.MANIFEST_FILENAME, AnthropicChatMatrixProtocol.RAW_FILENAME,
                            AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME, AnthropicChatMatrixProtocol.SUMMARY_FILENAME),
                    failures,
                    "Unsafe symbolic link is present in Anthropic evidence: ",
                    "Unexpected directory is present in Anthropic evidence: ",
                    "Unexpected artifact is present in Anthropic evidence: ",
                    "Anthropic evidence directory could not be inspected.");
            AnthropicChatMatrixResult result = read(root.resolve(AnthropicChatMatrixProtocol.RAW_FILENAME), failures);
            ChatPortabilitySnapshot snapshot = result == null ? null : AnthropicChatMatrixSnapshotFactory.fromResult(
                    root.getFileName().toString(), result, manifest.frameworkVersions(), manifest.codeBaseline());
            if (result != null) {
                validate(result);
                verifySnapshot(root.resolve(AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME), snapshot, failures);
            }
            if (failures.isEmpty()) {
                EvidenceFiles.replaceTextAtomically(root.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME),
                        renderSummary(result, snapshot, raw), ".anthropic-chat-summary-", null,
                        "Failed to regenerate Anthropic chat matrix summary");
            }
        } catch (Exception exception) {
            failures.add("Anthropic summary could not be regenerated: " + EvidenceFiles.safeMessage(exception) + ".");
        }
        return failures.isEmpty() ? verify(runDirectory) : new OfflineResult(failures);
    }

    private static void verifyRequiredArtifact(
            Path root,
            EvidenceArtifact artifact,
            String label,
            List<String> failures
    ) {
        Path path = EvidenceFiles.resolveArtifact(root, artifact, failures, label + " path escapes the evidence directory.");
        EvidenceFiles.verifyArtifact(path, artifact, true, failures,
                label + " is missing or unsafe.", label + " is empty.",
                label + " size does not match its manifest.", label + " SHA-256 does not match its manifest.",
                label + " could not be verified.");
    }

    AnthropicChatMatrixResult requireVerifiedResult(Path runDirectory) {
        OfflineResult verification = verify(runDirectory);
        if (!verification.valid()) {
            throw new IllegalArgumentException("Anthropic evidence is not valid: " + String.join(" ", verification.failures()));
        }
        try {
            return objectMapper.readerFor(AnthropicChatMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(runDirectory.resolve(AnthropicChatMatrixProtocol.RAW_FILENAME).toFile());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Anthropic raw result could not be read", exception);
        }
    }

    private void verifySnapshot(Path path, ChatPortabilitySnapshot expected, List<String> failures) {
        try {
            ChatPortabilitySnapshot actual = objectMapper.readerFor(ChatPortabilitySnapshot.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(path.toFile());
            if (!expected.equals(actual)) {
                failures.add("Anthropic portability snapshot drifted from raw evidence.");
            }
        } catch (Exception exception) {
            failures.add("Anthropic portability snapshot could not be read: " + EvidenceFiles.safeMessage(exception) + ".");
        }
    }

    private AnthropicChatMatrixResult read(Path raw, List<String> failures) {
        try {
            return objectMapper.readerFor(AnthropicChatMatrixResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(raw.toFile());
        } catch (Exception exception) {
            failures.add("Anthropic raw result could not be read: " + EvidenceFiles.safeMessage(exception) + ".");
            return null;
        }
    }

    private void writeJson(Path path, Object value, String label) {
        try {
            EvidenceFiles.writeNewBytes(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value),
                    "Failed to write " + label);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize " + label, exception);
        }
    }

    private static EvidenceArtifact artifact(EvidenceManifest manifest, String role, List<String> failures) {
        EvidenceArtifact artifact = EvidenceFiles.singleArtifact(manifest.artifacts(), role, failures,
                "Anthropic manifest must declare exactly one " + role + " artifact.");
        if (artifact != null && !expectedArtifactPath(role).equals(artifact.path())) {
            failures.add("Anthropic " + role + " artifact path drifted.");
        }
        return artifact;
    }

    private static String expectedArtifactPath(String role) {
        return switch (role) {
            case RAW_ROLE -> AnthropicChatMatrixProtocol.RAW_FILENAME;
            case SNAPSHOT_ROLE -> AnthropicChatMatrixProtocol.SNAPSHOT_FILENAME;
            case SUMMARY_ROLE -> AnthropicChatMatrixProtocol.SUMMARY_FILENAME;
            default -> throw new IllegalArgumentException("Unknown Anthropic artifact role: " + role);
        };
    }

    static boolean sameJsonValue(JsonNode left, JsonNode right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        }
        if (left.isObject() && right.isObject()) {
            if (left.size() != right.size()) {
                return false;
            }
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = left.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!right.has(field.getKey()) || !sameJsonValue(field.getValue(), right.get(field.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int index = 0; index < left.size(); index++) {
                if (!sameJsonValue(left.get(index), right.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return left.equals(right);
    }

    private static Map<String, Object> manifestSettings(AnthropicChatMatrixResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("endpointCategory", result.endpointCategory());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("requestedModelIdentity", result.requestedModelIdentity());
        settings.put("preflightCostEstimateUsd", result.preflightCostEstimate().estimatedUsd().toPlainString());
        settings.put("priceCheckedAt", result.preflightCostEstimate().priceCheckedAt().toString());
        settings.put("officialPriceSource", result.preflightCostEstimate().officialPriceSource());
        settings.put("maximumAuthorizedCostUsd", result.maximumAuthorizedCostUsd().toPlainString());
        return Map.copyOf(settings);
    }

    private static void validate(AnthropicChatMatrixResult result) {
        if (result.protocolVersion() != AnthropicChatMatrixProtocol.VERSION
                || !AnthropicChatMatrixProtocol.SUITE.equals(result.suite())
                || !AnthropicChatMatrixProtocol.PROVIDER.equals(result.provider())
                || !AnthropicChatMatrixProtocol.ENDPOINT_CATEGORY.equals(result.endpointCategory())
                || !AnthropicChatMatrixProtocol.EXECUTION_STRATEGY.equals(result.executionStrategy())
                || result.startedAt() == null || result.finishedAt() == null || result.finishedAt().isBefore(result.startedAt())
                || result.runSettings() == null || result.requestedModelIdentity() == null
                || !AnthropicChatMatrixProtocol.MODEL.equals(result.requestedModelIdentity().requestedModel())
                || result.rows().size() != AnthropicChatMatrixProtocol.ROW_COUNT
                || result.preflightCostEstimate() == null || result.maximumAuthorizedCostUsd() == null
                || result.maximumAuthorizedCostUsd().signum() <= 0
                || result.preflightCostEstimate().estimatedUsd().compareTo(result.maximumAuthorizedCostUsd()) > 0) {
            throw new IllegalArgumentException("Anthropic raw result drifted from the locked O3 contract");
        }
        ChatPortabilityRunSettings expected = AnthropicChatMatrixProtocol.settings(
                ChatPromptCatalog.load(com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
        if (!result.runSettings().equals(expected)) {
            throw new IllegalArgumentException("Anthropic run settings drifted from the locked O3 contract");
        }
        AnthropicChatMatrixSnapshotFactory.fromResult("validation", result,
                new com.setaccio.lab.evidence.EvidenceFrameworkVersions("validation", "validation"),
                new EvidenceCodeBaseline("validation", false));
    }

    private static String renderSummary(
            AnthropicChatMatrixResult result,
            ChatPortabilitySnapshot snapshot,
            EvidenceArtifact raw
    ) {
        long completed = snapshot.rows().stream().filter(ChatPortabilityRow::invocationSucceeded).count();
        long nonEmpty = snapshot.rows().stream().filter(ChatPortabilityRow::structuralOutputPresent).count();
        long usage = snapshot.rows().stream().filter(row -> row.totalTokens() != null).count();
        BigDecimal observedCost = AnthropicChatMatrixExecutor.observedCost(result.rows(), result.preflightCostEstimate());
        return "# Anthropic Chat Matrix Summary\n\n"
                + "## Protocol\n\n"
                + "- Provider: `anthropic`\n"
                + "- Requested model: `" + result.requestedModelIdentity().requestedModel() + "`\n"
                + "- Hosted identity semantics: `versioned provider model ID; no local digest`\n"
                + "- Rows: `6 sequential (3 prompts x 2 unseeded repetitions)`\n"
                + "- Temperature: `0.0`\n"
                + "- Seed: `unsupported; not simulated`\n"
                + "- Max output tokens: `128`\n"
                + "- Timeout: `PT2M`\n"
                + "- Attempts per row: `1`\n"
                + "- Authorized maximum USD: `$" + result.maximumAuthorizedCostUsd().toPlainString() + "`\n"
                + "- Preflight estimated USD: `$" + result.preflightCostEstimate().estimatedUsd().toPlainString() + "`\n"
                + "- Observed usage-derived USD: `$" + observedCost.toPlainString() + "`\n"
                + "\n## Deterministic Results\n\n"
                + "- Completed invocations: `" + completed + "/6`\n"
                + "- Non-empty structural outputs: `" + nonEmpty + "/6`\n"
                + "- Rows with complete usage: `" + usage + "/6`\n"
                + "- Raw result: `" + raw.path() + "`\n"
                + "- Raw result SHA-256: `" + raw.sha256() + "`\n"
                + "\n## Interpretation Boundary\n\n"
                + "This deterministic summary contains no raw provider response text, credential, header, base URL, or account detail. "
                + "It records an architecture portability run, not a model ranking or semantic comparison.\n";
    }

    record OfflineResult(List<String> failures) {
        OfflineResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        boolean valid() { return failures.isEmpty(); }
    }
}
