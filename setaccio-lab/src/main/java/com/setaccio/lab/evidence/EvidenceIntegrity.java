package com.setaccio.lab.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class EvidenceIntegrity {

    private EvidenceIntegrity() {}

    public static EvidenceArtifact describe(Path runDirectory, Path artifact, String role) {
        Path root = normalizedRoot(runDirectory);
        if (artifact == null) {
            throw new IllegalArgumentException("artifact must not be null");
        }

        Path normalizedArtifact = artifact.toAbsolutePath().normalize();
        if (!normalizedArtifact.startsWith(root)) {
            throw new IllegalArgumentException("artifact must be inside the evidence run directory");
        }
        if (Files.isSymbolicLink(normalizedArtifact)
                || !Files.isRegularFile(normalizedArtifact, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("artifact must be a regular non-symbolic file");
        }

        try {
            long sizeBytes = Files.size(normalizedArtifact);
            if (sizeBytes == 0) {
                throw new IllegalArgumentException("artifact must not be empty");
            }
            String relativePath = root.relativize(normalizedArtifact).toString().replace('\\', '/');
            if (EvidenceManifestStore.MANIFEST_FILENAME.equals(relativePath)) {
                throw new IllegalArgumentException("manifest.json is not a declared artifact");
            }
            return new EvidenceArtifact(relativePath, role, sizeBytes, sha256(normalizedArtifact));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to describe evidence artifact", e);
        }
    }

    public static String sha256(Path artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("artifact must not be null");
        }
        try (InputStream input = Files.newInputStream(artifact);
             DigestInputStream digestInput = new DigestInputStream(input, sha256Digest())) {
            digestInput.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to hash evidence artifact", e);
        }
    }

    public static String sha256(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static Path normalizedRoot(Path runDirectory) {
        if (runDirectory == null) {
            throw new IllegalArgumentException("runDirectory must not be null");
        }
        return runDirectory.toAbsolutePath().normalize();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
