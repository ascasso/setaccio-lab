package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisionMatrixAnalyzerTest {

    @TempDir
    Path temporaryDirectory;

    private final VisionMatrixAnalyzer analyzer =
            new VisionMatrixAnalyzer(VisionMatrixTestFixtures.PROMPT);

    @Test
    void keepsDeterministicHumanTokenLatencyAndFailureDimensionsSeparate() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("corpus"),
                List.of("vision-one"));
        VisionMatrixResult result = VisionMatrixTestFixtures.successfulMatrix(
                corpus,
                List.of("model-a"),
                null);

        VisionMatrixAnalyzer.MatrixAnalysis analysis = analyzer.analyze(result);
        VisionMatrixAnalyzer.GroupAnalysis group = analysis.groups()
                .get(new VisionMatrixAnalyzer.GroupKey("model-a", "vision-one"));

        assertThat(analysis.valid()).isTrue();
        assertThat(group.invocationSuccesses()).isEqualTo(2);
        assertThat(group.structuralCompletions()).isEqualTo(2);
        assertThat(group.expectedObservationReview()).isEqualTo("not_performed");
        assertThat(group.unsupportedDetailReview()).isEqualTo("not_performed");
        assertThat(group.repetitionStatus()).isEqualTo("ready_for_human_review");
        assertThat(group.structuralAgreement()).isTrue();
        assertThat(group.exactOutputMatch()).isFalse();
        assertThat(group.tokensInAvailable()).isEqualTo(2);
        assertThat(group.tokensOutAvailable()).isEqualTo(2);
        assertThat(group.tokensInTotal()).isEqualTo(22);
        assertThat(group.tokensOutTotal()).isEqualTo(14);
        assertThat(group.latencySamples()).isEqualTo(2);
        assertThat(group.medianLatencyMs()).isEqualTo(15.0);
        assertThat(group.minimumLatencyMs()).isEqualTo(10);
        assertThat(group.maximumLatencyMs()).isEqualTo(20);
        assertThat(group.failures()).isEmpty();
    }

    @Test
    void rejectsRowOrderAndInvocationSettingDrift() throws Exception {
        LoadedVisionCorpus corpus = VisionMatrixTestFixtures.writeAndLoadCorpus(
                temporaryDirectory.resolve("drift"),
                List.of("vision-one"));
        VisionMatrixResult valid = VisionMatrixTestFixtures.successfulMatrix(
                corpus,
                List.of("model-a"),
                null);
        List<VisionMatrixRow> rows = new ArrayList<>(valid.rows());
        VisionMatrixRow first = rows.getFirst();
        rows.set(0, new VisionMatrixRow(
                2,
                first.model(),
                first.caseId(),
                first.repetition(),
                new com.setaccio.lab.model.VisionInvocationSettings("model-a", 0.0, 99, null),
                first.mimeType(),
                first.inputBlake3(),
                first.promptId(),
                first.promptVersion(),
                first.promptSha256(),
                first.latencyMs(),
                first.tokensIn(),
                first.tokensOut(),
                first.outputText(),
                first.structuralChecks(),
                first.structureComplete(),
                first.invocationSuccess(),
                first.errorCategory(),
                first.error()));
        VisionMatrixResult drifted = new VisionMatrixResult(
                valid.suite(),
                valid.provider(),
                valid.host(),
                valid.startedAt(),
                valid.finishedAt(),
                valid.runSettings(),
                List.of(new VisionMatrixModelIdentity(
                        "model-b",
                        "model-b:latest",
                        valid.modelIdentities().getFirst().digest())),
                valid.executionStrategy(),
                valid.pullModelStrategy(),
                valid.promptId(),
                valid.promptVersion(),
                valid.promptSha256(),
                valid.inputs(),
                rows);

        VisionMatrixAnalyzer.MatrixAnalysis analysis = analyzer.analyze(drifted);

        assertThat(analysis.valid()).isFalse();
        assertThat(analysis.integrityFailures())
                .anyMatch(failure -> failure.contains("model identity drifted"))
                .anyMatch(failure -> failure.contains("order or identity"))
                .anyMatch(failure -> failure.contains("invocation settings"));
    }
}
