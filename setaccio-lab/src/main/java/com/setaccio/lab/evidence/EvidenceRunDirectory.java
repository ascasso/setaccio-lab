package com.setaccio.lab.evidence;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class EvidenceRunDirectory {

    private static final int MAX_ALLOCATION_ATTEMPTS = 100;
    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    private EvidenceRunDirectory() {}

    public static Path createUnique(Path root, String suite, Instant startedAt) {
        requireRoot(root);
        requireSafeSegment(suite, "suite");
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }

        createRoot(root);
        String prefix = RUN_TIMESTAMP.format(startedAt) + "-" + suite + "-";
        for (int attempt = 0; attempt < MAX_ALLOCATION_ATTEMPTS; attempt++) {
            String runId = prefix + UUID.randomUUID().toString().substring(0, 8);
            try {
                return Files.createDirectory(root.resolve(runId));
            } catch (FileAlreadyExistsException ignored) {
                // Allocate another run id without reusing or truncating the existing directory.
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create evidence run directory", e);
            }
        }
        throw new IllegalStateException("Unable to allocate a unique evidence run directory");
    }

    public static Path createNamed(Path root, String runId) {
        requireRoot(root);
        requireSafeSegment(runId, "runId");
        createRoot(root);
        try {
            return Files.createDirectory(root.resolve(runId));
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Evidence run directory already exists: " + runId, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create evidence run directory", e);
        }
    }

    private static void createRoot(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create evidence root directory", e);
        }
    }

    private static void requireRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
    }

    private static void requireSafeSegment(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(name + " must be one safe path segment");
        }
    }
}
