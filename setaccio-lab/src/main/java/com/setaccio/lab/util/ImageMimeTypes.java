package com.setaccio.lab.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

public final class ImageMimeTypes {

    public static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private ImageMimeTypes() {
    }

    public static boolean isSupported(String contentType) {
        return contentType != null && SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    public static MimeType detect(Path imagePath) {
        byte[] header = new byte[12];
        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            int read = inputStream.read(header);
            if (read < 4) {
                return MimeTypeUtils.IMAGE_JPEG;
            }
        } catch (IOException e) {
            return MimeTypeUtils.IMAGE_JPEG;
        }

        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return MimeTypeUtils.parseMimeType("image/gif");
        }
        if (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }
}
