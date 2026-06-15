package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.BenchmarkResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LabResultWriter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Path resultsDir;

    public LabResultWriter(ObjectMapper objectMapper,
                           @Value("${setaccio.lab.results-dir:build/lab-results}") String resultsDir) {
        this.objectMapper = objectMapper;
        this.resultsDir = Path.of(resultsDir);
    }

    public Path write(BenchmarkResult result) {
        try {
            Files.createDirectories(resultsDir);
            String timestamp = FILE_TIMESTAMP.format(result.startedAt());
            Path output = resultsDir.resolve(timestamp + "-" + sanitize(result.suite()) + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write lab result JSON", e);
        }
    }

    private String sanitize(String value) {
        return value == null || value.isBlank()
                ? "benchmark"
                : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
