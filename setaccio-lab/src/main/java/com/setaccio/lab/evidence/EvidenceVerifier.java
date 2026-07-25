package com.setaccio.lab.evidence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EvidenceVerifier {

    public EvidenceVerification verify(Path runDirectory, EvidenceManifest manifest) {
        List<String> failures = new ArrayList<>();
        if (runDirectory == null) {
            return new EvidenceVerification(List.of("Evidence run directory must not be null."));
        }
        if (manifest == null) {
            return new EvidenceVerification(List.of("Evidence manifest must not be null."));
        }

        Path root = runDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return new EvidenceVerification(List.of(
                    "Evidence run directory is missing or is not a regular directory."));
        }

        if (manifest.manifestVersion() != EvidenceManifest.CURRENT_VERSION) {
            failures.add("Unsupported manifest version " + manifest.manifestVersion() + ".");
        }
        Path fileName = root.getFileName();
        if (fileName == null || !fileName.toString().equals(manifest.runId())) {
            failures.add("Manifest runId does not match its evidence directory.");
        }
        if (manifest.artifacts().isEmpty()) {
            failures.add("Manifest declares no artifacts.");
        }

        Set<String> declared = new HashSet<>();
        for (EvidenceArtifact artifact : manifest.artifacts()) {
            verifyDeclaredArtifact(root, artifact, declared, failures);
        }
        verifyNoUndeclaredArtifacts(root, declared, failures);
        return new EvidenceVerification(failures);
    }

    private static void verifyDeclaredArtifact(
            Path root,
            EvidenceArtifact artifact,
            Set<String> declared,
            List<String> failures) {
        String relativePath = artifact.path();
        if (!declared.add(relativePath)) {
            failures.add("Artifact is declared more than once: " + relativePath + ".");
            return;
        }
        if (EvidenceManifestStore.MANIFEST_FILENAME.equals(relativePath)) {
            failures.add("manifest.json must not declare itself as an artifact.");
            return;
        }

        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            failures.add("Artifact path escapes the evidence directory: " + relativePath + ".");
            return;
        }
        if (Files.isSymbolicLink(resolved)) {
            failures.add("Declared artifact must not be a symbolic link: " + relativePath + ".");
            return;
        }
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            failures.add("Declared artifact is missing: " + relativePath + ".");
            return;
        }

        try {
            long actualSize = Files.size(resolved);
            if (actualSize == 0) {
                failures.add("Declared artifact is empty: " + relativePath + ".");
                return;
            }
            if (actualSize != artifact.sizeBytes()) {
                failures.add("Declared artifact size does not match manifest: " + relativePath + ".");
            }
            String actualSha256 = EvidenceIntegrity.sha256(resolved);
            if (!actualSha256.equals(artifact.sha256())) {
                failures.add("Declared artifact SHA-256 does not match manifest: " + relativePath + ".");
            }
        } catch (IOException e) {
            failures.add("Unable to inspect declared artifact: " + relativePath + ".");
        } catch (IllegalStateException e) {
            failures.add("Unable to hash declared artifact: " + relativePath + ".");
        }
    }

    private static void verifyNoUndeclaredArtifacts(
            Path root,
            Set<String> declared,
            List<String> failures) {
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                String relativePath = root.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    if (!declared.contains(relativePath)) {
                        failures.add("Undeclared symbolic link is present: " + relativePath + ".");
                    }
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !EvidenceManifestStore.MANIFEST_FILENAME.equals(relativePath)
                        && !declared.contains(relativePath)) {
                    failures.add("Undeclared artifact is present: " + relativePath + ".");
                }
            });
        } catch (IOException e) {
            failures.add("Unable to inspect the evidence run directory.");
        }
    }
}
