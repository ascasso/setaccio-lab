package com.setaccio.lab.controller;

import com.setaccio.lab.model.BenchmarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Profile("local")
@RestController
@RequestMapping("/api/lab/vision")
public class VisionBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(VisionBenchmarkController.class);

    @PostMapping
    public ResponseEntity<BenchmarkResult> run(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("models") String modelsCsv) throws IOException {

        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one multipart file");
        }
        List<String> models = parseModels(modelsCsv);
        List<Path> images = persistUploads(files);

        try {
            logger.info("Vision benchmark requested: {} models, {} images", models.size(), images.size());
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Vision benchmark execution is not wired yet");
        } finally {
            images.forEach(this::silentDelete);
        }
    }

    private List<String> parseModels(String modelsCsv) {
        if (modelsCsv == null || modelsCsv.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a comma-separated models parameter");
        }
        List<String> models = Arrays.stream(modelsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (models.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one model");
        }
        return models;
    }

    private List<Path> persistUploads(MultipartFile[] files) throws IOException {
        List<Path> out = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String name = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            Path tmp = Files.createTempFile("lab-vision-", "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try (var in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            out.add(tmp);
        }
        if (out.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one non-empty file");
        }
        return out;
    }

    private void silentDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // Best-effort cleanup of request-scoped temporary files.
        }
    }
}
