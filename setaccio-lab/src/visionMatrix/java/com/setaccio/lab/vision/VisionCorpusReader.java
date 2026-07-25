package com.setaccio.lab.vision;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.util.ImageMimeTypes;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VisionCorpusReader {

    static final int CURRENT_VERSION = 1;
    static final String CATALOG_FILENAME = "cases.json";
    private static final String SAFE_ID = "[a-z0-9]+(?:-[a-z0-9]+)*";
    private static final String BLAKE3 = "[0-9a-f]{64}";
    private static final String IMAGE_FILE = "images/%s\\.(?:jpg|jpeg|png|gif|webp)";

    private final ObjectMapper objectMapper;
    private final Blake3HashingService hashingService;

    VisionCorpusReader(ObjectMapper objectMapper, Blake3HashingService hashingService) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (hashingService == null) {
            throw new IllegalArgumentException("hashingService must not be null");
        }
        this.objectMapper = objectMapper;
        this.hashingService = hashingService;
    }

    LoadedVisionCorpus read(Path corpusDirectory) {
        Path root = requireDirectory(corpusDirectory);
        Path catalogPath = root.resolve(CATALOG_FILENAME);
        requireRegularFile(catalogPath, "Vision corpus catalog");

        VisionCorpusCatalog catalog;
        try {
            catalog = objectMapper.readerFor(VisionCorpusCatalog.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(catalogPath.toFile());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read vision corpus catalog: " + safeMessage(e), e);
        }
        if (catalog.corpusVersion() != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported vision corpus version: " + catalog.corpusVersion());
        }
        if (catalog.cases().isEmpty()) {
            throw new IllegalArgumentException("Vision corpus must contain at least one case");
        }

        Set<String> ids = new LinkedHashSet<>();
        Set<String> imageFiles = new HashSet<>();
        List<LoadedVisionCorpus.LoadedVisionCase> loaded = new ArrayList<>();
        for (VisionCorpusCase visionCase : catalog.cases()) {
            validateMetadata(visionCase, ids, imageFiles);
            Path imagePath = root.resolve(visionCase.imageFile()).normalize();
            if (!imagePath.startsWith(root)) {
                throw new IllegalArgumentException(
                        "Vision corpus image escapes the corpus directory: " + visionCase.caseId());
            }
            requireRegularFile(imagePath, "Vision corpus image " + visionCase.caseId());
            validateImageIdentity(visionCase, imagePath);
            loaded.add(new LoadedVisionCorpus.LoadedVisionCase(visionCase, imagePath));
        }
        return new LoadedVisionCorpus(catalog.corpusVersion(), loaded);
    }

    private void validateMetadata(
            VisionCorpusCase visionCase,
            Set<String> ids,
            Set<String> imageFiles) {
        if (visionCase == null) {
            throw new IllegalArgumentException("Vision corpus case must not be null");
        }
        String caseId = requireText(visionCase.caseId(), "caseId");
        if (!caseId.matches(SAFE_ID)) {
            throw new IllegalArgumentException("Vision corpus caseId is unsafe: " + caseId);
        }
        if (!ids.add(caseId)) {
            throw new IllegalArgumentException("Duplicate vision corpus caseId: " + caseId);
        }

        String imageFile = requireText(visionCase.imageFile(), "imageFile");
        if (!imageFile.matches(IMAGE_FILE.formatted(caseId))) {
            throw new IllegalArgumentException(
                    "Vision corpus imageFile must use images/<caseId>.<supported-extension>: " + caseId);
        }
        if (!imageFiles.add(imageFile)) {
            throw new IllegalArgumentException("Duplicate vision corpus imageFile: " + imageFile);
        }

        String mimeType = normalizeMimeType(requireText(visionCase.mimeType(), "mimeType"));
        if (!mimeType.equals(visionCase.mimeType().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Vision corpus MIME type must use canonical image/jpeg: " + caseId);
        }
        if (!ImageMimeTypes.isSupported(mimeType)) {
            throw new IllegalArgumentException("Unsupported vision corpus MIME type for " + caseId);
        }
        if (!extensionMatchesMimeType(imageFile, mimeType)) {
            throw new IllegalArgumentException(
                    "Vision corpus image extension does not match its MIME type: " + caseId);
        }
        String blake3 = requireText(visionCase.blake3(), "blake3");
        if (!blake3.matches(BLAKE3)) {
            throw new IllegalArgumentException(
                    "Vision corpus BLAKE3 must be 64 lowercase hexadecimal characters: " + caseId);
        }
        requireText(visionCase.referenceObservation(), "referenceObservation");
        requireTextList(visionCase.expectedConcepts(), "expectedConcepts", caseId);
        requireTextList(visionCase.unsupportedDetails(), "unsupportedDetails", caseId);
        requireTextList(visionCase.limitations(), "limitations", caseId);

        VisionPrivacyReview privacyReview = visionCase.privacyReview();
        if (privacyReview == null) {
            throw new IllegalArgumentException("Vision corpus privacyReview is required: " + caseId);
        }
        if (!privacyReview.sensitiveContentReviewed()) {
            throw new IllegalArgumentException(
                    "Vision corpus sensitive-content review is incomplete: " + caseId);
        }
    }

    private void validateImageIdentity(VisionCorpusCase visionCase, Path imagePath) {
        String actualMimeType = detectStrictMimeType(imagePath);
        String declaredMimeType = normalizeMimeType(visionCase.mimeType());
        if (!declaredMimeType.equals(actualMimeType)) {
            throw new IllegalArgumentException(
                    "Vision corpus MIME type does not match input bytes: " + visionCase.caseId());
        }

        String actualHash;
        try (InputStream input = Files.newInputStream(imagePath)) {
            actualHash = hashingService.hashInputStream(input);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to hash vision corpus image: " + visionCase.caseId(), e);
        }
        if (!visionCase.blake3().equals(actualHash)) {
            throw new IllegalArgumentException(
                    "Vision corpus BLAKE3 does not match input bytes: " + visionCase.caseId());
        }
    }

    private static Path requireDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Vision corpus directory must not be null");
        }
        Path root = directory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Vision corpus directory is missing or unsafe");
        }
        return root;
    }

    private static void requireRegularFile(Path path, String label) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " is missing or unsafe");
        }
        try {
            if (Files.size(path) == 0) {
                throw new IllegalArgumentException(label + " must not be empty");
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException(label + " could not be inspected", e);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Vision corpus " + field + " must not be blank");
        }
        return value;
    }

    private static void requireTextList(List<String> values, String field, String caseId) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "Vision corpus " + field + " must contain non-blank values: " + caseId);
        }
    }

    private static String normalizeMimeType(String mimeType) {
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private static boolean extensionMatchesMimeType(String imageFile, String mimeType) {
        String extension = imageFile.substring(imageFile.lastIndexOf('.') + 1);
        return switch (mimeType) {
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/png" -> "png".equals(extension);
            case "image/gif" -> "gif".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            default -> false;
        };
    }

    private static String detectStrictMimeType(Path imagePath) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = Files.newInputStream(imagePath)) {
            read = input.read(header);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to inspect vision corpus image bytes", e);
        }
        if (read >= 4
                && header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4e
                && header[3] == 0x47) {
            return "image/png";
        }
        if (read >= 3
                && header[0] == (byte) 0xff
                && header[1] == (byte) 0xd8
                && header[2] == (byte) 0xff) {
            return "image/jpeg";
        }
        if (read >= 4
                && header[0] == 0x47
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x38) {
            return "image/gif";
        }
        if (read >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50) {
            return "image/webp";
        }
        throw new IllegalArgumentException("Vision corpus image bytes have an unsupported MIME type");
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
