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

class EvidenceVerifierTest {

    private final EvidenceManifestStore store =
            new EvidenceManifestStore(new ObjectMapper().findAndRegisterModules());
    private final EvidenceVerifier verifier = new EvidenceVerifier();

    @TempDir
    Path tempDir;

    @Test
    void verifiesDeclaredNonEmptyArtifactsOffline() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "valid-run");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        Path summary = Files.writeString(runDirectory.resolve("SUMMARY.md"), "# Summary\n");
        EvidenceManifest manifest = manifest(
                runDirectory,
                List.of(
                        EvidenceIntegrity.describe(runDirectory, raw, "raw-result"),
                        EvidenceIntegrity.describe(runDirectory, summary, "summary")
                )
        );
        store.write(runDirectory, manifest);

        EvidenceVerification result = verifier.verify(runDirectory, store.read(runDirectory));

        assertThat(result.valid()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void reportsAMissingDeclaredArtifact() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "missing-run");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        EvidenceManifest manifest = manifest(
                runDirectory,
                List.of(EvidenceIntegrity.describe(runDirectory, raw, "raw-result"))
        );
        store.write(runDirectory, manifest);
        Files.delete(raw);

        EvidenceVerification result = verifier.verify(runDirectory, manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).contains("Declared artifact is missing: raw.json.");
    }

    @Test
    void reportsAModifiedDeclaredArtifact() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "modified-run");
        Path raw = Files.writeString(runDirectory.resolve("raw.txt"), "alpha");
        EvidenceManifest manifest = manifest(
                runDirectory,
                List.of(EvidenceIntegrity.describe(runDirectory, raw, "raw-result"))
        );
        store.write(runDirectory, manifest);
        Files.writeString(raw, "omega");

        EvidenceVerification result = verifier.verify(runDirectory, manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures())
                .contains("Declared artifact SHA-256 does not match manifest: raw.txt.");
    }

    @Test
    void reportsAnEmptyDeclaredArtifact() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "empty-run");
        Path raw = Files.createFile(runDirectory.resolve("raw.json"));
        EvidenceArtifact empty = new EvidenceArtifact(
                "raw.json",
                "raw-result",
                0,
                EvidenceIntegrity.sha256(raw)
        );
        EvidenceManifest manifest = manifest(runDirectory, List.of(empty));
        store.write(runDirectory, manifest);

        EvidenceVerification result = verifier.verify(runDirectory, manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).contains("Declared artifact is empty: raw.json.");
    }

    @Test
    void reportsAnUndeclaredArtifact() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "undeclared-run");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        EvidenceManifest manifest = manifest(
                runDirectory,
                List.of(EvidenceIntegrity.describe(runDirectory, raw, "raw-result"))
        );
        store.write(runDirectory, manifest);
        Files.writeString(runDirectory.resolve("unexpected.txt"), "unexpected");

        EvidenceVerification result = verifier.verify(runDirectory, manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).contains("Undeclared artifact is present: unexpected.txt.");
    }

    @Test
    void reportsDuplicateArtifactDeclarations() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "duplicate-run");
        Path raw = Files.writeString(runDirectory.resolve("raw.json"), "{\"result\":true}");
        EvidenceArtifact artifact = EvidenceIntegrity.describe(runDirectory, raw, "raw-result");
        EvidenceManifest manifest = manifest(runDirectory, List.of(artifact, artifact));
        store.write(runDirectory, manifest);

        EvidenceVerification result = verifier.verify(runDirectory, manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).contains("Artifact is declared more than once: raw.json.");
    }

    @Test
    void refusesToDescribeArtifactsOutsideTheRunDirectory() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "bounded-run");
        Path outside = Files.writeString(tempDir.resolve("outside.json"), "{}");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvidenceIntegrity.describe(runDirectory, outside, "raw-result"))
                .withMessageContaining("inside the evidence run directory");
    }

    @Test
    void rejectsSymbolicLinkArtifacts() throws Exception {
        Path runDirectory = EvidenceRunDirectory.createNamed(tempDir, "symbolic-run");
        Path target = Files.writeString(tempDir.resolve("target.json"), "{}");
        Path link = Files.createSymbolicLink(runDirectory.resolve("raw.json"), target);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvidenceIntegrity.describe(runDirectory, link, "raw-result"))
                .withMessageContaining("non-symbolic file");
    }

    @Test
    void rejectsUnsafeManifestArtifactPaths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EvidenceArtifact(
                        "../outside.json",
                        "raw-result",
                        2,
                        "0".repeat(64)))
                .withMessageContaining("normalized relative artifact path");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EvidenceArtifact(
                        "/private/output.json",
                        "raw-result",
                        2,
                        "0".repeat(64)))
                .withMessageContaining("normalized relative artifact path");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EvidenceArtifact(
                        "C:/private/output.json",
                        "raw-result",
                        2,
                        "0".repeat(64)))
                .withMessageContaining("normalized relative artifact path");
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
                Map.of("temperature", 0.0),
                artifacts
        );
    }
}
