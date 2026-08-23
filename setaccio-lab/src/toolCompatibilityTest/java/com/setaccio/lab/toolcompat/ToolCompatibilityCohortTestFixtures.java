package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class ToolCompatibilityCohortTestFixtures {

    static final JsonMapper OBJECT_MAPPER =
            JsonMapper.builder().findAndAddModules().build();
    static final ToolCompatibilityHumanDecisionBinding BINDING =
            new ToolCompatibilityHumanDecisionBinding(
                    "baseline-run",
                    "candidate-run",
                    ToolCompatibilitySystemPromptCatalog.SHA256,
                    "e".repeat(64),
                    LocalDate.parse("2026-08-23"));
    static final String RUNTIME_VERSION = "0.32.15";

    private static final Instant STARTED = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-23T11:00:00Z");

    private ToolCompatibilityCohortTestFixtures() {}

    static ToolCompatibilityCohortResult result() {
        ToolCompatibilityCohortPreflight.Prepared preflight = prepared();
        ToolCompatibilityHumanDecision decision = new ToolCompatibilityHumanDecision(
                ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE,
                BINDING);
        ToolCompatibilityCohortExecutionPlan plan =
                ToolCompatibilityCohortExecutionPlan.create(preflight, decision, BINDING);
        List<ToolCompatibilityCohortModelRun> runs = new ArrayList<>();
        for (ToolCompatibilityCohortModelIdentity identity : preflight.orderedModels()) {
            List<ToolCompatibilityRow> rows = ToolCompatibilityAnalysisTestFixtures.successfulResult()
                    .rows()
                    .stream()
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
                Path.of("build/tool-compatibility/2026-08-23-fixture"),
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
        ToolCompatibilityMetadataField unavailable = ToolCompatibilityMetadataField.unavailable();
        return new ToolCompatibilityCohortModelIdentity(
                position,
                role,
                tag,
                tag,
                digest,
                seedSemantics,
                new ToolCompatibilityCohortModelMetadata(
                        ToolCompatibilityMetadataField.available("1000"),
                        ToolCompatibilityMetadataField.available("fixture-family"),
                        ToolCompatibilityMetadataField.available(format),
                        ToolCompatibilityMetadataField.available("fixture-precision"),
                        ToolCompatibilityMetadataField.available("sha256:" + "c".repeat(64)),
                        unavailable,
                        ToolCompatibilityMetadataField.available("tools"),
                        ToolCompatibilityMetadataField.available(
                                "effective-default-unavailable")));
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
}
