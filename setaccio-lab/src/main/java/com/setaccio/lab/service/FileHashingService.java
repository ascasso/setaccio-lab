package com.setaccio.lab.service;

import com.setaccio.core.service.Blake3HashingService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class FileHashingService {

    private final Blake3HashingService blake3HashingService;

    public FileHashingService(Blake3HashingService blake3HashingService) {
        this.blake3HashingService = blake3HashingService;
    }

    public String hash(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return blake3HashingService.hashInputStream(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to hash file: " + path.getFileName(), e);
        }
    }
}
