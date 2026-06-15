package com.setaccio.lab.model;

import java.nio.file.Path;

public record UploadedImage(
        String originalFilename,
        String contentType,
        long size,
        Path path
) {}
