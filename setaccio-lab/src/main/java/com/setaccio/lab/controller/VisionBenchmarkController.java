package com.setaccio.lab.controller;

import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.service.VisionBenchmarkService;
import com.setaccio.lab.util.MultipartUploads;
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
import java.util.Arrays;
import java.util.List;

@Profile("local")
@RestController
@RequestMapping("/api/lab/vision")
public class VisionBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(VisionBenchmarkController.class);

    private final VisionBenchmarkService visionBenchmarkService;

    public VisionBenchmarkController(VisionBenchmarkService visionBenchmarkService) {
        this.visionBenchmarkService = visionBenchmarkService;
    }

    @PostMapping
    public ResponseEntity<BenchmarkResult> run(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("models") String modelsCsv) throws IOException {

        List<String> models = parseModels(modelsCsv);
        List<UploadedImage> images = MultipartUploads.persistImages(files);

        try {
            logger.info("Vision benchmark requested: {} models, {} images", models.size(), images.size());
            return ResponseEntity.ok(visionBenchmarkService.run(images, models));
        } finally {
            MultipartUploads.cleanup(images);
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
}
