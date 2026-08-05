package com.setaccio.lab.evidence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class EvidenceFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndVerifiesNonOverwritingArtifacts() throws Exception {
        Path raw = tempDir.resolve("raw.json");
        EvidenceFiles.writeNewBytes(raw, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "write failed");
        EvidenceArtifact artifact = EvidenceIntegrity.describe(tempDir, raw, "raw-result");
        List<String> failures = new ArrayList<>();

        boolean valid = EvidenceFiles.verifyArtifact(
                raw,
                artifact,
                true,
                failures,
                "missing",
                "empty",
                "size",
                "hash",
                "inspect");

        assertThat(valid).isTrue();
        assertThat(failures).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> EvidenceFiles.writeNewText(raw, "replacement", "write failed"))
                .withMessage("write failed");
    }

    @Test
    void verifiesAndAtomicallyReplacesDeterministicText() throws Exception {
        Path summary = tempDir.resolve("SUMMARY.md");
        EvidenceFiles.writeNewText(summary, "before\n", "write failed");
        Set<String> failures = new LinkedHashSet<>();

        EvidenceFiles.verifyText(
                summary,
                "before\n",
                failures,
                "missing",
                "empty",
                "different",
                "inspect");
        EvidenceFiles.replaceTextAtomically(
                summary,
                "after\n",
                ".summary-",
                "unsafe summary",
                "replace failed");

        assertThat(failures).isEmpty();
        assertThat(summary).hasContent("after\n");
        assertThat(tempDir).isDirectoryContaining(path -> path.getFileName().toString().equals("SUMMARY.md"));
        assertThat(tempDir).isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(".tmp"));
    }

    @Test
    void reportsUnsafeAndUnexpectedSavedEvidenceEntries() throws Exception {
        Path raw = Files.writeString(tempDir.resolve("raw.json"), "{}");
        Path unexpected = Files.writeString(tempDir.resolve("unexpected.txt"), "unexpected");
        List<String> failures = new ArrayList<>();

        EvidenceFiles.validateLayout(
                tempDir,
                Set.of(raw.getFileName().toString()),
                failures,
                "link: ",
                "unexpected: ",
                "inspect");

        assertThat(unexpected).exists();
        assertThat(failures).containsExactly("unexpected: unexpected.txt.");
    }

    @Test
    void validatesDeterministicTextDescriptors() throws Exception {
        Path summary = Files.writeString(tempDir.resolve("SUMMARY.md"), "# Summary\n");
        EvidenceArtifact artifact = EvidenceIntegrity.describe(tempDir, summary, "summary");
        List<String> failures = new ArrayList<>();

        EvidenceFiles.validateTextDescriptor(artifact, "# Summary\n", failures, "different");
        EvidenceFiles.validateTextDescriptor(artifact, "# Changed\n", failures, "different");

        assertThat(failures).containsExactly("different");
    }
}
