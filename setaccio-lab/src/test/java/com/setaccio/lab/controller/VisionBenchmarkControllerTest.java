package com.setaccio.lab.controller;

import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.RunRow;
import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.service.VisionBenchmarkService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VisionBenchmarkControllerTest {

    private MockMvc mockMvc;

    private VisionBenchmarkService visionBenchmarkService;

    @BeforeEach
    void setUp() {
        visionBenchmarkService = mock(VisionBenchmarkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new VisionBenchmarkController(visionBenchmarkService)).build();
    }

    @Test
    void runDelegatesToServiceAndCleansUpTempFiles() throws Exception {
        AtomicReference<Path> tempPath = new AtomicReference<>();
        when(visionBenchmarkService.runConfigured(anyList(), anyList())).thenAnswer(invocation -> {
            List<UploadedImage> images = invocation.getArgument(0);
            List<VisionInvocationSettings> settings = invocation.getArgument(1);
            tempPath.set(images.getFirst().path());
            assertThat(Files.exists(tempPath.get())).isTrue();
            assertThat(settings).containsExactly(
                    VisionInvocationSettings.modelDefaults("model-a"),
                    VisionInvocationSettings.modelDefaults("model-b"));
            return new BenchmarkResult(
                    "vision",
                    Instant.parse("2026-06-15T00:00:00Z"),
                    Instant.parse("2026-06-15T00:00:01Z"),
                    "test-host",
                    "http://localhost:11434",
                    List.of(RunRow.ok("model-a", "sample.jpg", "hash", 10, null, null, "analysis"))
            );
        });

        MockMultipartFile file = new MockMultipartFile(
                "files", "sample.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        mockMvc.perform(multipart("/api/lab/vision")
                        .file(file)
                        .param("models", "model-a, model-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("vision"))
                .andExpect(jsonPath("$.runs[0].inputHash").value("hash"));

        assertThat(tempPath.get()).isNotNull();
        assertThat(Files.exists(tempPath.get())).isFalse();
    }

    @Test
    void runAcceptsExplicitGenerationSettings() throws Exception {
        when(visionBenchmarkService.runConfigured(anyList(), anyList())).thenAnswer(invocation -> {
            List<VisionInvocationSettings> settings = invocation.getArgument(1);
            assertThat(settings).containsExactly(
                    new VisionInvocationSettings("model-a", 0.2, 43, 512));
            return result();
        });
        MockMultipartFile file = new MockMultipartFile(
                "files", "sample.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        mockMvc.perform(multipart("/api/lab/vision")
                        .file(file)
                        .param("models", "model-a")
                        .param("temperature", "0.2")
                        .param("seed", "43")
                        .param("maxTokens", "512"))
                .andExpect(status().isOk());
    }

    @Test
    void runRejectsMissingModels() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "sample.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        mockMvc.perform(multipart("/api/lab/vision")
                        .file(file)
                        .param("models", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runRejectsUnsupportedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "sample.txt", MediaType.TEXT_PLAIN_VALUE, "text".getBytes());

        mockMvc.perform(multipart("/api/lab/vision")
                        .file(file)
                        .param("models", "model-a"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void runRejectsInvalidGenerationSettings() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "sample.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        mockMvc.perform(multipart("/api/lab/vision")
                        .file(file)
                        .param("models", "model-a")
                        .param("temperature", "2.1"))
                .andExpect(status().isBadRequest());
    }

    private BenchmarkResult result() {
        return new BenchmarkResult(
                "vision",
                Instant.parse("2026-06-15T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:01Z"),
                "local",
                "http://localhost:11434",
                List.of(RunRow.ok("model-a", "sample.jpg", "hash", 10, null, null, "analysis")));
    }
}
