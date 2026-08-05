package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMatrixEvidenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndVerifiesSharedV1EvidenceOffline() throws Exception {
        Path run = runDirectory("2026-08-04-chat-test");
        ChatMatrixEvidence evidence = evidence();

        Path manifestPath = evidence.write(
                run,
                ChatMatrixTestFixtures.successfulResult(),
                new EvidenceCodeBaseline("abc123", false));

        assertThat(manifestPath).isEqualTo(run.resolve("manifest.json"));
        assertThat(run.resolve(ChatMatrixProtocol.RAW_FILENAME)).isRegularFile();
        assertThat(run.resolve(ChatMatrixEvidence.SUMMARY_FILENAME)).isRegularFile();
        EvidenceManifest manifest = new EvidenceManifestStore(ChatMatrixTestFixtures.OBJECT_MAPPER).read(run);
        assertThat(manifest.manifestVersion()).isEqualTo(1);
        assertThat(manifest.suite()).isEqualTo(ChatMatrixProtocol.SUITE);
        assertThat(manifest.artifacts()).extracting(artifact -> artifact.role())
                .containsExactly(ChatMatrixEvidence.RAW_ROLE, ChatMatrixEvidence.SUMMARY_ROLE);
        assertThat(evidence.verify(run).failures()).isEmpty();
        assertThat(Files.readString(run.resolve(ChatMatrixEvidence.SUMMARY_FILENAME)))
                .contains("# Ollama Chat Matrix Summary")
                .contains("semantic answer quality")
                .doesNotContain("response-1");
    }

    @Test
    void reanalyzesSummaryDriftWithoutCallingAProvider() throws Exception {
        Path run = runDirectory("2026-08-04-chat-reanalyze");
        ChatMatrixEvidence evidence = evidence();
        evidence.write(
                run,
                ChatMatrixTestFixtures.diagnosticResult(),
                new EvidenceCodeBaseline("abc123", true));
        Path summary = run.resolve(ChatMatrixEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary);
        Files.writeString(summary, "drift", StandardCharsets.UTF_8);

        assertThat(evidence.verify(run).valid()).isFalse();
        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(Files.readString(summary)).isEqualTo(expected);
    }

    @Test
    void rejectsTamperedRawEvidenceAndUnexpectedArtifacts() throws Exception {
        Path tampered = runDirectory("2026-08-04-chat-tampered");
        ChatMatrixEvidence evidence = evidence();
        evidence.write(
                tampered,
                ChatMatrixTestFixtures.successfulResult(),
                new EvidenceCodeBaseline("abc123", false));
        Files.writeString(tampered.resolve(ChatMatrixProtocol.RAW_FILENAME), "{}", StandardCharsets.UTF_8);
        assertThat(evidence.verify(tampered).failures())
                .anyMatch(failure -> failure.contains("SHA-256") || failure.contains("size"));

        Path unexpected = runDirectory("2026-08-04-chat-unexpected");
        evidence.write(
                unexpected,
                ChatMatrixTestFixtures.successfulResult(),
                new EvidenceCodeBaseline("abc123", false));
        Files.writeString(unexpected.resolve("extra.txt"), "unexpected", StandardCharsets.UTF_8);
        assertThat(evidence.verify(unexpected).failures())
                .contains("Unexpected artifact is present in chat matrix evidence: extra.txt.");
    }

    private ChatMatrixEvidence evidence() {
        return new ChatMatrixEvidence(
                ChatMatrixTestFixtures.OBJECT_MAPPER,
                ChatMatrixTestFixtures.CATALOG);
    }

    private Path runDirectory(String name) {
        return EvidenceRunDirectory.createNamed(temporaryDirectory, name);
    }
}
