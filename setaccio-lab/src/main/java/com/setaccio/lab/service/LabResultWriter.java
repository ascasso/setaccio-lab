package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.BenchmarkResult;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LabResultWriter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Path resultsDir;

    public LabResultWriter(ObjectMapper objectMapper,
                           @Value("${setaccio.lab.results-dir:build/lab-results}") String resultsDir) {
        this.objectMapper = objectMapper;
        this.resultsDir = Path.of(resultsDir);
    }

    public Path write(BenchmarkResult result) {
        return write(result.suite(), result.startedAt(), result);
    }

    public Path write(String suite, Instant startedAt, Object result) {
        Path output = null;
        try {
            Files.createDirectories(resultsDir);
            String timestamp = FILE_TIMESTAMP.format(startedAt);
            output = createUniqueOutput(timestamp, sanitize(suite));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
            return output;
        } catch (IOException e) {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw new IllegalStateException("Failed to write lab result JSON", e);
        }
    }

    private Path createUniqueOutput(String timestamp, String suite) throws IOException {
        for (int attempt = 0; attempt < 100; attempt++) {
            String runId = UUID.randomUUID().toString().substring(0, 8);
            Path output = resultsDir.resolve(timestamp + "-" + runId + "-" + suite + ".json");
            try {
                return Files.createFile(output);
            } catch (FileAlreadyExistsException ignored) {
                // Generate another id without ever truncating the existing result.
            }
        }
        throw new IOException("Unable to allocate a unique lab result filename");
    }

    private String sanitize(String value) {
        return value == null || value.isBlank()
                ? "benchmark"
                : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
