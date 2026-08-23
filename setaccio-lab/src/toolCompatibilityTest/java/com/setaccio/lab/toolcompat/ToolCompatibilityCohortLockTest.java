package com.setaccio.lab.toolcompat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortLockTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void locksTheExactOwnerApprovedOrderRolesTagsDigestsAndMetadata() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("approved-project"));

        ToolCompatibilityCohortPreflight.Prepared prepared =
                new ToolCompatibilityCohortPreflight().prepareApproved(
                        project,
                        "http://localhost:11434",
                        "build/tool-compatibility/2026-08-23-approved-cohort",
                        () -> approvedInventory(ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION));

        assertThat(ToolCompatibilityCohortLock.APPROVAL_DATE).isEqualTo("2026-08-23");
        assertThat(prepared.ollamaRuntimeVersion()).isEqualTo("0.32.15");
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::effectiveInstalledTag)
                .containsExactly(
                        "hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0",
                        "granite4.1:3b",
                        "ministral-3:3b",
                        "gemma4:e2b",
                        "qwen3.5:0.8b",
                        "qwen3.8:27b-mlx");
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::digest)
                .containsExactly(
                        "2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed",
                        "6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb",
                        "a48e77f25d7933c64552d810c3ca5c7fc8cce4ad7e1ff1432fe24574c8e146e0",
                        "7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e",
                        "f3817196d142eaf72ce79dfebe53dcb20bd21da87ce13e138a8f8e10a866b3a4",
                        "5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e");
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::role)
                .containsExactly(
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.REFERENCE);
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::seedSemantics)
                .containsOnly(ToolCompatibilityCohortSeedSemantics.SUPPORTED);
        assertThat(prepared.orderedModels())
                .extracting(model -> model.metadata().artifactRuntimeFormat().value())
                .containsExactly(
                        "GGUF via Ollama",
                        "GGUF via Ollama",
                        "GGUF via Ollama",
                        "GGUF via Ollama",
                        "GGUF via Ollama",
                        "safetensors/MLX via Ollama");
        assertThat(prepared.orderedModels())
                .allSatisfy(model -> {
                    assertThat(model.metadata().templateFingerprint().value())
                            .matches("sha256:[0-9a-f]{64}");
                    assertThat(model.metadata().defaultSystemPromptFingerprint().value())
                            .matches("sha256:[0-9a-f]{64}");
                });
        ToolCompatibilityCohortExecutionPlan plan =
                ToolCompatibilityCohortExecutionPlan.createApproved(prepared);
        assertThat(plan.schedule().entries()).hasSize(96);
        assertThat(plan.schedule().entries())
                .extracting(ToolCompatibilityCohortSchedule.Entry::modelPosition)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 6)
                        .boxed()
                        .flatMap(position -> java.util.stream.Stream.generate(() -> position)
                                .limit(ToolCompatibilityProtocol.ROW_COUNT))
                        .toList());
        assertThat(plan.schedule().entries())
                .extracting(ToolCompatibilityCohortSchedule.Entry::effectiveSeed)
                .doesNotContainNull();
        assertThat(plan.promptPolicy().promptCondition())
                .isEqualTo(ToolCompatibilityPromptCondition.UNTREATED);
        assertThat(prepared.outputDirectory()).doesNotExist();
    }

    @Test
    void rejectsRuntimeDigestAndMetadataDriftBeforeAllocation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("drift-project"));
        Path output = project.resolve("build/tool-compatibility/2026-08-23-approved-cohort");
        ToolCompatibilityCohortPreflight preflight = new ToolCompatibilityCohortPreflight();

        assertThatThrownBy(() -> preflight.prepareApproved(
                project,
                "http://localhost:11434",
                "build/tool-compatibility/2026-08-23-approved-cohort",
                () -> approvedInventory("0.32.16")))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("owner-approved T3.1 lock");

        assertThatThrownBy(() -> preflight.prepareApproved(
                project,
                "http://localhost:11434",
                "build/tool-compatibility/2026-08-23-approved-cohort",
                () -> inventoryWithDigestDrift()))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("position 2");

        assertThatThrownBy(() -> preflight.prepareApproved(
                project,
                "http://localhost:11434",
                "build/tool-compatibility/2026-08-23-approved-cohort",
                () -> inventoryWithMetadataDrift()))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("position 3");

        assertThatThrownBy(() -> preflight.prepareApproved(
                project,
                "http://localhost:11434",
                "build/tool-compatibility/2026-08-23-approved-cohort",
                () -> inventoryWithSeedDrift()))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("position 4");
        assertThat(output).doesNotExist();
    }

    private static ToolCompatibilityCohortInventory approvedInventory(String runtimeVersion) {
        List<ToolCompatibilityCohortInventoryModel> models =
                ToolCompatibilityCohortLock.orderedModels().stream()
                        .map(ToolCompatibilityCohortLockTest::inventoryModel)
                        .toList();
        return new ToolCompatibilityCohortInventory(runtimeVersion, models);
    }

    private static ToolCompatibilityCohortInventory inventoryWithDigestDrift() {
        List<ToolCompatibilityCohortInventoryModel> models = new ArrayList<>(
                approvedInventory(ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION).models());
        ToolCompatibilityCohortInventoryModel original = models.get(1);
        models.set(1, new ToolCompatibilityCohortInventoryModel(
                original.installedTag(),
                "0".repeat(64),
                original.executionLocation(),
                original.seedSemantics(),
                original.metadata()));
        return new ToolCompatibilityCohortInventory(
                ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION, models);
    }

    private static ToolCompatibilityCohortInventory inventoryWithMetadataDrift() {
        List<ToolCompatibilityCohortInventoryModel> models = new ArrayList<>(
                approvedInventory(ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION).models());
        ToolCompatibilityCohortInventoryModel original = models.get(2);
        ToolCompatibilityCohortModelMetadata metadata = original.metadata();
        models.set(2, new ToolCompatibilityCohortInventoryModel(
                original.installedTag(),
                original.digest(),
                original.executionLocation(),
                original.seedSemantics(),
                new ToolCompatibilityCohortModelMetadata(
                        metadata.sizeBytes(),
                        metadata.familyProvenance(),
                        metadata.artifactRuntimeFormat(),
                        ToolCompatibilityMetadataField.available("drifted-precision"),
                        metadata.templateFingerprint(),
                        metadata.defaultSystemPromptFingerprint(),
                        metadata.toolCapability(),
                        metadata.thinkingMode())));
        return new ToolCompatibilityCohortInventory(
                ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION, models);
    }

    private static ToolCompatibilityCohortInventory inventoryWithSeedDrift() {
        List<ToolCompatibilityCohortInventoryModel> models = new ArrayList<>(
                approvedInventory(ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION).models());
        ToolCompatibilityCohortInventoryModel original = models.get(3);
        models.set(3, new ToolCompatibilityCohortInventoryModel(
                original.installedTag(),
                original.digest(),
                original.executionLocation(),
                ToolCompatibilityCohortSeedSemantics.UNSUPPORTED,
                original.metadata()));
        return new ToolCompatibilityCohortInventory(
                ToolCompatibilityCohortLock.OLLAMA_RUNTIME_VERSION, models);
    }

    private static ToolCompatibilityCohortInventoryModel inventoryModel(
            ToolCompatibilityCohortLock.ApprovedModel model
    ) {
        return new ToolCompatibilityCohortInventoryModel(
                model.installedTag(),
                model.digest(),
                ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL,
                model.seedSemantics(),
                model.metadata());
    }
}
