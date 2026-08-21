package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCompatibilityEvidenceIntegrityTest {

    private static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    private static final EvidenceCodeBaseline CLEAN_BASELINE =
            new EvidenceCodeBaseline("c".repeat(40), false);

    @TempDir
    Path temporaryDirectory;

    private final ToolCompatibilityEvidence evidence =
            new ToolCompatibilityEvidence(OBJECT_MAPPER);

    @Test
    void rejectsUnknownRawResultSchemaFieldsAfterArtifactIntegrityIsRefreshed() throws Exception {
        Fixture fixture = writeFixture("2026-08-18-raw-schema");

        rewriteRawAndRefreshArtifact(fixture, raw -> raw.put("unknownField", true));

        assertThat(evidence.verify(fixture.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("Raw tool compatibility JSON could not be read")
                        && failure.contains("unknownField"));
    }

    @Test
    void rejectsManifestSuiteAndRunIdentityDrift() throws Exception {
        Fixture suite = writeFixture("2026-08-18-suite-drift");
        rewriteManifest(suite, manifest -> manifest.put("suite", "different-suite"));
        assertThat(evidence.verify(suite.runDirectory()).failures())
                .contains("Tool compatibility manifest suite is not ollama-tool-compatibility.");

        Fixture run = writeFixture("2026-08-18-run-drift");
        rewriteManifest(run, manifest -> manifest.put("runId", "different-run"));
        assertThat(evidence.verify(run.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("runId")
                        && failure.contains("evidence directory"));
    }

    @Test
    void rejectsCanonicalPromptSystemPromptAndFullModelDigestDrift() throws Exception {
        Fixture canonicalPrompt = writeFixture("2026-08-18-canonical-prompt-drift");
        rewriteRawAndRefreshArtifact(canonicalPrompt, raw ->
                raw.put("canonicalCasesSha256", "f".repeat(64)));
        assertRawProtocolRejected(canonicalPrompt);

        Fixture systemPrompt = writeFixture("2026-08-18-system-prompt-drift");
        rewriteRawAndRefreshArtifact(systemPrompt, raw ->
                ((ObjectNode) raw.path("systemPromptIdentity")).put("id", "different-system-prompt"));
        assertRawProtocolRejected(systemPrompt);

        Fixture rowSystemPrompt = writeFixture("2026-08-18-row-system-prompt-drift");
        ToolCompatibilitySystemPromptIdentity prompted = ToolCompatibilityPromptCondition.PROMPTED.prompt(
                ToolCompatibilityProtocol.systemPromptCatalog());
        rewriteRawAndRefreshArtifact(rowSystemPrompt, raw -> {
            ObjectNode row = firstRow(raw);
            row.put("systemPromptId", prompted.id());
            row.put("systemPromptVersion", prompted.version());
            row.put("systemPromptSha256", prompted.sha256());
        });
        assertRawProtocolRejected(rowSystemPrompt);

        Fixture modelDigest = writeFixture("2026-08-18-model-digest-drift");
        rewriteRawAndRefreshArtifact(modelDigest, raw ->
                ((ObjectNode) raw.path("modelIdentity")).put("digest", "incomplete"));
        assertRawProtocolRejected(modelDigest);
    }

    @Test
    void rejectsRowOrderDriftAfterArtifactIntegrityIsRefreshed() throws Exception {
        Fixture fixture = writeFixture("2026-08-18-row-order-drift");

        rewriteRawAndRefreshArtifact(fixture, raw -> {
            ArrayNode rows = (ArrayNode) raw.path("rows");
            JsonNode first = rows.get(0).deepCopy();
            JsonNode second = rows.get(1).deepCopy();
            rows.set(0, second);
            rows.set(1, first);
        });

        assertRawProtocolRejected(fixture);
    }

    @Test
    void rejectsProviderTurnOrderAndToolCallLinkageDrift() throws Exception {
        Fixture turnOrder = writeFixture("2026-08-18-turn-order-drift");
        rewriteRawAndRefreshArtifact(turnOrder, raw -> firstTurn(raw).put("sequence", 2));
        assertRawProtocolRejected(turnOrder);

        Fixture turnLinkage = writeFixture("2026-08-18-turn-linkage-drift");
        rewriteRawAndRefreshArtifact(turnLinkage, raw ->
                ((ArrayNode) firstTurn(raw).path("orderedToolCallIds"))
                        .set(0, OBJECT_MAPPER.getNodeFactory().textNode("wrong-call")));
        assertRawProtocolRejected(turnLinkage);
    }

    @Test
    void rejectsToolCallTurnAndResponseLinkageDrift() throws Exception {
        Fixture callTurn = writeFixture("2026-08-18-call-turn-drift");
        rewriteRawAndRefreshArtifact(callTurn, raw -> firstCall(raw).put("providerTurnSequence", 2));
        assertRawProtocolRejected(callTurn);

        Fixture response = writeFixture("2026-08-18-response-linkage-drift");
        rewriteRawAndRefreshArtifact(response, raw -> firstResponse(raw).put("callId", "wrong-call"));
        assertRawProtocolRejected(response);
    }

    @Test
    void rejectsSemanticOracleIdentityAndAssertionDrift() throws Exception {
        Fixture oracle = writeFixture("2026-08-18-oracle-identity-drift");
        rewriteRawAndRefreshArtifact(oracle, raw -> raw.put("caseOracleSha256", "f".repeat(64)));
        assertRawProtocolRejected(oracle);

        Fixture assertion = writeFixture("2026-08-18-oracle-assertion-drift");
        rewriteRawAndRefreshArtifact(assertion, raw -> {
            ArrayNode assertions = (ArrayNode) firstRow(raw).path("assertions");
            ((ObjectNode) assertions.get(0)).put("passed", false);
        });
        assertRawProtocolRejected(assertion);
    }

    @Test
    void rejectsAttemptCountDrift() throws Exception {
        Fixture fixture = writeFixture("2026-08-18-attempt-count-drift");

        rewriteRawAndRefreshArtifact(fixture, raw -> firstRow(raw).put("attemptCount", 2));

        assertRawProtocolRejected(fixture);
    }

    private void assertRawProtocolRejected(Fixture fixture) {
        assertThat(evidence.verify(fixture.runDirectory()).failures())
                .anyMatch(failure -> failure.contains("Raw tool compatibility JSON could not be read")
                        || failure.contains("Raw tool compatibility result could not be analyzed"));
        assertThat(evidence.reanalyze(fixture.runDirectory()).valid()).isFalse();
    }

    private Fixture writeFixture(String runId) throws Exception {
        Path run = Files.createDirectory(temporaryDirectory.resolve(runId));
        evidence.write(
                run,
                ToolCompatibilityAnalysisTestFixtures.successfulResult(),
                CLEAN_BASELINE);
        return new Fixture(
                run,
                run.resolve(ToolCompatibilityProtocol.RAW_FILENAME),
                run.resolve(EvidenceManifestStore.MANIFEST_FILENAME));
    }

    private static void rewriteRawAndRefreshArtifact(
            Fixture fixture,
            Consumer<ObjectNode> mutation
    ) throws Exception {
        ObjectNode raw = (ObjectNode) OBJECT_MAPPER.readTree(fixture.rawResult().toFile());
        mutation.accept(raw);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fixture.rawResult().toFile(), raw);

        rewriteManifest(fixture, manifest -> {
            ArrayNode artifacts = (ArrayNode) manifest.path("artifacts");
            ObjectNode rawArtifact = null;
            for (JsonNode artifact : artifacts) {
                if (ToolCompatibilityEvidence.RAW_ROLE.equals(artifact.path("role").asText())) {
                    rawArtifact = (ObjectNode) artifact;
                    break;
                }
            }
            if (rawArtifact == null) {
                throw new AssertionError("Raw artifact descriptor is missing");
            }
            rawArtifact.put("sizeBytes", fileSize(fixture.rawResult()));
            rawArtifact.put("sha256", EvidenceIntegrity.sha256(fixture.rawResult()));
        });
    }

    private static void rewriteManifest(Fixture fixture, Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode manifest = (ObjectNode) OBJECT_MAPPER.readTree(fixture.manifest().toFile());
        mutation.accept(manifest);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fixture.manifest().toFile(), manifest);
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            throw new AssertionError("Could not inspect synthetic raw result", exception);
        }
    }

    private static ObjectNode firstRow(ObjectNode raw) {
        return (ObjectNode) raw.path("rows").get(0);
    }

    private static ObjectNode firstTurn(ObjectNode raw) {
        return (ObjectNode) firstRow(raw).path("providerTurns").get(0);
    }

    private static ObjectNode firstCall(ObjectNode raw) {
        return (ObjectNode) firstRow(raw).path("toolCalls").get(0);
    }

    private static ObjectNode firstResponse(ObjectNode raw) {
        return (ObjectNode) firstRow(raw).path("toolResponses").get(0);
    }

    private record Fixture(Path runDirectory, Path rawResult, Path manifest) {}
}
