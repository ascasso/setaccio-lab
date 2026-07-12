package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabResultWriterTest {

    @Test
    void writesUniqueFilesForTheSameSuiteAndStartInstant() throws Exception {
        Path outputDir = Files.createTempDirectory("lab-result-writer-");
        LabResultWriter writer = new LabResultWriter(
                new ObjectMapper().findAndRegisterModules(), outputDir.toString());
        Instant startedAt = Instant.parse("2026-07-11T12:34:56.123456789Z");

        Path first = writer.write("tool-calling-comparison", startedAt, Map.of("run", 1));
        Path second = writer.write("tool-calling-comparison", startedAt, Map.of("run", 2));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).exists();
        assertThat(second).exists();
        assertThat(Files.readString(first)).contains("\"run\" : 1");
        assertThat(Files.readString(second)).contains("\"run\" : 2");
        assertThat(first.getFileName().toString())
                .matches("20260711T123456\\.123456789Z-[0-9a-f]{8}-tool-calling-comparison\\.json");
        assertThat(Files.list(outputDir)).hasSize(2);
    }
}
