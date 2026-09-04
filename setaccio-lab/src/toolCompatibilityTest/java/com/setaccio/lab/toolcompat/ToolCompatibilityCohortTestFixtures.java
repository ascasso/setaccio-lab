package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ToolCompatibilityCohortTestFixtures {

    static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    static final ToolCompatibilityHumanDecisionBinding BINDING =
            ToolCompatibilityPhase2DecisionLock.binding();
    static final String RUNTIME_VERSION = "0.32.15";

    private static final Instant STARTED = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-23T11:00:00Z");

    private ToolCompatibilityCohortTestFixtures() {}

    static ToolCompatibilityCohortResult result() {
        List<ToolCompatibilityRow> successful =
                ToolCompatibilityAnalysisTestFixtures.successfulResult().rows();
        return result(successful, successful);
    }

    static ToolCompatibilityCohortResult result(
            List<ToolCompatibilityRow> peerRows,
            List<ToolCompatibilityRow> referenceRows
    ) {
        ToolCompatibilityCohortPreflight.Prepared preflight = prepared();
        return result(preflight, List.of(peerRows, referenceRows));
    }

    static ToolCompatibilityCohortResult comparisonResult(
            List<ToolCompatibilityRow> firstPeerRows,
            List<ToolCompatibilityRow> secondPeerRows,
            List<ToolCompatibilityRow> referenceRows
    ) {
        ToolCompatibilityCohortModelIdentity firstPeer = identity(
                1,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                "fixture-peer-one:1b",
                "a".repeat(64),
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                "GGUF");
        ToolCompatibilityCohortModelIdentity secondPeer = identity(
                2,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                "fixture-peer-two:3b",
                "b".repeat(64),
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                "GGUF");
        ToolCompatibilityCohortModelIdentity reference = identity(
                3,
                ToolCompatibilityCohortModelIdentity.Role.REFERENCE,
                "fixture-reference:27b-mlx",
                "c".repeat(64),
                ToolCompatibilityCohortSeedSemantics.UNSUPPORTED,
                "MLX");
        ToolCompatibilityCohortPreflight.Prepared preflight =
                new ToolCompatibilityCohortPreflight.Prepared(
                        Path.of("local/evidence/tool-compatibility/2026-08-25-comparison-fixture"),
                        RUNTIME_VERSION,
                        List.of(firstPeer, secondPeer),
                        reference);
        return result(
                preflight,
                List.of(firstPeerRows, secondPeerRows, referenceRows));
    }

    static ToolCompatibilityCohortResult frontierResult(
            List<FrontierModelFixture> models,
            List<List<ToolCompatibilityRow>> rowsByModel
    ) {
        List<FrontierModelFixture> fixtures = List.copyOf(models);
        if (fixtures.size() < 2
                || fixtures.getLast().role()
                        != ToolCompatibilityCohortModelIdentity.Role.REFERENCE
                || fixtures.subList(0, fixtures.size() - 1).stream().anyMatch(model ->
                        model.role() != ToolCompatibilityCohortModelIdentity.Role.PEER)) {
            throw new IllegalArgumentException(
                    "frontier fixtures require ordered peers and a final reference");
        }
        List<ToolCompatibilityCohortModelIdentity> identities = new ArrayList<>();
        for (int index = 0; index < fixtures.size(); index++) {
            FrontierModelFixture fixture = fixtures.get(index);
            identities.add(identity(
                    index + 1,
                    fixture.role(),
                    fixture.tag(),
                    fixture.digest(),
                    fixture.role() == ToolCompatibilityCohortModelIdentity.Role.REFERENCE
                            ? ToolCompatibilityCohortSeedSemantics.UNSUPPORTED
                            : ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                    fixture.role() == ToolCompatibilityCohortModelIdentity.Role.REFERENCE
                            ? "MLX"
                            : "GGUF",
                    fixture.sizeBytes() == null
                            ? ToolCompatibilityMetadataField.unavailable()
                            : ToolCompatibilityMetadataField.available(fixture.sizeBytes())));
        }
        ToolCompatibilityCohortPreflight.Prepared preflight =
                new ToolCompatibilityCohortPreflight.Prepared(
                        Path.of("local/evidence/tool-compatibility/2026-08-25-frontier-fixture"),
                        RUNTIME_VERSION,
                        identities.subList(0, identities.size() - 1),
                        identities.getLast());
        return result(preflight, rowsByModel);
    }

    private static ToolCompatibilityCohortResult result(
            ToolCompatibilityCohortPreflight.Prepared preflight,
            List<List<ToolCompatibilityRow>> rowsByModel
    ) {
        ToolCompatibilityHumanDecision decision = ToolCompatibilityPhase2DecisionLock.decision();
        ToolCompatibilityCohortExecutionPlan plan =
                ToolCompatibilityCohortExecutionPlan.create(preflight, decision, BINDING);
        List<ToolCompatibilityCohortModelRun> runs = new ArrayList<>();
        if (rowsByModel.size() != preflight.orderedModels().size()) {
            throw new IllegalArgumentException(
                    "cohort fixture rows must match the prepared model count");
        }
        for (int index = 0; index < preflight.orderedModels().size(); index++) {
            ToolCompatibilityCohortModelIdentity identity = preflight.orderedModels().get(index);
            List<ToolCompatibilityRow> rows = List.copyOf(rowsByModel.get(index)).stream()
                    .map(row -> rowFor(identity, row))
                    .toList();
            runs.add(ToolCompatibilityCohortModelRun.create(
                    identity,
                    STARTED,
                    FINISHED,
                    plan.systemPrompt(),
                    rows));
        }
        return ToolCompatibilityCohortResult.create(STARTED, FINISHED, plan, runs);
    }

    static ToolCompatibilityCohortResult completeUsageResult() {
        List<ToolCompatibilityRow> rows = ToolCompatibilityAnalysisTestFixtures.successfulResult()
                .rows()
                .stream()
                .map(ToolCompatibilityCohortTestFixtures::withCompleteUsage)
                .toList();
        return result(rows, rows);
    }

    static ToolCompatibilityCohortPreflight.Prepared prepared() {
        ToolCompatibilityCohortModelIdentity peer = identity(
                1,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                "fixture-peer:1b",
                "a".repeat(64),
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                "GGUF");
        ToolCompatibilityCohortModelIdentity reference = identity(
                2,
                ToolCompatibilityCohortModelIdentity.Role.REFERENCE,
                "fixture-reference:27b-mlx",
                "b".repeat(64),
                ToolCompatibilityCohortSeedSemantics.UNSUPPORTED,
                "MLX");
        return new ToolCompatibilityCohortPreflight.Prepared(
                Path.of("local/evidence/tool-compatibility/2026-08-23-fixture"),
                RUNTIME_VERSION,
                List.of(peer),
                reference);
    }

    private static ToolCompatibilityCohortModelIdentity identity(
            int position,
            ToolCompatibilityCohortModelIdentity.Role role,
            String tag,
            String digest,
            ToolCompatibilityCohortSeedSemantics seedSemantics,
            String format
    ) {
        return identity(
                position,
                role,
                tag,
                digest,
                seedSemantics,
                format,
                ToolCompatibilityMetadataField.available("1000"));
    }

    private static ToolCompatibilityCohortModelIdentity identity(
            int position,
            ToolCompatibilityCohortModelIdentity.Role role,
            String tag,
            String digest,
            ToolCompatibilityCohortSeedSemantics seedSemantics,
            String format,
            ToolCompatibilityMetadataField sizeBytes
    ) {
        ToolCompatibilityMetadataField unavailable = ToolCompatibilityMetadataField.unavailable();
        return new ToolCompatibilityCohortModelIdentity(
                position,
                role,
                tag,
                tag,
                digest,
                seedSemantics,
                new ToolCompatibilityCohortModelMetadata(
                        sizeBytes,
                        ToolCompatibilityMetadataField.available("fixture-family"),
                        ToolCompatibilityMetadataField.available(format),
                        ToolCompatibilityMetadataField.available("fixture-precision"),
                        ToolCompatibilityMetadataField.available("sha256:" + "c".repeat(64)),
                        unavailable,
                        ToolCompatibilityMetadataField.available("tools"),
                        ToolCompatibilityMetadataField.available(
                                "effective-default-unavailable")));
    }

    record FrontierModelFixture(
            ToolCompatibilityCohortModelIdentity.Role role,
            String tag,
            String digest,
            String sizeBytes
    ) {

        FrontierModelFixture {
            if (role == null
                    || tag == null
                    || tag.isBlank()
                    || digest == null
                    || !digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("frontier model fixture is invalid");
            }
        }
    }

    private static ToolCompatibilityRow rowFor(
            ToolCompatibilityCohortModelIdentity identity,
            ToolCompatibilityRow source
    ) {
        try {
            ObjectNode row = OBJECT_MAPPER.valueToTree(source);
            row.put("requestedModel", identity.requestedTag());
            row.put("effectiveModel", identity.effectiveInstalledTag());
            row.put("modelDigest", identity.digest());
            if (identity.seedSemantics() == ToolCompatibilityCohortSeedSemantics.UNSUPPORTED) {
                row.putNull("seed");
            }
            return OBJECT_MAPPER.treeToValue(row, ToolCompatibilityRow.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create cohort row fixture", exception);
        }
    }

    private static ToolCompatibilityRow withCompleteUsage(ToolCompatibilityRow source) {
        try {
            ObjectNode row = OBJECT_MAPPER.valueToTree(source);
            int turns = row.withArray("providerTurns").size();
            row.withArray("providerTurns").forEach(turn -> {
                ObjectNode object = (ObjectNode) turn;
                object.set(
                        "usage",
                        OBJECT_MAPPER.valueToTree(new ToolCompatibilityTokenUsageEvidence(
                                ToolCompatibilityUsageAvailability.COMPLETE,
                                4,
                                2,
                                6)));
                object.put("outputLimitState", ToolCompatibilityOutputLimitState.NOT_REACHED.name());
            });
            row.set(
                    "aggregateUsage",
                    OBJECT_MAPPER.valueToTree(new ToolCompatibilityTokenUsageEvidence(
                            ToolCompatibilityUsageAvailability.COMPLETE,
                            Math.multiplyExact(turns, 4),
                            Math.multiplyExact(turns, 2),
                            Math.multiplyExact(turns, 6))));
            row.put("anyProviderTurnReachedOutputLimit", false);
            return OBJECT_MAPPER.treeToValue(row, ToolCompatibilityRow.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create complete-usage cohort row", exception);
        }
    }
}
