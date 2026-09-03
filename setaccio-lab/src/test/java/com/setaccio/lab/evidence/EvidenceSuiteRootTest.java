package com.setaccio.lab.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceSuiteRootTest {

    private static final EvidenceSuiteRoot SUITE = EvidenceSuiteRoot.of("retrieval-embedding");

    @Test
    void exposesTheDurableAndLegacySuiteRoots() {
        assertThat(SUITE.durableRelativePath()).isEqualTo("local/evidence/retrieval-embedding");
        assertThat(SUITE.legacyRelativePath()).isEqualTo("build/retrieval-embedding");
    }

    @Test
    void rejectsUnsafeSuiteNames() {
        assertThatThrownBy(() -> EvidenceSuiteRoot.of("../escape"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one lowercase safe path segment");
    }

    @Test
    void allocatesNewEvidenceOnlyAsADirectChildOfTheDurableRoot(@TempDir Path project) {
        assertThat(SUITE.resolveNewRunDirectory(project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Output"))
                .isEqualTo(project.resolve("local/evidence/retrieval-embedding/2026-09-03-r4"));

        assertThatThrownBy(() -> SUITE.resolveNewRunDirectory(
                project, "build/retrieval-embedding/2026-09-03-r4", "Output"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-embedding");
        assertThatThrownBy(() -> SUITE.resolveNewRunDirectory(
                project, "local/evidence/retrieval-embedding/nested/2026-09-03-r4", "Output"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directly under");
        assertThatThrownBy(() -> SUITE.resolveNewRunDirectory(
                project, "local/evidence/retrieval-embedding/../../../escape", "Output"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directly under");
        assertThatThrownBy(() -> SUITE.resolveNewRunDirectory(project, "  ", "Output"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonblank trimmed path");
    }

    @Test
    void readsDurableEvidenceAndStillAcceptsLegacyBuildEvidence(@TempDir Path project) throws IOException {
        Path durable = Files.createDirectories(
                project.resolve("local/evidence/retrieval-embedding/2026-09-03-r4"));
        Path legacy = Files.createDirectories(
                project.resolve("build/retrieval-embedding/2026-09-02-r4"));

        assertThat(SUITE.requireSavedRunDirectory(project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Run"))
                .isEqualTo(durable);
        assertThat(SUITE.requireSavedRunDirectory(project, "build/retrieval-embedding/2026-09-02-r4", "Run"))
                .isEqualTo(legacy);
        assertThat(SUITE.requireReanalyzableRunDirectory(
                project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Run"))
                .isEqualTo(durable);
        assertThatThrownBy(() -> SUITE.requireReanalyzableRunDirectory(
                project, "build/retrieval-embedding/2026-09-02-r4", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("legacy build/retrieval-embedding evidence and is read-only");
        assertThat(SUITE.isLegacy(project, legacy)).isTrue();
        assertThat(SUITE.isLegacy(project, durable)).isFalse();
    }

    @Test
    void rejectsMissingUnsafeAndForeignSavedRunDirectories(@TempDir Path project) throws IOException {
        Files.createDirectories(project.resolve("local/evidence/retrieval-embedding"));
        Files.createDirectories(project.resolve("elsewhere/2026-09-03-r4"));
        Path link = project.resolve("local/evidence/retrieval-embedding/linked");
        Files.createSymbolicLink(link, project.resolve("elsewhere/2026-09-03-r4"));

        assertThatThrownBy(() -> SUITE.requireSavedRunDirectory(
                project, "local/evidence/retrieval-embedding/absent", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist or is unsafe");
        assertThatThrownBy(() -> SUITE.requireSavedRunDirectory(
                project, "local/evidence/retrieval-embedding/linked", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain symbolic links");
        assertThatThrownBy(() -> SUITE.requireSavedRunDirectory(
                project, "elsewhere/2026-09-03-r4", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-embedding");
    }

    @Test
    void rejectsSymbolicLinksThroughoutTheDurableRootToRunPath(@TempDir Path project) throws IOException {
        Path localProject = Files.createDirectory(project.resolve("local-project"));
        Path evidenceProject = Files.createDirectory(project.resolve("evidence-project"));
        Path suiteProject = Files.createDirectory(project.resolve("suite-project"));

        assertRejectsDurableParentLink(localProject, localProject.resolve("local"));
        assertRejectsDurableParentLink(evidenceProject, evidenceProject.resolve("local/evidence"));
        assertRejectsDurableParentLink(
                suiteProject, suiteProject.resolve("local/evidence/retrieval-embedding"));
    }

    @Test
    void rejectsSymbolicLinksThroughoutTheLegacyRootToRunPath(@TempDir Path project) throws IOException {
        Path buildProject = Files.createDirectory(project.resolve("build-project"));
        Path suiteProject = Files.createDirectory(project.resolve("suite-project"));

        assertRejectsLegacyParentLink(buildProject, buildProject.resolve("build"));
        assertRejectsLegacyParentLink(suiteProject, suiteProject.resolve("build/retrieval-embedding"));
    }

    @Test
    void rejectsSymbolicLinksAboveAFixedDurableWorksheetRoot(@TempDir Path project) throws IOException {
        Path outside = Files.createDirectories(project.resolve("outside"));
        Files.createDirectories(project.resolve("local"));
        Files.createSymbolicLink(project.resolve("local/evidence"), outside);
        EvidenceSuiteRoot review = EvidenceSuiteRoot.of("vision-human-review");

        assertThatThrownBy(() -> review.resolveFixedDurableRoot(
                project, "local/evidence/vision-human-review", "Review output root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain symbolic links");
    }

    @Test
    void derivesTheProjectDirectoryFromTheDurableRootDepth(@TempDir Path project) {
        Path run = SUITE.resolveNewRunDirectory(
                project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Output");
        assertThat(SUITE.projectDirectoryOfDurableRun(run))
                .isEqualTo(project.toAbsolutePath().normalize());
    }

    @Test
    void pinsFixedWorksheetRootsToTheDurableRoot(@TempDir Path project) {
        EvidenceSuiteRoot review = EvidenceSuiteRoot.of("vision-human-review");
        assertThat(review.resolveFixedDurableRoot(project, "local/evidence/vision-human-review", "Review output root"))
                .isEqualTo(project.resolve("local/evidence/vision-human-review"));
        assertThatThrownBy(() -> review.resolveFixedDurableRoot(
                project, "build/vision-human-review", "Review output root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/vision-human-review");
    }

    private void assertRejectsDurableParentLink(Path project, Path link) throws IOException {
        Path parent = link.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path outside = Files.createDirectories(project.resolve("outside-" + link.getFileName()));
        Files.createSymbolicLink(link, outside);

        assertThatThrownBy(() -> SUITE.resolveNewRunDirectory(
                project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Output"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain symbolic links");
        assertThatThrownBy(() -> SUITE.requireSavedRunDirectory(
                project, "local/evidence/retrieval-embedding/2026-09-03-r4", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain symbolic links");
    }

    private void assertRejectsLegacyParentLink(Path project, Path link) throws IOException {
        Path parent = link.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path outside = Files.createDirectories(project.resolve("outside-" + link.getFileName()));
        Files.createSymbolicLink(link, outside);

        assertThatThrownBy(() -> SUITE.requireSavedRunDirectory(
                project, "build/retrieval-embedding/2026-09-02-r4", "Run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain symbolic links");
    }
}
