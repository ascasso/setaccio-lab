package com.setaccio.lab.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EvidenceManifestStoreTest {

    private final EvidenceManifestStore store =
            new EvidenceManifestStore(new ObjectMapper().findAndRegisterModules());

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsAVersionedPublicSafeManifest() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "run-1");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        EvidenceArtifact artifact = EvidenceIntegrity.describe(runDirectory, raw, "raw-result");
        EvidenceManifest manifest = manifest(runDirectory, List.of(artifact));

        Path manifestPath = store.write(runDirectory, manifest);
        EvidenceManifest loaded = store.read(runDirectory);

        assertThat(manifestPath).hasFileName(EvidenceManifestStore.MANIFEST_FILENAME);
        assertThat(loaded).isEqualTo(manifest);
        assertThat(loaded.manifestVersion()).isEqualTo(EvidenceManifest.CURRENT_VERSION);
        assertThat(loaded.codeBaseline().gitCommit()).isEqualTo("abc123");
        assertThat(loaded.frameworkVersions().springBoot()).isEqualTo("4.1.0");
        assertThat(loaded.settings()).containsEntry("temperature", 0.0);

        String json = Files.readString(manifestPath);
        assertThat(json)
                .contains("\"manifestVersion\" : 1")
                .contains("\"executionEngine\" : \"spring-ai-direct\"")
                .doesNotContain(tempDir.toAbsolutePath().toString())
                .doesNotContain("\"host\"")
                .doesNotContain("\"credentials\"");
    }

    @Test
    void refusesToOverwriteAnExistingManifest() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "run-2");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        EvidenceManifest manifest = manifest(
                runDirectory,
                List.of(EvidenceIntegrity.describe(runDirectory, raw, "raw-result"))
        );
        store.write(runDirectory, manifest);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.write(runDirectory, manifest))
                .withMessageContaining("already exists");
    }

    @Test
    void rejectsAManifestForAnotherRunDirectory() {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "run-3");
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                "fixture-suite",
                "another-run",
                Instant.parse("2026-07-24T12:00:00Z"),
                new EvidenceCodeBaseline("abc123", false),
                new EvidenceFrameworkVersions("4.1.0", "2.0.0"),
                "spring-ai-direct",
                Map.of(),
                List.of()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.write(runDirectory, manifest))
                .withMessageContaining("runId");
    }

    @Test
    void refusesToWriteAnUnsupportedManifestVersion() {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "run-4");
        EvidenceManifest manifest = new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION + 1,
                "fixture-suite",
                "run-4",
                Instant.parse("2026-07-24T12:00:00Z"),
                new EvidenceCodeBaseline("abc123", false),
                new EvidenceFrameworkVersions("4.1.0", "2.0.0"),
                "spring-ai-direct",
                Map.of(),
                List.of()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.write(runDirectory, manifest))
                .withMessageContaining("Unsupported evidence manifest version");
    }

    @Test
    void provenanceDetectionDoesNotRequireARepositoryOrSpringContext() {
        EvidenceCodeBaseline baseline = EvidenceProvenance.captureCodeBaseline(tempDir);
        EvidenceFrameworkVersions versions = EvidenceProvenance.detectFrameworkVersions();

        assertThat(baseline.gitCommit()).isEqualTo("unknown");
        assertThat(baseline.workingTreeDirty()).isTrue();
        assertThat(versions.springBoot()).isNotBlank();
        assertThat(versions.springAi()).isNotBlank();
    }

    private EvidenceManifest manifest(Path runDirectory, List<EvidenceArtifact> artifacts) {
        return new EvidenceManifest(
                EvidenceManifest.CURRENT_VERSION,
                "fixture-suite",
                runDirectory.getFileName().toString(),
                Instant.parse("2026-07-24T12:00:00Z"),
                new EvidenceCodeBaseline("abc123", false),
                new EvidenceFrameworkVersions("4.1.0", "2.0.0"),
                "spring-ai-direct",
                Map.of("temperature", 0.0, "seed", 42),
                artifacts
        );
    }
}
