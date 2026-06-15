package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.UploadedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisionBenchmarkServiceTest {

    @Test
    void runBuildsRowsForEachImageAndModelAndWritesJson() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-results-");
        Path image = Files.createTempFile("vision-", ".jpg");
        Files.write(image, "fake image".getBytes(StandardCharsets.UTF_8));

        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            assertThat(((OllamaChatOptions) prompt.getOptions()).getModel()).isIn("model-a", "model-b");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("analysis"))));
        });

        VisionBenchmarkService service = new VisionBenchmarkService(
                singletonProvider(ollamaChatModel),
                new FileHashingService(new StubHashingService()),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("vision-benchmark-results"),
                Executors.newFixedThreadPool(2),
                "http://localhost:11434"
        );

        BenchmarkResult result = service.run(
                List.of(new UploadedImage("sample.jpg", "image/jpeg", Files.size(image), image)),
                List.of("model-a", "model-b")
        );

        assertThat(result.suite()).isEqualTo("vision");
        assertThat(result.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(result.runs()).hasSize(2);
        assertThat(result.runs()).allSatisfy(row -> {
            assertThat(row.input()).isEqualTo("sample.jpg");
            assertThat(row.inputHash()).isEqualTo("hash-value");
            assertThat(row.outputText()).isEqualTo("analysis");
            assertThat(row.success()).isTrue();
        });
        assertThat(Files.list(outputDir)).anySatisfy(path -> assertThat(path.getFileName().toString()).endsWith("-vision.json"));
    }

    @Test
    void runReturnsFailureRowsWhenOllamaModelIsUnavailable() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-results-");
        Path image = Files.createTempFile("vision-", ".jpg");
        Files.write(image, "fake image".getBytes(StandardCharsets.UTF_8));

        VisionBenchmarkService service = new VisionBenchmarkService(
                singletonProvider(null),
                new FileHashingService(new StubHashingService()),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("vision-benchmark-results"),
                Executors.newSingleThreadExecutor(),
                "http://localhost:11434"
        );

        BenchmarkResult result = service.run(
                List.of(new UploadedImage("sample.jpg", "image/jpeg", Files.size(image), image)),
                List.of("model-a")
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isFalse();
            assertThat(row.error()).contains("Ollama chat model is not available");
            assertThat(row.inputHash()).isEqualTo("hash-value");
        });
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OllamaChatModel> singletonProvider(OllamaChatModel model) {
        ObjectProvider<OllamaChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
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
