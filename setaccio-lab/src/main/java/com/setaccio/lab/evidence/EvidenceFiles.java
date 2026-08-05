package com.setaccio.lab.evidence;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

public final class EvidenceFiles {

    private EvidenceFiles() {}

    public static Path requireWritableRunDirectory(
            Path runDirectory,
            String nullMessage,
            String unsafeMessage
    ) {
        if (runDirectory == null) {
            throw new IllegalArgumentException(nullMessage);
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(unsafeMessage);
        }
        return root;
    }

    public static Path inspectRunDirectory(
            Path runDirectory,
            List<String> failures,
            String nullFailure,
            String unsafeFailure
    ) {
        if (runDirectory == null) {
            failures.add(nullFailure);
            return null;
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            failures.add(unsafeFailure);
            return null;
        }
        return root;
    }

    public static void writeNewBytes(Path target, byte[] bytes, String failureMessage) {
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    public static void writeNewText(Path target, String text, String failureMessage) {
        try {
            Files.writeString(
                    target,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (Exception exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    public static EvidenceArtifact singleArtifact(
            List<EvidenceArtifact> artifacts,
            String role,
            List<String> failures,
            String failureMessage
    ) {
        List<EvidenceArtifact> matches = artifacts.stream()
                .filter(artifact -> role.equals(artifact.role()))
                .toList();
        if (matches.size() != 1) {
            failures.add(failureMessage);
            return null;
        }
        return matches.getFirst();
    }

    public static Path resolveArtifact(
            Path root,
            EvidenceArtifact artifact,
            List<String> failures,
            String escapeFailure
    ) {
        if (artifact == null) {
            return null;
        }
        Path resolved = root.resolve(artifact.path()).normalize();
        if (!resolved.startsWith(root)) {
            failures.add(escapeFailure);
            return null;
        }
        return resolved;
    }

    public static boolean verifyArtifact(
            Path artifactPath,
            EvidenceArtifact artifact,
            boolean verifyDeclaredSize,
            List<String> failures,
            String missingOrUnsafeFailure,
            String emptyFailure,
            String sizeFailure,
            String sha256Failure,
            String inspectionFailure
    ) {
        if (artifactPath == null || artifact == null) {
            return false;
        }
        if (Files.isSymbolicLink(artifactPath)
                || !Files.isRegularFile(artifactPath, LinkOption.NOFOLLOW_LINKS)) {
            failures.add(missingOrUnsafeFailure);
            return false;
        }
        try {
            long size = Files.size(artifactPath);
            if (size == 0) {
                failures.add(emptyFailure);
                return false;
            }
            if (verifyDeclaredSize && size != artifact.sizeBytes()) {
                failures.add(sizeFailure);
            }
            if (!EvidenceIntegrity.sha256(artifactPath).equals(artifact.sha256())) {
                failures.add(sha256Failure);
                return false;
            }
            return true;
        } catch (Exception exception) {
            failures.add(inspectionFailure);
            return false;
        }
    }

    public static void validateTextDescriptor(
            EvidenceArtifact artifact,
            String expectedText,
            List<String> failures,
            String mismatchFailure
    ) {
        if (artifact == null || expectedText == null) {
            return;
        }
        byte[] bytes = expectedText.getBytes(StandardCharsets.UTF_8);
        if (artifact.sizeBytes() != bytes.length
                || !artifact.sha256().equals(EvidenceIntegrity.sha256(bytes))) {
            failures.add(mismatchFailure);
        }
    }

    public static void validateLayout(
            Path root,
            Set<String> allowedFiles,
            List<String> failures,
            String symbolicLinkPrefix,
            String unexpectedArtifactPrefix,
            String inspectionFailure
    ) {
        validateLayout(
                root,
                allowedFiles,
                failures,
                symbolicLinkPrefix,
                null,
                unexpectedArtifactPrefix,
                inspectionFailure);
    }

    public static void validateLayout(
            Path root,
            Set<String> allowedFiles,
            List<String> failures,
            String symbolicLinkPrefix,
            String unexpectedDirectoryPrefix,
            String unexpectedArtifactPrefix,
            String inspectionFailure
    ) {
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    failures.add(symbolicLinkPrefix + relative + ".");
                } else if (unexpectedDirectoryPrefix != null
                        && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    failures.add(unexpectedDirectoryPrefix + relative + ".");
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !allowedFiles.contains(relative)) {
                    failures.add(unexpectedArtifactPrefix + relative + ".");
                }
            });
        } catch (Exception exception) {
            failures.add(inspectionFailure);
        }
    }

    public static void verifyText(
            Path path,
            String expectedText,
            Set<String> failures,
            String missingOrUnsafeFailure,
            String emptyFailure,
            String mismatchFailure,
            String inspectionFailure
    ) {
        if (path == null || expectedText == null) {
            return;
        }
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            failures.add(missingOrUnsafeFailure);
            return;
        }
        try {
            if (Files.size(path) == 0) {
                failures.add(emptyFailure);
            } else if (!Files.readString(path, StandardCharsets.UTF_8).equals(expectedText)) {
                failures.add(mismatchFailure);
            }
        } catch (Exception exception) {
            failures.add(inspectionFailure);
        }
    }

    public static void replaceTextAtomically(
            Path target,
            String text,
            String temporaryPrefix,
            String symbolicLinkMessage,
            String failureMessage
    ) {
        Path root = target.getParent();
        Path temporary = null;
        try {
            if (symbolicLinkMessage != null && Files.isSymbolicLink(target)) {
                throw new IllegalArgumentException(symbolicLinkMessage);
            }
            temporary = Files.createTempFile(root, temporaryPrefix, ".tmp");
            Files.writeString(
                    temporary,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(failureMessage, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // Preserve the primary failure.
                }
            }
        }
    }

    public static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
