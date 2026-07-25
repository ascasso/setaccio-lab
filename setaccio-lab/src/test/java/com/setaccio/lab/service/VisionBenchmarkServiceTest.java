package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.model.VisionStructuralCheck;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionBenchmarkServiceTest {

    @Test
    void runBuildsRowsForEachImageAndModelAndWritesJson() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-results-");
        Path image = Files.createTempFile("vision-", ".jpg");
        Files.write(image, "fake image".getBytes(StandardCharsets.UTF_8));
        VisionModelInvoker invoker = mock(VisionModelInvoker.class);
        when(invoker.invoke(any(UploadedImage.class), any(VisionInvocationSettings.class)))
                .thenAnswer(invocation -> successful(invocation.getArgument(1)));
        VisionBenchmarkService service = newService(outputDir, invoker);
        List<VisionInvocationSettings> settings = List.of(
                new VisionInvocationSettings("model-a", 0.0, 42, 512),
                new VisionInvocationSettings("model-b", 0.0, 42, 512));

        BenchmarkResult result = service.runConfigured(
                List.of(new UploadedImage("sample.jpg", "image/jpeg", Files.size(image), image)),
                settings);

        assertThat(result.suite()).isEqualTo("vision");
        assertThat(result.host()).isEqualTo("local");
        assertThat(result.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(result.runs()).hasSize(2).allSatisfy(row -> {
            assertThat(row.input()).isEqualTo("sample.jpg");
            assertThat(row.inputHash()).isEqualTo("hash-value");
            assertThat(row.mimeType()).isEqualTo("image/jpeg");
            assertThat(row.promptId()).isEqualTo("vision-image-analysis");
            assertThat(row.promptVersion()).isEqualTo("1");
            assertThat(row.promptSha256()).hasSize(64);
            assertThat(row.temperature()).isZero();
            assertThat(row.seed()).isEqualTo(42);
            assertThat(row.maxTokens()).isEqualTo(512);
            assertThat(row.tokensIn()).isEqualTo(11);
            assertThat(row.tokensOut()).isEqualTo(7);
            assertThat(row.outputText()).isEqualTo("analysis");
            assertThat(row.structuralChecks()).singleElement().satisfies(check ->
                    assertThat(check.present()).isTrue());
            assertThat(row.structureComplete()).isTrue();
            assertThat(row.success()).isTrue();
            assertThat(row.errorCategory()).isNull();
        });
        assertThat(Files.list(outputDir))
                .anySatisfy(path -> assertThat(path.getFileName().toString()).endsWith("-vision.json"));
        verify(invoker, times(2)).invoke(any(UploadedImage.class), any(VisionInvocationSettings.class));
    }

    @Test
    void runPreservesModelDefaultsForTheBackwardCompatibleServiceEntryPoint() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-results-");
        Path image = Files.createTempFile("vision-", ".jpg");
        VisionModelInvoker invoker = mock(VisionModelInvoker.class);
        when(invoker.invoke(any(UploadedImage.class), any(VisionInvocationSettings.class)))
                .thenAnswer(invocation -> successful(invocation.getArgument(1)));
        VisionBenchmarkService service = newService(outputDir, invoker);

        service.run(
                List.of(new UploadedImage("sample.jpg", "image/jpeg", 0, image)),
                List.of("model-a"));

        ArgumentCaptor<VisionInvocationSettings> captor = ArgumentCaptor.forClass(VisionInvocationSettings.class);
        verify(invoker).invoke(any(UploadedImage.class), captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new VisionInvocationSettings("model-a", null, null, null));
    }

    @Test
    void runRetainsClassifiedInvocationFailuresAsRows() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-results-");
        Path image = Files.createTempFile("vision-", ".jpg");
        VisionModelInvoker invoker = mock(VisionModelInvoker.class);
        when(invoker.invoke(any(UploadedImage.class), any(VisionInvocationSettings.class)))
                .thenAnswer(invocation -> failed(invocation.getArgument(1)));
        VisionBenchmarkService service = newService(outputDir, invoker);

        BenchmarkResult result = service.run(
                List.of(new UploadedImage("sample.jpg", "image/jpeg", 0, image)),
                List.of("model-a"));

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isFalse();
            assertThat(row.errorCategory()).isEqualTo(VisionErrorCategory.MODEL_UNAVAILABLE);
            assertThat(row.error()).isEqualTo("Ollama chat model is not available");
            assertThat(row.inputHash()).isEqualTo("hash-value");
            assertThat(row.promptSha256()).hasSize(64);
        });
    }

    private VisionBenchmarkService newService(Path outputDir, VisionModelInvoker invoker) {
        return new VisionBenchmarkService(
                invoker,
                new FileHashingService(new StubHashingService()),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("vision-benchmark-results"),
                Executors.newFixedThreadPool(2),
                "http://localhost:11434");
    }

    private VisionInvocationResult successful(VisionInvocationSettings settings) {
        return new VisionInvocationResult(
                settings,
                "image/jpeg",
                "vision-image-analysis",
                "1",
                "a".repeat(64),
                5,
                11,
                7,
                "analysis",
                List.of(new VisionStructuralCheck("Primary Category", true)),
                true,
                true,
                null,
                null);
    }

    private VisionInvocationResult failed(VisionInvocationSettings settings) {
        return new VisionInvocationResult(
                settings,
                "image/jpeg",
                "vision-image-analysis",
                "1",
                "a".repeat(64),
                5,
                null,
                null,
                null,
                List.of(),
                false,
                false,
                VisionErrorCategory.MODEL_UNAVAILABLE,
                "Ollama chat model is not available");
    }

    private static class StubHashingService implements Blake3HashingService {
        @Override
        public String hashBytes(byte[] data) {
            return "hash-value";
        }

        @Override
        public String hashString(String input) {
            return "hash-value";
        }

        @Override
        public String hashInputStream(java.io.InputStream inputStream) {
            return "hash-value";
        }

        @Override
        public boolean verifyHash(byte[] data, String expectedHash) {
            return "hash-value".equals(expectedHash);
        }

        @Override
        public boolean verifyHash(java.io.InputStream inputStream, String expectedHash) {
            return "hash-value".equals(expectedHash);
        }
    }
}
