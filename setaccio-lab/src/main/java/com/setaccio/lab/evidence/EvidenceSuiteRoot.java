package com.setaccio.lab.evidence;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * The single durable formal-evidence path contract shared by every suite.
 *
 * <p>Formal evidence is allocated only under the private, ignored durable root
 * {@code local/evidence/<suite>/}, resolved against the lab project directory. That root is not a
 * Gradle output directory, so {@code clean} cannot delete a saved run.
 *
 * <p>The legacy {@code build/<suite>/} root stays readable so already-saved runs can still be
 * verified, reanalyzed, compared, and consumed. It is read-only: no writer allocates there, and
 * nothing moves, repairs, or rewrites evidence that lives there.
 *
 * <p>Suites keep their own run-id date rules; this type owns only the root, direct-child,
 * traversal, and symlink policy so those are not reimplemented per runner.
 */
public final class EvidenceSuiteRoot {

    /** Private durable formal-evidence root, relative to the lab project directory. */
    public static final String DURABLE_ROOT = "local/evidence";

    /** Legacy formal-evidence root, accepted for reading already-saved runs only. */
    public static final String LEGACY_ROOT = "build";

    private static final String SAFE_SEGMENT = "[A-Za-z0-9][A-Za-z0-9._-]*";

    private final String suite;

    private EvidenceSuiteRoot(String suite) {
        if (suite == null || !suite.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("suite must be one lowercase safe path segment");
        }
        this.suite = suite;
    }

    public static EvidenceSuiteRoot of(String suite) {
        return new EvidenceSuiteRoot(suite);
    }

    public String suite() {
        return suite;
    }

    /** The durable suite root as written in commands and documentation. */
    public String durableRelativePath() {
        return DURABLE_ROOT + "/" + suite;
    }

    /** The legacy suite root as written in historical commands and documentation. */
    public String legacyRelativePath() {
        return LEGACY_ROOT + "/" + suite;
    }

    public Path durableRoot(Path projectDirectory) {
        return project(projectDirectory).resolve(durableRelativePath()).normalize();
    }

    public Path legacyRoot(Path projectDirectory) {
        return project(projectDirectory).resolve(legacyRelativePath()).normalize();
    }

    /**
     * Resolves one new formal-evidence run directory. New evidence may only be allocated as a
     * direct child of the durable root; the legacy root is never a write target.
     */
    public Path resolveNewRunDirectory(Path projectDirectory, String value, String label) {
        Path project = project(projectDirectory);
        Path target = project.resolve(requireValue(value, label)).normalize();
        if (!durableRoot(project).equals(target.getParent())) {
            throw new IllegalArgumentException(
                    label + " must be one new directory directly under " + durableRelativePath() + "/.");
        }
        requireSafeName(target, label);
        return target;
    }

    /**
     * Resolves one already-saved formal-evidence run directory without checking that it exists.
     * Accepts the durable root and, read-only, the legacy root.
     */
    public Path resolveSavedRunDirectory(Path projectDirectory, String value, String label) {
        Path project = project(projectDirectory);
        Path target = project.resolve(requireValue(value, label)).normalize();
        Path parent = target.getParent();
        if (!durableRoot(project).equals(parent) && !legacyRoot(project).equals(parent)) {
            throw new IllegalArgumentException(
                    label + " must be directly under " + durableRelativePath()
                            + "/ (or legacy " + legacyRelativePath() + "/ for already-saved evidence).");
        }
        requireSafeName(target, label);
        return target;
    }

    /**
     * Resolves one already-saved run directory and requires it to be a real, existing directory
     * rather than a symbolic link.
     */
    public Path requireSavedRunDirectory(Path projectDirectory, String value, String label) {
        Path target = resolveSavedRunDirectory(projectDirectory, value, label);
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " does not exist or is unsafe.");
        }
        return target;
    }

    /**
     * Resolves a fixed suite root that a runner writes into directly, such as a human-review
     * worksheet root. Only the durable root is accepted.
     */
    public Path resolveFixedDurableRoot(Path projectDirectory, String value, String label) {
        Path project = project(projectDirectory);
        Path target = project.resolve(requireValue(value, label)).normalize();
        Path expected = durableRoot(project);
        if (!target.equals(expected)) {
            throw new IllegalArgumentException(
                    label + " must be the fixed " + durableRelativePath() + " directory.");
        }
        if (Files.isSymbolicLink(target)
                || (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException(label + " is unsafe.");
        }
        return target;
    }

    /**
     * The project directory that a durable run directory belongs to, derived from the durable
     * root's own depth rather than a hard-coded number of parent hops.
     */
    public Path projectDirectoryOfDurableRun(Path runDirectory) {
        if (runDirectory == null) {
            throw new IllegalArgumentException("runDirectory must not be null");
        }
        Path current = runDirectory.toAbsolutePath().normalize();
        int hops = Path.of(durableRelativePath()).getNameCount() + 1;
        for (int hop = 0; hop < hops; hop++) {
            current = current.getParent();
            if (current == null) {
                throw new IllegalArgumentException(
                        "Run directory is not under " + durableRelativePath() + "/.");
            }
        }
        return current;
    }

    /** True when the resolved run directory came from the read-only legacy root. */
    public boolean isLegacy(Path projectDirectory, Path runDirectory) {
        return runDirectory != null
                && legacyRoot(projectDirectory).equals(runDirectory.toAbsolutePath().normalize().getParent());
    }

    private static Path project(Path projectDirectory) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        return projectDirectory.toAbsolutePath().normalize();
    }

    private static String requireValue(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(label + " must be a nonblank trimmed path.");
        }
        return value;
    }

    private static void requireSafeName(Path target, String label) {
        Path fileName = target.getFileName();
        if (fileName == null || !fileName.toString().matches(SAFE_SEGMENT)) {
            throw new IllegalArgumentException(label + " name must be one safe path segment.");
        }
    }
}
