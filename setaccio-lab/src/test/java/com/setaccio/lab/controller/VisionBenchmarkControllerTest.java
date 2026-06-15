package com.setaccio.lab.controller;

import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.RunRow;
import com.setaccio.lab.model.UploadedImage;
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
        when(visionBenchmarkService.run(anyList(), anyList())).thenAnswer(invocation -> {
            List<UploadedImage> images = invocation.getArgument(0);
            List<String> models = invocation.getArgument(1);
            tempPath.set(images.getFirst().path());
            assertThat(Files.exists(tempPath.get())).isTrue();
            assertThat(models).containsExactly("model-a", "model-b");
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
}
