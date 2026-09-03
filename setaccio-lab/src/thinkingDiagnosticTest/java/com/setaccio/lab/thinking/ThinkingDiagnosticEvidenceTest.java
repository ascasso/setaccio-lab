package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThinkingDiagnosticEvidenceTest {

    private static final EvidenceCodeBaseline BASELINE =
            new EvidenceCodeBaseline("a".repeat(40), false);

    private final ObjectMapper objectMapper = ThinkingDiagnosticTestSupport.objectMapper();
    private final LocalFactCheckFixtureCatalog catalog = ThinkingDiagnosticTestSupport.catalog();

    @Test
    void writesVerifiesAndDeterministicallyReanalyzesOneSavedRun(@TempDir Path root) throws IOException {
        Path run = Files.createDirectory(root.resolve("2026-09-03-thinking"));
        ThinkingDiagnosticEvidence evidence = evidence();

        evidence.write(run, result(), BASELINE);

        assertThat(evidence.verify(run).failures()).isEmpty();
        String summaryBefore = Files.readString(run.resolve(ThinkingDiagnosticEvidence.SUMMARY_FILENAME));
        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(Files.readString(run.resolve(ThinkingDiagnosticEvidence.SUMMARY_FILENAME)))
                .isEqualTo(summaryBefore);
        assertThat(Files.list(run).map(path -> path.getFileName().toString()).sorted().toList())
                .containsExactly("SUMMARY.md", "manifest.json", "thinking-diagnostic-results.json");
    }

    @Test
    void keepsRecordedContentAndReasoningOutOfTheDeterministicSummary(@TempDir Path root)
            throws IOException {
        Path run = Files.createDirectory(root.resolve("2026-09-03-thinking"));
        evidence().write(run, result(), BASELINE);

        String summary = Files.readString(run.resolve(ThinkingDiagnosticEvidence.SUMMARY_FILENAME));
        assertThat(summary).doesNotContain("reasoning trace");
        assertThat(summary).contains("EMPTY_CONTENT_WITH_THINKING");
        assertThat(summary).contains("Interpretation boundary");
        assertThat(summary).contains("not a rerun, repair,");

        String raw = Files.readString(
                run.resolve(ThinkingDiagnosticProtocol.RAW_FILENAME), StandardCharsets.UTF_8);
        assertThat(raw).contains("reasoning trace");
        assertThat(raw).contains("\"thinking\"");
        assertThat(raw).contains("\"content\"");
    }

    @Test
    void rejectsATamperedRawArtifactAndADriftedSummary(@TempDir Path root) throws IOException {
        Path run = Files.createDirectory(root.resolve("2026-09-03-thinking"));
        ThinkingDiagnosticEvidence evidence = evidence();
        evidence.write(run, result(), BASELINE);

        Path summary = run.resolve(ThinkingDiagnosticEvidence.SUMMARY_FILENAME);
        Files.writeString(summary, Files.readString(summary) + "\ntampered\n");
        assertThat(evidence.verify(run).failures()).isNotEmpty();

        Path other = Files.createDirectory(root.resolve("2026-09-03-other"));
        evidence.write(other, result(), BASELINE);
        Path raw = other.resolve(ThinkingDiagnosticProtocol.RAW_FILENAME);
        Files.writeString(raw, Files.readString(raw).replace("reasoning trace", "changed trace"));
        List<String> failures = evidence.verify(other).failures();
        assertThat(failures).isNotEmpty();
        assertThat(String.join(" ", failures)).contains("SHA-256");
    }

    @Test
    void rejectsAnUnexpectedExtraArtifactInTheRunDirectory(@TempDir Path root) throws IOException {
        Path run = Files.createDirectory(root.resolve("2026-09-03-thinking"));
        ThinkingDiagnosticEvidence evidence = evidence();
        evidence.write(run, result(), BASELINE);
        Files.writeString(run.resolve("notes.txt"), "unexpected");

        assertThat(String.join(" ", evidence.verify(run).failures()))
                .contains("Unexpected artifact");
    }

    private ThinkingDiagnosticEvidence evidence() {
        return new ThinkingDiagnosticEvidence(objectMapper, catalog);
    }

    private ThinkingDiagnosticResult result() {
        return new ThinkingDiagnosticExecutor(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(),
                ThinkingDiagnosticTestSupport.prompt())
                .execute(catalog, ThinkingDiagnosticTestSupport.identities(), "0.33.2");
    }
}
