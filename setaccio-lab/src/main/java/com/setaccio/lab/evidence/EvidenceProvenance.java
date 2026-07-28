package com.setaccio.lab.evidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringBootVersion;

public final class EvidenceProvenance {

    private static final String UNKNOWN = "unknown";

    private EvidenceProvenance() {}

    public static EvidenceCodeBaseline captureCodeBaseline(Path workingDirectory) {
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory must not be null");
        }
        GitResult commit = runGit(workingDirectory, "rev-parse", "HEAD");
        GitResult status = runGit(workingDirectory, "status", "--porcelain", "--untracked-files=all");
        return new EvidenceCodeBaseline(
                commit.success() && !commit.output().isBlank() ? commit.output() : UNKNOWN,
                !status.success() || !status.output().isBlank()
        );
    }

    public static EvidenceFrameworkVersions detectFrameworkVersions() {
        return new EvidenceFrameworkVersions(
                versionOrUnknown(SpringBootVersion.getVersion()),
                versionOrUnknown(ChatModel.class.getPackage().getImplementationVersion())
        );
    }

    private static GitResult runGit(Path workingDirectory, String... arguments) {
        String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = workingDirectory.toAbsolutePath().normalize().toString();
        System.arraycopy(arguments, 0, command, 3, arguments.length);

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new GitResult(process.waitFor() == 0, output);
        } catch (IOException e) {
            return new GitResult(false, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitResult(false, "");
        }
    }

    private static String versionOrUnknown(String version) {
        return version == null || version.isBlank() ? UNKNOWN : version;
    }

    private record GitResult(boolean success, String output) {}
}
