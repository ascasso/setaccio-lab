package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceFrameworkVersions;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.service.VisionPromptCatalog;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionMatrixComparisonTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesVerifiedVersionOneAndVersionTwoRunsDeterministically() throws Exception {
        Run baseline = writeRun("2026-07-26-v1", VisionMatrixTestFixtures.PROMPT, List.of("vision-one"));
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-26-v2", version2, List.of("vision-one"));
        VisionMatrixComparison comparison = new VisionMatrixComparison(VisionMatrixTestFixtures.OBJECT_MAPPER);

        String first = comparison.compare(baseline.directory(), candidate.directory()).report();
        String second = comparison.compare(baseline.directory(), candidate.directory()).report();

        assertThat(first).isEqualTo(second);
        assertThat(first)
                .contains("# Offline Vision Prompt Comparison")
                .contains("Baseline run: `2026-07-26-v1`")
                .contains("Candidate run: `2026-07-26-v2`")
                .contains("## Invocation and structural deltas")
                .contains("## Repetition and token deltas")
                .contains("## Latency and infrastructure deltas")
                .contains("does not score expected concepts, hallucinations, or image quality")
                .doesNotContain(temporaryDirectory.toString(), "Private fixture observation", "images/");
    }

    @Test
    void rejectsMismatchedInputIdentitiesBeforeRenderingAComparison() throws Exception {
        Run baseline = writeRun("2026-07-26-baseline", VisionMatrixTestFixtures.PROMPT, List.of("vision-one"));
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-26-candidate", version2, List.of("vision-two"));

        assertThatThrownBy(() -> new VisionMatrixComparison(VisionMatrixTestFixtures.OBJECT_MAPPER)
                .compare(baseline.directory(), candidate.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("case IDs or BLAKE3 input identities differ");
    }

    @Test
    void rejectsMismatchedModelIdentitiesAndRunSettingsBeforeRenderingAComparison() throws Exception {
        Run baseline = writeRun("2026-07-26-model-baseline", VisionMatrixTestFixtures.PROMPT, List.of("vision-one"));
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run differentModel = writeRun(
                "2026-07-26-model-candidate", version2, List.of("vision-one"), List.of("model-b"), null);
        Run differentTokenPolicy = writeRun(
                "2026-07-26-settings-candidate", version2, List.of("vision-one"), List.of("model-a"), 512);
        VisionMatrixComparison comparison = new VisionMatrixComparison(VisionMatrixTestFixtures.OBJECT_MAPPER);

        assertThatThrownBy(() -> comparison.compare(baseline.directory(), differentModel.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model identities or their order differ");
        assertThatThrownBy(() -> comparison.compare(baseline.directory(), differentTokenPolicy.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetitions, seeds, temperature, token policy, or models differ");
    }

    @Test
    void rejectsMismatchedFrameworkVersionsBeforeRenderingAComparison() throws Exception {
        Run baseline = writeRun("2026-07-26-framework-baseline", VisionMatrixTestFixtures.PROMPT, List.of("vision-one"));
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run differentSpringBoot = writeRun(
                "2026-07-26-spring-boot-candidate", version2, List.of("vision-one"));
        Run differentSpringAi = writeRun(
                "2026-07-26-spring-ai-candidate", version2, List.of("vision-one"));
        EvidenceFrameworkVersions baselineVersions = readManifest(baseline).frameworkVersions();
        rewriteFrameworkVersions(
                differentSpringBoot,
                new EvidenceFrameworkVersions("different-spring-boot", baselineVersions.springAi()));
        rewriteFrameworkVersions(
                differentSpringAi,
                new EvidenceFrameworkVersions(baselineVersions.springBoot(), "different-spring-ai"));
        VisionMatrixComparison comparison = new VisionMatrixComparison(VisionMatrixTestFixtures.OBJECT_MAPPER);

        assertThatThrownBy(() -> comparison.compare(baseline.directory(), differentSpringBoot.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Spring Boot or Spring AI framework versions differ");
        assertThatThrownBy(() -> comparison.compare(baseline.directory(), differentSpringAi.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Spring Boot or Spring AI framework versions differ");
    }

    @Test
    void rejectsTamperedEvidenceBeforeRenderingAComparison() throws Exception {
        Run baseline = writeRun("2026-07-26-tampered-baseline", VisionMatrixTestFixtures.PROMPT, List.of("vision-one"));
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-26-tampered-candidate", version2, List.of("vision-one"));
        Files.writeString(candidate.rawJson(), "\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new VisionMatrixComparison(VisionMatrixTestFixtures.OBJECT_MAPPER)
                .compare(baseline.directory(), candidate.directory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate run did not verify")
                .hasMessageContaining("SHA-256");
    }

    private Run writeRun(
            String runId,
            VisionPromptDefinition prompt,
            List<String> caseIds) throws Exception {
        return writeRun(runId, prompt, caseIds, List.of("model-a"), null);
    }

    private Run writeRun(
            String runId,
            VisionPromptDefinition prompt,
            List<String> caseIds,
            List<String> models,
            Integer maxTokens) throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve(runId + "-corpus"), caseIds);
        VisionMatrixResult result = VisionMatrixTestFixtures.successfulMatrix(
                corpus, models, maxTokens, prompt);
        VisionMatrixAnalyzer.MatrixAnalysis analysis = new VisionMatrixAnalyzer(prompt).analyze(result);
        Path directory = Files.createDirectory(temporaryDirectory.resolve(runId));
        new VisionMatrixEvidence(VisionMatrixTestFixtures.OBJECT_MAPPER, prompt)
                .write(directory, result, analysis);
        return new Run(directory, directory.resolve(VisionMatrixProtocol.RAW_FILENAME));
    }

    private EvidenceManifest readManifest(Run run) {
        return new EvidenceManifestStore(VisionMatrixTestFixtures.OBJECT_MAPPER).read(run.directory());
    }

    private void rewriteFrameworkVersions(Run run, EvidenceFrameworkVersions frameworkVersions) throws Exception {
        EvidenceManifest manifest = readManifest(run);
        EvidenceManifest replacement = new EvidenceManifest(
                manifest.manifestVersion(),
                manifest.suite(),
                manifest.runId(),
                manifest.generatedAt(),
                manifest.codeBaseline(),
                frameworkVersions,
                manifest.executionEngine(),
                manifest.settings(),
                manifest.artifacts());
        VisionMatrixTestFixtures.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(run.directory().resolve(EvidenceManifestStore.MANIFEST_FILENAME).toFile(), replacement);
    }

    private record Run(Path directory, Path rawJson) {}
}
