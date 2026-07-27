package com.setaccio.lab.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.mockito.Mockito;

class VisionMatrixRunnerTest {

    @Test
    void parsesExplicitModelsAndTokenPolicy() {
        assertThat(VisionMatrixRunner.parseModels("gemma4:e2b, qwen3.5:latest"))
                .containsExactly("gemma4:e2b", "qwen3.5:latest");
        assertThat(VisionMatrixRunner.parseMaxTokens("none")).isNull();
        assertThat(VisionMatrixRunner.parseMaxTokens("1024")).isEqualTo(1024);
    }

    @Test
    void usageRequiresAnExplicitPromptVersion() {
        assertThatThrownBy(() -> VisionMatrixRunner.main(new String[] {
                "--corpus-dir", "local/vision-corpus",
                "--models", "model-a",
                "--max-tokens", "none",
                "--output-dir", "build/vision-matrix/2026-07-26-fixture"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--prompt-version");
    }

    @Test
    void rejectsAmbiguousOrUnsafeProtocolOptions() {
        assertThatThrownBy(() -> VisionMatrixRunner.parseModels("model-a,model-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates");
        assertThatThrownBy(() -> VisionMatrixProtocol.settings(List.of("unsafe model"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe Ollama model tags");
        assertThatThrownBy(() -> VisionMatrixRunner.parseMaxTokens(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisionMatrixRunner.parseMaxTokens("32769"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresEveryRequestedModelToAlreadyExistWithoutPulling() {
        OllamaApi ollamaApi = Mockito.mock(OllamaApi.class);
        Mockito.when(ollamaApi.listModels()).thenReturn(new OllamaApi.ListModelResponse(List.of(
                model("model-a:latest"),
                model("model-b:tag"))));

        List<VisionMatrixModelIdentity> identities = VisionMatrixRunner.requireInstalledModels(
                ollamaApi,
                List.of("model-a", "model-b:tag"));

        assertThat(identities)
                .extracting(
                        VisionMatrixModelIdentity::requestedModel,
                        VisionMatrixModelIdentity::resolvedModel,
                        VisionMatrixModelIdentity::digest)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "model-a", "model-a:latest", digest(1)),
                        org.assertj.core.groups.Tuple.tuple(
                                "model-b:tag", "model-b:tag", digest(2)));
        assertThatThrownBy(() -> VisionMatrixRunner.requireInstalledModels(
                ollamaApi,
                List.of("model-a", "missing:tag")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing:tag");
        Mockito.verify(ollamaApi, Mockito.times(2)).listModels();
        Mockito.verifyNoMoreInteractions(ollamaApi);
    }

    @Test
    void rejectsMissingDigestsAndAliasesForTheSameInstalledModel() {
        OllamaApi missingDigestApi = Mockito.mock(OllamaApi.class);
        Mockito.when(missingDigestApi.listModels()).thenReturn(new OllamaApi.ListModelResponse(
                List.of(new OllamaApi.Model(
                        "model-a:latest",
                        null,
                        null,
                        0L,
                        null,
                        null))));

        assertThatThrownBy(() -> VisionMatrixRunner.requireInstalledModels(
                missingDigestApi,
                List.of("model-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity is incomplete");

        OllamaApi duplicateApi = Mockito.mock(OllamaApi.class);
        Mockito.when(duplicateApi.listModels()).thenReturn(new OllamaApi.ListModelResponse(List.of(
                new OllamaApi.Model("model-a:latest", null, null, 0L, digest(1), null),
                new OllamaApi.Model("alias:tag", null, null, 0L, digest(1), null))));

        assertThatThrownBy(() -> VisionMatrixRunner.requireInstalledModels(
                duplicateApi,
                List.of("model-a", "alias:tag")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate model digests");
    }

    @Test
    void restrictsInputsAndOutputsToTheFixedIgnoredLayout() {
        Path corpus = VisionMatrixRunner.resolveCorpusDirectory("local/vision-corpus");
        Path output = VisionMatrixRunner.resolveNewOutputDirectory(
                "build/vision-matrix/2026-07-25-offline-fixture");

        assertThat(corpus.toString()).endsWith("local/vision-corpus");
        assertThat(output.toString()).endsWith("build/vision-matrix/2026-07-25-offline-fixture");
        assertThatThrownBy(() -> VisionMatrixRunner.resolveCorpusDirectory("../Pictures"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VisionMatrixRunner.resolveNewOutputDirectory(
                "build/other/2026-07-25-fixture"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OllamaApi.Model model(String name) {
        int digestIndex = name.startsWith("model-a") ? 1 : 2;
        return new OllamaApi.Model(
                name,
                null,
                null,
                0L,
                digest(digestIndex),
                null);
    }

    private String digest(int value) {
        return "%064x".formatted(value);
    }
}
