package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.lab.service.VisionPromptCatalog;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionHumanReviewPreparerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesOnePrivateWorksheetFromVerifiedComparableRuns() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("corpus"),
                List.of("vision-one", "vision-two"));
        Run baseline = writeRun("2026-07-27-v1", VisionMatrixTestFixtures.PROMPT, corpus, false);
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-27-v2", version2, corpus, false);
        Path outputRoot = temporaryDirectory.resolve("review-output");

        Path worksheet = preparer().prepare(
                        baseline.directory(),
                        candidate.directory(),
                        temporaryDirectory.resolve("corpus"),
                        outputRoot,
                        "2026-07-27-v1--vs--2026-07-27-v2")
                .worksheet();
        String markdown = Files.readString(worksheet, StandardCharsets.UTF_8);

        assertThat(worksheet.getFileName().toString())
                .isEqualTo(VisionHumanReviewPreparer.WORKSHEET_FILENAME);
        assertThat(markdown)
                .contains("# Private Vision Human-Review Worksheet")
                .contains("Both saved runs passed offline verification")
                .contains("Private fixture observation 0")
                .contains("fixture concept 0")
                .contains("unsupported fixture detail 0")
                .contains("![Private source image](")
                .contains("#### Repetition 1", "#### Repetition 2")
                .contains("Primary-concept retention")
                .contains("Unsupported specificity")
                .contains("Excessive `unknown`")
                .contains("## Final human decision")
                .doesNotContain(temporaryDirectory.toString());

        assertThatThrownBy(() -> preparer().prepare(
                        baseline.directory(),
                        candidate.directory(),
                        temporaryDirectory.resolve("corpus"),
                        outputRoot,
                        "2026-07-27-v1--vs--2026-07-27-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void collapsesSuccessfulExactRepetitionsToOneSharedResponse() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("exact-corpus"),
                List.of("vision-one"));
        Run baseline = writeRun("2026-07-27-exact-v1", VisionMatrixTestFixtures.PROMPT, corpus, true);
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-27-exact-v2", version2, corpus, true);

        Path worksheet = preparer().prepare(
                        baseline.directory(),
                        candidate.directory(),
                        temporaryDirectory.resolve("exact-corpus"),
                        temporaryDirectory.resolve("exact-review"),
                        "2026-07-27-exact-v1--vs--2026-07-27-exact-v2")
                .worksheet();
        String markdown = Files.readString(worksheet, StandardCharsets.UTF_8);

        assertThat(markdown)
                .contains("Repetitions matched exactly; review the shared successful response once.")
                .contains("#### Shared response")
                .doesNotContain("#### Repetition 1", "#### Repetition 2");
        assertThat(occurrences(markdown, "shared exact output")).isEqualTo(2);
    }

    @Test
    void rejectsPrivateCorpusIdentityDriftBeforeCreatingOutput() throws Exception {
        LoadedVisionCorpus evidenceCorpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("evidence-corpus"),
                List.of("vision-one", "vision-two"));
        VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("different-corpus"),
                List.of("vision-two", "vision-one"));
        Run baseline = writeRun("2026-07-27-drift-v1", VisionMatrixTestFixtures.PROMPT, evidenceCorpus, false);
        VisionPromptDefinition version2 = new VisionPromptCatalog(new VisionPromptDefinition()).require("2");
        Run candidate = writeRun("2026-07-27-drift-v2", version2, evidenceCorpus, false);
        Path outputRoot = temporaryDirectory.resolve("drift-review");

        assertThatThrownBy(() -> preparer().prepare(
                        baseline.directory(),
                        candidate.directory(),
                        temporaryDirectory.resolve("different-corpus"),
                        outputRoot,
                        "2026-07-27-drift-v1--vs--2026-07-27-drift-v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match saved input identity");
        assertThat(outputRoot).doesNotExist();
    }

    @Test
    void restrictsTheRunnerToTheIgnoredReviewRootAndSafeRunNames() {
        assertThat(VisionHumanReviewPrepareRunner.resolveReviewRoot("build/vision-human-review"))
                .hasToString(Path.of("").toAbsolutePath().normalize()
                        .resolve("build/vision-human-review").toString());
        assertThat(VisionHumanReviewPrepareRunner.reviewId(
                        Path.of("build/vision-matrix/2026-07-27-v1"),
                        Path.of("build/vision-matrix/2026-07-27-v2")))
                .isEqualTo("2026-07-27-v1--vs--2026-07-27-v2");
        assertThatThrownBy(() -> VisionHumanReviewPrepareRunner.resolveReviewRoot("build/other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed build/vision-human-review");
        assertThatThrownBy(() -> VisionHumanReviewPrepareRunner.reviewId(
                        Path.of("build/vision-matrix/unsafe name"),
                        Path.of("build/vision-matrix/safe-name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe path segments");
    }

    private Run writeRun(
            String runId,
            VisionPromptDefinition prompt,
            LoadedVisionCorpus corpus,
            boolean matchingRepetitions) throws Exception {
        List<String> models = List.of("model-a");
        VisionMatrixRunSettings settings = VisionMatrixProtocol.settings(models, null);
        VisionMatrixResult result = new VisionMatrixExecutor(
                        (image, invocationSettings) -> VisionMatrixTestFixtures.successfulInvocation(
                                prompt,
                                invocationSettings,
                                image.contentType(),
                                10,
                                matchingRepetitions
                                        ? "shared exact output"
                                        : "fixture output seed " + invocationSettings.seed()),
                        prompt,
                        VisionMatrixTestFixtures.FIXED_CLOCK)
                .execute(corpus, settings, VisionMatrixTestFixtures.modelIdentities(models));
        VisionMatrixAnalyzer.MatrixAnalysis analysis = new VisionMatrixAnalyzer(prompt).analyze(result);
        Path directory = Files.createDirectory(temporaryDirectory.resolve(runId));
        new VisionMatrixEvidence(VisionMatrixTestFixtures.OBJECT_MAPPER, prompt)
                .write(directory, result, analysis);
        return new Run(directory);
    }

    private VisionHumanReviewPreparer preparer() {
        return new VisionHumanReviewPreparer(
                VisionMatrixTestFixtures.OBJECT_MAPPER,
                new ApacheCommonsBlake3HashingServiceImpl());
    }

    private static int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private record Run(Path directory) {}
}
