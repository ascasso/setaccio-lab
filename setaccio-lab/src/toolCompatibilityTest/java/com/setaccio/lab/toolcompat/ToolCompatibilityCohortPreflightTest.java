package com.setaccio.lab.toolcompat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortPreflightTest {

    private static final String PEER_ONE = "model-a:1b";
    private static final String PEER_TWO = "model-b:3b-mlx";
    private static final String REFERENCE = "model-reference:27b-mlx";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOrderedPeersAndSeparatelyLabelledReferenceWithoutAllocatingOutput()
            throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        ToolCompatibilityCohortModelMetadata gguf = metadata(
                "1073741824",
                "family-a",
                "GGUF",
                "Q8_0",
                "sha256:template-a",
                "sha256:system-a",
                "tools",
                "default-disabled");
        ToolCompatibilityCohortModelMetadata mlx = metadata(
                "3221225472",
                "family-b",
                "MLX",
                "bf16",
                "sha256:template-b",
                "sha256:system-b",
                "tools",
                "default-enabled");
        AtomicInteger snapshots = new AtomicInteger();

        ToolCompatibilityCohortPreflight.Prepared prepared =
                new ToolCompatibilityCohortPreflight().prepare(
                        input(project, List.of(PEER_TWO, PEER_ONE), REFERENCE),
                        () -> {
                            snapshots.incrementAndGet();
                            return inventory(
                                    model(PEER_ONE, "a".repeat(64), gguf),
                                    model(PEER_TWO, "b".repeat(64), mlx),
                                    model(REFERENCE, "c".repeat(64), mlx));
                        });

        assertThat(snapshots).hasValue(1);
        assertThat(prepared.ollamaRuntimeVersion()).isEqualTo("0.11.8");
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::effectiveInstalledTag)
                .containsExactly(PEER_TWO, PEER_ONE, REFERENCE);
        assertThat(prepared.orderedModels())
                .extracting(ToolCompatibilityCohortModelIdentity::role)
                .containsExactly(
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.PEER,
                        ToolCompatibilityCohortModelIdentity.Role.REFERENCE);
        assertThat(prepared.orderedModels())
                .extracting(model -> model.metadata().artifactRuntimeFormat().value())
                .containsExactly("MLX", "GGUF", "MLX");
        assertThat(prepared.outputDirectory()).doesNotExist();
    }

    @Test
    void retainsMissingOptionalMetadataAsExplicitlyUnavailable() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("unavailable-project"));
        ToolCompatibilityCohortModelMetadata unavailable =
                ToolCompatibilityCohortModelMetadata.unavailable();

        ToolCompatibilityCohortPreflight.Prepared prepared =
                new ToolCompatibilityCohortPreflight().prepare(
                        input(project, List.of(PEER_ONE), REFERENCE),
                        () -> inventory(
                                model(PEER_ONE, "a".repeat(64), unavailable),
                                model(REFERENCE, "c".repeat(64), unavailable)));

        assertThat(prepared.orderedModels())
                .allSatisfy(identity -> assertThat(identity.metadata().thinkingMode().availability())
                        .isEqualTo(ToolCompatibilityMetadataField.Availability.UNAVAILABLE));
        assertThat(prepared.orderedModels())
                .allSatisfy(identity -> assertThat(identity.metadata().thinkingMode().value())
                        .isNull());
    }

    @Test
    void rejectsMissingRuntimeVersionBeforeAllocation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("runtime-project"));
        Path output = output(project);

        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(project, List.of(PEER_ONE), REFERENCE),
                () -> new ToolCompatibilityCohortInventory(
                        null,
                        List.of(
                                model(PEER_ONE, "a".repeat(64)),
                                model(REFERENCE, "c".repeat(64))))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("runtime version");
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsMissingAndRemoteModelsBeforeAllocation() throws Exception {
        Path missingProject = Files.createDirectory(temporaryDirectory.resolve("missing-project"));
        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(missingProject, List.of(PEER_ONE), REFERENCE),
                () -> inventory(model(PEER_ONE, "a".repeat(64)))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("not installed");
        assertThat(output(missingProject)).doesNotExist();

        Path remoteProject = Files.createDirectory(temporaryDirectory.resolve("remote-project"));
        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(remoteProject, List.of(PEER_ONE), REFERENCE),
                () -> inventory(
                        model(PEER_ONE, "a".repeat(64)),
                        new ToolCompatibilityCohortInventoryModel(
                                REFERENCE,
                                "c".repeat(64),
                                ToolCompatibilityCohortInventoryModel.ExecutionLocation.REMOTE,
                                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                                ToolCompatibilityCohortModelMetadata.unavailable()))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("execute locally");
        assertThat(output(remoteProject)).doesNotExist();
    }

    @Test
    void rejectsDuplicateRequestedTagsAndDuplicateSelectedBytes() throws Exception {
        Path duplicateTagProject = Files.createDirectory(
                temporaryDirectory.resolve("duplicate-tag-project"));
        AtomicInteger snapshots = new AtomicInteger();
        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(duplicateTagProject, List.of(PEER_ONE, PEER_ONE), REFERENCE),
                () -> {
                    snapshots.incrementAndGet();
                    return inventory();
                }))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("duplicate normalized peer tag");
        assertThat(snapshots).hasValue(0);

        Path duplicateBytesProject = Files.createDirectory(
                temporaryDirectory.resolve("duplicate-bytes-project"));
        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(duplicateBytesProject, List.of(PEER_ONE, PEER_TWO), REFERENCE),
                () -> inventory(
                        model(PEER_ONE, "a".repeat(64)),
                        model(PEER_TWO, "a".repeat(64)),
                        model(REFERENCE, "c".repeat(64)))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("duplicate aliases");
        assertThat(output(duplicateBytesProject)).doesNotExist();
    }

    @Test
    void rejectsMutableLatestWhenVersionedAliasExists() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("alias-project"));
        String mutable = "model-a:latest";
        String versioned = "model-a:1b";

        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(project, List.of(mutable), REFERENCE),
                () -> inventory(
                        model(mutable, "a".repeat(64)),
                        model(versioned, "a".repeat(64)),
                        model(REFERENCE, "c".repeat(64)))))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Mutable :latest alias");
        assertThat(output(project)).doesNotExist();
    }

    @Test
    void rejectsIncompleteIdentitiesAndInventedUnavailableMetadata() {
        assertThatThrownBy(() -> new ToolCompatibilityCohortInventoryModel(
                PEER_ONE,
                "short-digest",
                ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL,
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                ToolCompatibilityCohortModelMetadata.unavailable()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full lowercase SHA-256");
        assertThatThrownBy(() -> new ToolCompatibilityMetadataField(
                ToolCompatibilityMetadataField.Availability.UNAVAILABLE,
                "guessed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not invent");
    }

    @Test
    void rejectsNonLoopbackAndCommaSeparatedInputBeforeReadingInventory() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("input-project"));
        AtomicInteger snapshots = new AtomicInteger();
        ToolCompatibilityCohortPreflight.InventorySource source = () -> {
            snapshots.incrementAndGet();
            return inventory();
        };

        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                new ToolCompatibilityCohortPreflight.Input(
                        project,
                        "https://example.com:11434",
                        List.of(PEER_ONE),
                        REFERENCE,
                        "build/tool-compatibility/2026-08-23-cohort"),
                source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
        assertThatThrownBy(() -> new ToolCompatibilityCohortPreflight().prepare(
                input(project, List.of(PEER_ONE + "," + PEER_TWO), REFERENCE),
                source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit ordered entries");
        assertThat(snapshots).hasValue(0);
    }

    private static ToolCompatibilityCohortPreflight.Input input(
            Path project,
            List<String> peers,
            String reference
    ) {
        return new ToolCompatibilityCohortPreflight.Input(
                project,
                "http://localhost:11434",
                peers,
                reference,
                "build/tool-compatibility/2026-08-23-cohort");
    }

    private static Path output(Path project) {
        return project.resolve("build/tool-compatibility/2026-08-23-cohort");
    }

    private static ToolCompatibilityCohortInventory inventory(
            ToolCompatibilityCohortInventoryModel... models
    ) {
        return new ToolCompatibilityCohortInventory("0.11.8", List.of(models));
    }

    private static ToolCompatibilityCohortInventoryModel model(String tag, String digest) {
        return model(tag, digest, ToolCompatibilityCohortModelMetadata.unavailable());
    }

    private static ToolCompatibilityCohortInventoryModel model(
            String tag,
            String digest,
            ToolCompatibilityCohortModelMetadata metadata
    ) {
        return new ToolCompatibilityCohortInventoryModel(
                tag,
                digest,
                ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL,
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                metadata);
    }

    private static ToolCompatibilityCohortModelMetadata metadata(
            String sizeBytes,
            String family,
            String format,
            String precision,
            String template,
            String systemPrompt,
            String toolCapability,
            String thinkingMode
    ) {
        return new ToolCompatibilityCohortModelMetadata(
                ToolCompatibilityMetadataField.available(sizeBytes),
                ToolCompatibilityMetadataField.available(family),
                ToolCompatibilityMetadataField.available(format),
                ToolCompatibilityMetadataField.available(precision),
                ToolCompatibilityMetadataField.available(template),
                ToolCompatibilityMetadataField.available(systemPrompt),
                ToolCompatibilityMetadataField.available(toolCapability),
                ToolCompatibilityMetadataField.available(thinkingMode));
    }
}
