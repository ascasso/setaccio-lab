package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalEvaluationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void evaluatesTheConfirmedFixturesWithPinnedRetrievalOnlyMetricsAndDocumentText() throws Exception {
        Inputs inputs = inputs();
        RetrievalEvaluationResult result = execute(inputs);
        RetrievalEvaluationAnalyzer.Analysis analysis = new RetrievalEvaluationAnalyzer(
                inputs.corpus(), inputs.catalog(), new DeterministicLexicalRetriever()).analyze(result);

        assertThat(analysis.integrityFailures()).isEmpty();
        assertThat(analysis.matchingFixtures()).isEqualTo(12);
        assertThat(analysis.expectedSupportingDocumentsRetrieved()).isEqualTo(12);
        assertThat(analysis.expectedSupportingDocumentsInTop1()).isEqualTo(12);
        assertThat(analysis.expectedSupportingDocumentsInTop3()).isEqualTo(12);
        assertThat(analysis.forbiddenDocumentRetrievedFixtures()).isZero();
        assertThat(analysis.noMatchFixtures()).isEqualTo(2);
        assertThat(analysis.correctNoMatchFixtures()).isEqualTo(2);
        assertThat(analysis.stableRows()).isEqualTo(14);

        RetrievalEvaluationRow first = result.rows().getFirst();
        RetrievalEvaluationRetrievedDocument captured = first.retrievedDocuments().getFirst();
        RetrievalDocument corpusDocument = inputs.corpus().documents().stream()
                .filter(document -> document.documentId().equals(captured.documentId()))
                .findFirst()
                .orElseThrow();
        assertThat(captured.content()).isEqualTo(corpusDocument.content());
        assertThat(captured.contentSha256()).isEqualTo(corpusDocument.contentSha256());
        assertThat(captured.rank()).isEqualTo(first.retrieval().hits().getFirst().rank());
        assertThat(captured.matchedTerms()).isEqualTo(first.retrieval().hits().getFirst().matchedTerms());
    }

    @Test
    void writesVerifiesAndRegeneratesOnlyTheDeterministicSummary() throws Exception {
        Inputs inputs = inputs();
        RetrievalEvaluationEvidence evidence = evidence(inputs);
        Path run = Files.createDirectory(temporaryDirectory.resolve("r3-run"));
        evidence.write(run, execute(inputs), new EvidenceCodeBaseline("test-baseline", false));

        assertThat(evidence.verify(run).failures()).isEmpty();
        Path summary = run.resolve(RetrievalEvaluationEvidence.SUMMARY_FILENAME);
        Files.writeString(summary, "changed summary\n");
        assertThat(evidence.verify(run).failures())
                .anyMatch(failure -> failure.contains("summary"));

        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(evidence.verify(run).failures()).isEmpty();
        assertThat(Files.readString(summary)).contains("# Retrieval-Only Evaluation");
    }

    @Test
    void rejectsRawOrLayoutTamperingWithoutRepairingThoseArtifacts() throws Exception {
        Inputs inputs = inputs();
        RetrievalEvaluationEvidence evidence = evidence(inputs);
        Path run = Files.createDirectory(temporaryDirectory.resolve("tampered-run"));
        evidence.write(run, execute(inputs), new EvidenceCodeBaseline("test-baseline", false));
        Files.writeString(run.resolve("unexpected.txt"), "not declared\n");

        assertThat(evidence.verify(run).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
        assertThat(evidence.reanalyze(run).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
    }

    @Test
    void comparesTwoVerifiedCompatibleRunsWithoutProviderAccess() throws Exception {
        Inputs inputs = inputs();
        RetrievalEvaluationEvidence evidence = evidence(inputs);
        Path baseline = Files.createDirectory(temporaryDirectory.resolve("baseline-run"));
        Path candidate = Files.createDirectory(temporaryDirectory.resolve("candidate-run"));
        evidence.write(baseline, execute(inputs), new EvidenceCodeBaseline("baseline", false));
        evidence.write(candidate, execute(inputs), new EvidenceCodeBaseline("candidate", false));

        String report = new RetrievalEvaluationComparison(
                JsonMapper.builder().findAndAddModules().build(),
                inputs.corpus(),
                inputs.catalog()).compare(baseline, candidate).report();

        assertThat(report).contains("# Offline Retrieval Evaluation Comparison");
        assertThat(report).contains("Expected supporting document retrieved | 12 | 12 | 0");
        assertThat(report).contains("`garden-compost-accepted-materials`");
        assertThat(report).contains("`garden-compost-basics`");
    }

    @Test
    void restrictsNewEvidenceOutputToTheDedicatedDatedRoot() {
        assertThat(RetrievalEvaluationRunner.resolveNewOutputDirectory(
                "build/retrieval-evaluation/2026-08-28-r3").getFileName().toString())
                .isEqualTo("2026-08-28-r3");
        assertThatThrownBy(() -> RetrievalEvaluationRunner.resolveNewOutputDirectory("build/elsewhere/2026-08-28-r3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("build/retrieval-evaluation");
        assertThatThrownBy(() -> RetrievalEvaluationRunner.resolveNewOutputDirectory("build/retrieval-evaluation/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    private static RetrievalEvaluationEvidence evidence(Inputs inputs) {
        return new RetrievalEvaluationEvidence(
                JsonMapper.builder().findAndAddModules().build(), inputs.corpus(), inputs.catalog());
    }

    private static RetrievalEvaluationResult execute(Inputs inputs) {
        return new RetrievalEvaluationExecutor(new DeterministicLexicalRetriever())
                .execute(inputs.corpus(), inputs.catalog());
    }

    private static Inputs inputs() throws Exception {
        RetrievalCorpus corpus = new RetrievalCorpusLoader().loadApproved(packagedCorpusRoot());
        RetrievalQueryCatalog catalog = new RetrievalQueryCatalogLoader()
                .loadConfirmed(packagedQueryCatalogRoot(), corpus);
        return new Inputs(corpus, catalog);
    }

    private static Path packagedCorpusRoot() throws URISyntaxException {
        return resourcePath("retrieval/corpus-v1", "Packaged retrieval corpus resource is missing");
    }

    private static Path packagedQueryCatalogRoot() throws URISyntaxException {
        return resourcePath("retrieval/query-fixtures-v1", "Packaged retrieval query catalog is missing");
    }

    private static Path resourcePath(String resourceName, String missingMessage) throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                RetrievalEvaluationTest.class.getClassLoader().getResource(resourceName),
                missingMessage);
        return Path.of(resource.toURI());
    }

    private record Inputs(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {}
}
