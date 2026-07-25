package com.setaccio.lab.evidence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class EvidenceManifestStore {

    public static final String MANIFEST_FILENAME = "manifest.json";

    private final ObjectMapper objectMapper;

    public EvidenceManifestStore(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    public Path write(Path runDirectory, EvidenceManifest manifest) {
        Path root = requireRunDirectory(runDirectory);
        requireManifestForDirectory(root, manifest);
        Path manifestPath = root.resolve(MANIFEST_FILENAME);
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            return Files.write(manifestPath, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("Evidence manifest already exists for run " + manifest.runId(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write evidence manifest", e);
        }
    }

    public EvidenceManifest read(Path runDirectory) {
        Path root = requireRunDirectory(runDirectory);
        Path manifestPath = root.resolve(MANIFEST_FILENAME);
        if (Files.isSymbolicLink(manifestPath)
                || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Evidence manifest is missing or is not a regular file");
        }
        try {
            EvidenceManifest manifest = objectMapper.readerFor(EvidenceManifest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(manifestPath.toFile());
            requireManifestForDirectory(root, manifest);
            if (manifest.manifestVersion() != EvidenceManifest.CURRENT_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported evidence manifest version: " + manifest.manifestVersion());
            }
            return manifest;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read evidence manifest", e);
        }
    }

    private static Path requireRunDirectory(Path runDirectory) {
        if (runDirectory == null) {
            throw new IllegalArgumentException("runDirectory must not be null");
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Evidence run directory is missing or is not a regular directory");
        }
        return root;
    }

    private static void requireManifestForDirectory(Path root, EvidenceManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        if (manifest.manifestVersion() != EvidenceManifest.CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported evidence manifest version: " + manifest.manifestVersion());
        }
        Path fileName = root.getFileName();
        if (fileName == null || !fileName.toString().equals(manifest.runId())) {
            throw new IllegalArgumentException("Manifest runId does not match its evidence directory");
        }
    }
}
