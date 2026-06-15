package com.setaccio.lab.util;

import com.setaccio.lab.model.UploadedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public final class MultipartUploads {

    private MultipartUploads() {
    }

    public static List<UploadedImage> persistImages(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one multipart file");
        }

        List<UploadedImage> images = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (!ImageMimeTypes.isSupported(contentType)) {
                cleanup(images);
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Unsupported file type: " + contentType + ". Supported: " + ImageMimeTypes.SUPPORTED_CONTENT_TYPES);
            }

            String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "image"
                    : file.getOriginalFilename();
            Path tempFile = Files.createTempFile("lab-vision-", "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            images.add(new UploadedImage(name, contentType, file.getSize(), tempFile));
        }

        if (images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one non-empty file");
        }
        return images;
    }

    public static void cleanup(List<UploadedImage> images) {
        for (UploadedImage image : images) {
            try {
                Files.deleteIfExists(image.path());
            } catch (IOException ignored) {
                // Best-effort cleanup of request-scoped temporary files.
            }
        }
    }
}
