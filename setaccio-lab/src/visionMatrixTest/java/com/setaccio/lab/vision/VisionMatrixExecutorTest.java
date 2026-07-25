package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;

import com.setaccio.lab.model.VisionInvocationSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionMatrixExecutorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesEveryModelCaseAndRepetitionStrictlySequentially() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("corpus"),
                List.of("vision-one", "vision-two"));
        List<String> calls = new ArrayList<>();
        VisionMatrixRunSettings settings =
                VisionMatrixProtocol.settings(List.of("model-a", "model-b"), null);
        VisionMatrixExecutor executor = new VisionMatrixExecutor(
                (image, invocationSettings) -> {
                    calls.add(image.originalFilename() + "/" + invocationSettings.model()
                            + "/" + invocationSettings.seed());
                    return VisionMatrixTestFixtures.successfulInvocation(
                            invocationSettings,
                            image.contentType(),
                            calls.size(),
                            "output " + calls.size());
                },
                VisionMatrixTestFixtures.PROMPT,
                VisionMatrixTestFixtures.FIXED_CLOCK);

        VisionMatrixResult result = executor.execute(corpus, settings);

        assertThat(calls).containsExactly(
                "vision-one/model-a/42",
                "vision-one/model-a/43",
                "vision-two/model-a/42",
                "vision-two/model-a/43",
                "vision-one/model-b/42",
                "vision-one/model-b/43",
                "vision-two/model-b/42",
                "vision-two/model-b/43");
        assertThat(result.executionStrategy()).isEqualTo("sequential");
        assertThat(result.pullModelStrategy()).isEqualTo("never");
        assertThat(result.host()).isEqualTo("local");
        assertThat(result.rows())
                .extracting(VisionMatrixRow::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(result.inputs())
                .extracting(VisionMatrixInput::caseId)
                .containsExactly("vision-one", "vision-two");
    }

    @Test
    void recordsUnexpectedInvocationFailureAndContinues() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("failure"),
                List.of("vision-one"));
        VisionMatrixExecutor executor = new VisionMatrixExecutor(
                (image, settings) -> {
                    if (settings.seed() == 42) {
                        throw new IllegalStateException("fixture failure");
                    }
                    return VisionMatrixTestFixtures.successfulInvocation(
                            settings,
                            image.contentType(),
                            2,
                            "second repetition");
                },
                VisionMatrixTestFixtures.PROMPT,
                VisionMatrixTestFixtures.FIXED_CLOCK);

        VisionMatrixResult result = executor.execute(
                corpus,
                VisionMatrixProtocol.settings(List.of("model-a"), 512));

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().invocationSuccess()).isFalse();
        assertThat(result.rows().getFirst().error()).isEqualTo("Vision provider invocation failed");
        assertThat(result.rows().getLast().invocationSuccess()).isTrue();
        assertThat(result.rows())
                .extracting(VisionMatrixRow::invocationSettings)
                .extracting(VisionInvocationSettings::maxTokens)
                .containsOnly(512);
    }
}
