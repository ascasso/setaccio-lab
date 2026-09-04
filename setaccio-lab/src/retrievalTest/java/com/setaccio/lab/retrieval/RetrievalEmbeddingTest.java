package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.ollama.api.OllamaApi;

class RetrievalEmbeddingTest {

    private static final int DIMENSION = 12;
    private static final RetrievalEmbeddingModelIdentity MODEL = new RetrievalEmbeddingModelIdentity(
            "test-embedding-model:latest", "test-embedding-model:latest", "a".repeat(64));

    @TempDir
    Path temporaryDirectory;

    @Test
    void embedsDocumentsAndQueriesInOneRecordedBatchThenNormalizesAndRanksThem() throws Exception {
        Inputs inputs = inputs();
        RecordingEmbeddingClient client = new RecordingEmbeddingClient(MODEL.effectiveModel(), vectors());

        RetrievalEmbeddingResult result = execute(inputs, client);
        RetrievalEmbeddingAnalyzer.Analysis analysis = analyzer(inputs).analyze(result);

        assertThat(analysis.integrityFailures()).isEmpty();
        assertThat(client.calls()).isEqualTo(1);
        assertThat(client.modelIdentities()).containsExactly(MODEL);
        assertThat(client.inputs()).containsExactlyElementsOf(expectedInputs(inputs));
        assertThat(result.documentVectors()).hasSize(12);
        assertThat(result.queryVectors()).hasSize(14);
        assertThat(result.vectorDimension()).isEqualTo(DIMENSION);
        assertThat(result.documentVectors()).allSatisfy(vector -> assertThat(l2Norm(vector.values())).isCloseTo(1.0, within(0.0001)));
        assertThat(result.queryVectors()).allSatisfy(vector -> assertThat(l2Norm(vector.values())).isCloseTo(1.0, within(0.0001)));
        assertThat(result.rows()).hasSize(14).allSatisfy(row -> assertThat(row.hits()).hasSize(3));
        assertThat(result.rows().getFirst().hits().getFirst().documentId())
                .isEqualTo(inputs.corpus().documents().getFirst().documentId());
        assertThat(result.rows().getLast().hits()).extracting(RetrievalEmbeddingHit::documentId)
                .containsExactlyElementsOf(inputs.corpus().documents().stream()
                        .map(RetrievalDocument::documentId)
                        .sorted()
                        .limit(3)
                        .toList());
    }

    @Test
    void rejectsProviderIdentityAndVectorShapeDriftBeforeEvidenceIsAllocated() throws Exception {
        Inputs inputs = inputs();

        assertThatThrownBy(() -> execute(inputs,
                new RecordingEmbeddingClient("substituted-model", vectors())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response model differs");

        List<List<Float>> wrongCount = new ArrayList<>(vectors());
        wrongCount.removeLast();
        assertThatThrownBy(() -> execute(inputs,
                new RecordingEmbeddingClient(MODEL.effectiveModel(), wrongCount)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector count");

        List<List<Float>> mismatchedDimension = new ArrayList<>(vectors());
        mismatchedDimension.set(5, List.of(1.0f, 0.0f));
        assertThatThrownBy(() -> execute(inputs,
                new RecordingEmbeddingClient(MODEL.effectiveModel(), mismatchedDimension)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one dimension");

        assertThatThrownBy(() -> execute(inputs, (modelIdentity, requestInputs) -> {
            throw new IllegalStateException("provider unavailable");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider unavailable");
    }

    @Test
    void detectsSavedRankAndNonFiniteScoreDriftFromTheRetainedVectors() throws Exception {
        Inputs inputs = inputs();
        RetrievalEmbeddingResult result = execute(inputs,
                new RecordingEmbeddingClient(MODEL.effectiveModel(), vectors()));
        List<RetrievalEmbeddingRow> rows = new ArrayList<>(result.rows());
        RetrievalEmbeddingRow first = rows.getFirst();
        List<RetrievalEmbeddingHit> hits = new ArrayList<>(first.hits());
        RetrievalEmbeddingHit topHit = hits.getFirst();
        hits.set(0, new RetrievalEmbeddingHit(
                topHit.rank(), topHit.documentId(), topHit.contentSha256(), Double.NaN));
        rows.set(0, new RetrievalEmbeddingRow(first.sequence(), first.caseId(), first.querySha256(), hits));

        assertThat(analyzer(inputs).analyze(copyWithRows(result, rows)).integrityFailures())
                .anyMatch(failure -> failure.contains("ranks or cosine scores"));
    }

    @Test
    void writesVerifiesAndRegeneratesOnlyTheDeterministicSummary() throws Exception {
        Inputs inputs = inputs();
        RetrievalEmbeddingEvidence evidence = evidence(inputs);
        Path run = Files.createDirectory(temporaryDirectory.resolve("r4-run"));
        evidence.write(run, execute(inputs, new RecordingEmbeddingClient(MODEL.effectiveModel(), vectors())),
                new EvidenceCodeBaseline("test-baseline", false));

        assertThat(evidence.verify(run).failures()).isEmpty();
        Path summary = run.resolve(RetrievalEmbeddingEvidence.SUMMARY_FILENAME);
        Files.writeString(summary, "changed summary\n");
        assertThat(evidence.verify(run).failures())
                .anyMatch(failure -> failure.contains("summary"));

        assertThat(evidence.reanalyze(run).failures()).isEmpty();
        assertThat(evidence.verify(run).failures()).isEmpty();
        assertThat(Files.readString(summary)).contains("# Local Embedding Retrieval");
    }

    @Test
    void rejectsUnexpectedEvidenceArtifactsWithoutRepairingThem() throws Exception {
        Inputs inputs = inputs();
        RetrievalEmbeddingEvidence evidence = evidence(inputs);
        Path run = Files.createDirectory(temporaryDirectory.resolve("tampered-run"));
        evidence.write(run, execute(inputs, new RecordingEmbeddingClient(MODEL.effectiveModel(), vectors())),
                new EvidenceCodeBaseline("test-baseline", false));
        Files.writeString(run.resolve("unexpected.txt"), "not declared\n");

        assertThat(evidence.verify(run).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
        assertThat(evidence.reanalyze(run).failures())
                .anyMatch(failure -> failure.contains("Unexpected artifact"));
    }

    @Test
    void requiresAnExactInstalledTagAndFullDigestBeforeGeneration() {
        OllamaApi.Model installed = new OllamaApi.Model(
                MODEL.effectiveModel(), MODEL.effectiveModel(), Instant.EPOCH, 1L, MODEL.digest(), null);
        RetrievalEmbeddingModelIdentity resolved = RetrievalEmbeddingModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(installed)), MODEL.requestedModel());

        assertThat(resolved).isEqualTo(MODEL);
        RetrievalEmbeddingModelInventory.requireEmbeddingCapability(new OllamaApi.ShowModelResponse(
                null, null, null, null, null, null, null, null, null,
                List.of("completion", "embedding"), Instant.EPOCH), resolved);
        assertThatThrownBy(() -> RetrievalEmbeddingModelInventory.requireEmbeddingCapability(
                new OllamaApi.ShowModelResponse(
                        null, null, null, null, null, null, null, null, null,
                        List.of("completion"), Instant.EPOCH), resolved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not advertise embedding capability");
        assertThatThrownBy(() -> RetrievalEmbeddingModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(installed)), "other:latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not installed");
        assertThatThrownBy(() -> RetrievalEmbeddingModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(new OllamaApi.Model(
                        MODEL.effectiveModel(), MODEL.effectiveModel(), Instant.EPOCH, 1L, "not-a-digest", null))),
                MODEL.requestedModel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full lowercase");
    }

    @Test
    void reservesTheNamedOutputBeforeGenerationAndRejectsModelIdentityDriftAfterward() {
        Path outputDirectory = temporaryDirectory.resolve("2026-08-28-r4-embedding");

        assertThat(RetrievalEmbeddingRunner.reserveOutputDirectory(outputDirectory)).isEqualTo(outputDirectory);
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.reserveOutputDirectory(outputDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        RetrievalEmbeddingRunner.requireUnchangedModelIdentity(MODEL, MODEL);
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.requireUnchangedModelIdentity(
                MODEL,
                new RetrievalEmbeddingModelIdentity(
                        MODEL.requestedModel(), MODEL.effectiveModel(), "b".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity changed during generation");
    }

    @Test
    void requiresTheSameCleanGitBaselineBeforeWritingEvidence() {
        EvidenceCodeBaseline baseline = new EvidenceCodeBaseline("a".repeat(40), false);

        RetrievalEmbeddingRunner.requireSameCleanBaseline(
                baseline, new EvidenceCodeBaseline("a".repeat(40), false));

        assertThatThrownBy(() -> RetrievalEmbeddingRunner.requireSameCleanBaseline(
                baseline, new EvidenceCodeBaseline("a".repeat(40), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clean worktree");
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.requireSameCleanBaseline(
                baseline, new EvidenceCodeBaseline("b".repeat(40), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Git baseline changed");
    }

    @Test
    void restrictsNewAndSavedEvidenceToTheDedicatedR4Root() {
        assertThat(RetrievalEmbeddingRunner.resolveNewOutputDirectory(
                "local/evidence/retrieval-embedding/2026-08-28-r4").getFileName().toString())
                .isEqualTo("2026-08-28-r4");
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.resolveNewOutputDirectory(
                "local/evidence/elsewhere/2026-08-28-r4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-embedding");
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.resolveNewOutputDirectory(
                "build/retrieval-embedding/2026-08-28-r4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/retrieval-embedding");
        assertThatThrownBy(() -> RetrievalEmbeddingRunner.resolveNewOutputDirectory(
                "local/evidence/retrieval-embedding/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    private static RetrievalEmbeddingAnalyzer analyzer(Inputs inputs) {
        return new RetrievalEmbeddingAnalyzer(inputs.corpus(), inputs.catalog());
    }

    private static RetrievalEmbeddingEvidence evidence(Inputs inputs) {
        return new RetrievalEmbeddingEvidence(
                JsonMapper.builder().findAndAddModules().build(), inputs.corpus(), inputs.catalog());
    }

    private static RetrievalEmbeddingResult execute(Inputs inputs, RetrievalEmbeddingClient client) {
        return new RetrievalEmbeddingExecutor(client).execute(
                inputs.corpus(), inputs.catalog(), RetrievalEmbeddingProtocol.settings(3), MODEL);
    }

    private static RetrievalEmbeddingResult copyWithRows(
            RetrievalEmbeddingResult result,
            List<RetrievalEmbeddingRow> rows
    ) {
        return new RetrievalEmbeddingResult(
                result.protocolVersion(), result.suite(), result.startedAt(), result.finishedAt(),
                result.executionStrategy(), result.pullModelStrategy(), result.runSettings(), result.modelIdentity(),
                result.corpusCatalogId(), result.corpusCatalogVersion(), result.corpusCatalogSha256(),
                result.queryCatalogId(), result.queryCatalogVersion(), result.queryCatalogSha256(),
                result.vectorDimension(), result.providerTotalDurationNanos(), result.providerLoadDurationNanos(),
                result.providerPromptEvalCount(), result.documentVectors(), result.queryVectors(), rows);
    }

    private static List<String> expectedInputs(Inputs inputs) {
        List<String> values = new ArrayList<>();
        inputs.corpus().documents().forEach(document -> values.add(document.content()));
        inputs.catalog().fixtures().forEach(fixture -> values.add(fixture.query()));
        return List.copyOf(values);
    }

    private static List<List<Float>> vectors() {
        List<List<Float>> values = new ArrayList<>();
        for (int index = 0; index < DIMENSION; index++) {
            values.add(oneHot(index, 2.0f));
        }
        for (int index = 0; index < DIMENSION; index++) {
            values.add(oneHot(index, 5.0f));
        }
        values.add(uniform(1.0f));
        values.add(uniform(1.0f));
        return List.copyOf(values);
    }

    private static List<Float> oneHot(int position, float value) {
        List<Float> vector = new ArrayList<>();
        for (int index = 0; index < DIMENSION; index++) {
            vector.add(index == position ? value : 0.0f);
        }
        return List.copyOf(vector);
    }

    private static List<Float> uniform(float value) {
        List<Float> vector = new ArrayList<>();
        for (int index = 0; index < DIMENSION; index++) {
            vector.add(value);
        }
        return List.copyOf(vector);
    }

    private static double l2Norm(List<Float> values) {
        return Math.sqrt(values.stream().mapToDouble(value -> value * value).sum());
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
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
                RetrievalEmbeddingTest.class.getClassLoader().getResource(resourceName), missingMessage);
        return Path.of(resource.toURI());
    }

    private record Inputs(RetrievalCorpus corpus, RetrievalQueryCatalog catalog) {}

    private static final class RecordingEmbeddingClient implements RetrievalEmbeddingClient {

        private final String effectiveModel;
        private final List<List<Float>> responseVectors;
        private final List<RetrievalEmbeddingModelIdentity> modelIdentities = new ArrayList<>();
        private final List<String> inputs = new ArrayList<>();
        private int calls;

        private RecordingEmbeddingClient(String effectiveModel, List<List<Float>> responseVectors) {
            this.effectiveModel = effectiveModel;
            this.responseVectors = List.copyOf(responseVectors);
        }

        @Override
        public EmbeddingResponse embed(RetrievalEmbeddingModelIdentity modelIdentity, List<String> requestInputs) {
            calls++;
            modelIdentities.add(modelIdentity);
            inputs.addAll(requestInputs);
            return new EmbeddingResponse(effectiveModel, responseVectors, 11L, 3L, requestInputs.size());
        }

        private int calls() {
            return calls;
        }

        private List<RetrievalEmbeddingModelIdentity> modelIdentities() {
            return List.copyOf(modelIdentities);
        }

        private List<String> inputs() {
            return List.copyOf(inputs);
        }
    }
}
