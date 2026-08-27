package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalQueryCatalogLoaderTest {

    private static final String PACKAGED_QUERY_CATALOG_SHA256 =
            "ced4a31b13542a47d171a88879400fe649a0de985eeecd4ca58fea4feefb59b5";
    private static final List<String> EXPECTED_CASE_IDS = List.of(
            "garden-compost-accepted-materials",
            "garden-shed-tool-inventory",
            "garden-rain-watering-schedule",
            "library-borrowing-renewal-condition",
            "library-study-room-equipment",
            "library-repair-workshop-exclusions",
            "trail-dune-shortcut-closure",
            "trail-bird-observation-rules",
            "trail-weather-closure-alerts",
            "workshop-bicycle-safety-inspection",
            "workshop-first-visit-membership",
            "workshop-route-map-limit",
            "no-match-library-home-delivery",
            "no-match-trail-overnight-camping");

    @TempDir
    Path temporaryDirectory;

    private final AtomicInteger copyNumber = new AtomicInteger();
    private final RetrievalCorpusLoader corpusLoader = new RetrievalCorpusLoader();
    private final RetrievalQueryCatalogLoader queryLoader = new RetrievalQueryCatalogLoader();

    @Test
    void loadsThePinnedOrderedCatalogAgainstTheExactApprovedCorpus() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        RetrievalQueryCatalog catalog = queryLoader.load(packagedQueryCatalogRoot(), corpus);

        assertThat(catalog.catalogId()).isEqualTo(RetrievalQueryCatalogLoader.CATALOG_ID);
        assertThat(catalog.catalogVersion()).isEqualTo(RetrievalQueryCatalogLoader.CATALOG_VERSION);
        assertThat(catalog.catalogSha256()).isEqualTo(PACKAGED_QUERY_CATALOG_SHA256);
        assertThat(catalog.corpusCatalogId()).isEqualTo(corpus.catalogId());
        assertThat(catalog.corpusCatalogVersion()).isEqualTo(corpus.catalogVersion());
        assertThat(catalog.corpusCatalogSha256()).isEqualTo(corpus.catalogSha256());
        assertThat(catalog.humanReviewState()).isEqualTo(RetrievalQueryReviewState.CONFIRMED);
        assertThat(catalog.fixtures()).extracting(RetrievalQueryFixture::caseId).containsExactlyElementsOf(EXPECTED_CASE_IDS);
        assertThat(catalog.fixtures()).allSatisfy(fixture -> {
            assertThat(fixture.query()).endsWith("?");
            assertThat(fixture.humanReviewState()).isEqualTo(RetrievalQueryReviewState.CONFIRMED);
        });

        List<String> expectedDocumentCoverage = catalog.fixtures().stream()
                .flatMap(fixture -> fixture.expectedSupportingDocumentIds().stream())
                .toList();
        assertThat(expectedDocumentCoverage)
                .containsExactlyElementsOf(corpus.documents().stream().map(RetrievalDocument::documentId).toList());
        assertThat(catalog.fixtures()).filteredOn(RetrievalQueryFixture::expectedNoMatch).hasSize(2);
    }

    @Test
    void requiresExplicitHumanConfirmationOfTheWholeExactCatalog() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();

        assertThat(queryLoader.loadConfirmed(packagedQueryCatalogRoot(), corpus).humanReviewState())
                .isEqualTo(RetrievalQueryReviewState.CONFIRMED);

        TestQueryCatalog pending = copyPackagedQueryCatalog();
        rewriteCatalog(pending, catalog -> catalog.replace("CONFIRMED", "PENDING_HUMAN_REVIEW"));
        assertThatThrownBy(() -> queryLoader.loadConfirmed(pending.root(), corpus))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not confirmed");

        rewriteCatalog(pending, catalog -> catalog.replace("PENDING_HUMAN_REVIEW", "CONFIRMED"));

        assertThat(queryLoader.loadConfirmed(pending.root(), corpus).humanReviewState())
                .isEqualTo(RetrievalQueryReviewState.CONFIRMED);
    }

    @Test
    void rejectsUnknownOrDuplicateDocumentLinkage() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        TestQueryCatalog unknownDocument = copyPackagedQueryCatalog();
        rewriteCatalog(unknownDocument, catalog -> catalog.replaceFirst(
                "\"garden-compost-basics\"", "\"unknown-document\""));
        assertThatThrownBy(() -> queryLoader.load(unknownDocument.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown corpus document");

        TestQueryCatalog duplicateDocument = copyPackagedQueryCatalog();
        rewriteCatalog(duplicateDocument, catalog -> catalog.replaceFirst(
                "\"garden-tool-shed\",\n        \"garden-water-schedule\"",
                "\"garden-tool-shed\",\n        \"garden-tool-shed\""));
        assertThatThrownBy(() -> queryLoader.load(duplicateDocument.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate document ID");
    }

    @Test
    void rejectsDuplicateCasesAndIncompleteBalancedCoverage() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        TestQueryCatalog duplicateCase = copyPackagedQueryCatalog();
        rewriteCatalog(duplicateCase, catalog -> catalog.replace(
                "\"garden-shed-tool-inventory\"", "\"garden-compost-accepted-materials\""));
        assertThatThrownBy(() -> queryLoader.load(duplicateCase.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate retrieval query caseId");

        TestQueryCatalog incompleteCoverage = copyPackagedQueryCatalog();
        rewriteCatalog(incompleteCoverage, catalog -> catalog.replace(
                """
                      "expectedSupportingDocumentIds": [
                        "garden-tool-shed"
                      ],
                      "allowedSupportingDocumentIds": [
                        "garden-tool-shed"
                      ],
                      "forbiddenDocumentIds": [
                        "garden-compost-basics",
                        "garden-water-schedule"
                      ],
                """,
                """
                      "expectedSupportingDocumentIds": [
                        "garden-compost-basics"
                      ],
                      "allowedSupportingDocumentIds": [
                        "garden-compost-basics"
                      ],
                      "forbiddenDocumentIds": [
                        "garden-tool-shed",
                        "garden-water-schedule"
                      ],
                """));
        assertThatThrownBy(() -> queryLoader.load(incompleteCoverage.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cover every corpus document");
    }

    @Test
    void rejectsNoMatchDriftAndMissingRequiredBoolean() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        TestQueryCatalog noMatchDrift = copyPackagedQueryCatalog();
        rewriteCatalog(noMatchDrift, catalog -> catalog.replaceFirst(
                "\"expectedNoMatch\": true", "\"expectedNoMatch\": false"));
        assertThatThrownBy(() -> queryLoader.load(noMatchDrift.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match query");

        TestQueryCatalog missingBoolean = copyPackagedQueryCatalog();
        rewriteCatalog(missingBoolean, catalog -> catalog.replaceFirst(
                "      \"expectedNoMatch\": false,\n", ""));
        assertThatThrownBy(() -> queryLoader.load(missingBoolean.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedNoMatch is required");
    }

    @Test
    void rejectsCatalogDigestOrBoundCorpusIdentityDrift() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        TestQueryCatalog digestDrift = copyPackagedQueryCatalog();
        Files.writeString(digestDrift.catalogPath(), Files.readString(digestDrift.catalogPath()).replace(
                "public-safe-retrieval-query-fixtures", "changed-query-catalog"));
        assertThatThrownBy(() -> queryLoader.load(digestDrift.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest does not match catalog.json");

        TestQueryCatalog corpusDrift = copyPackagedQueryCatalog();
        rewriteCatalog(corpusDrift, catalog -> catalog.replace(
                corpus.catalogSha256(), "0".repeat(64)));
        assertThatThrownBy(() -> queryLoader.load(corpusDrift.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact approved corpus");
    }

    @Test
    void rejectsUnknownDuplicateAndAnswerBearingJsonFields() throws Exception {
        RetrievalCorpus corpus = approvedCorpus();
        TestQueryCatalog unknownField = copyPackagedQueryCatalog();
        rewriteCatalog(unknownField, catalog -> catalog.replace(
                "      \"query\": \"Which items does Harbor Garden accept in its shared compost bins?\",",
                "      \"query\": \"Which items does Harbor Garden accept in its shared compost bins?\",\n"
                        + "      \"expectedAnswer\": \"Not allowed in a retrieval-only fixture\","));
        assertThatThrownBy(() -> queryLoader.load(unknownField.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse retrieval query catalog");

        TestQueryCatalog duplicateField = copyPackagedQueryCatalog();
        rewriteCatalog(duplicateField, catalog -> catalog.replace(
                "  \"catalogVersion\": 1,",
                "  \"catalogVersion\": 1,\n  \"catalogVersion\": 1,"));
        assertThatThrownBy(() -> queryLoader.load(duplicateField.root(), corpus))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse retrieval query catalog");
    }

    private RetrievalCorpus approvedCorpus() throws URISyntaxException {
        return corpusLoader.loadApproved(packagedCorpusRoot());
    }

    private static Path packagedCorpusRoot() throws URISyntaxException {
        return resourcePath("retrieval/corpus-v1", "Packaged retrieval corpus resource is missing");
    }

    private static Path packagedQueryCatalogRoot() throws URISyntaxException {
        return resourcePath("retrieval/query-fixtures-v1", "Packaged retrieval query catalog is missing");
    }

    private static Path resourcePath(String resourceName, String missingMessage) throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                RetrievalQueryCatalogLoaderTest.class.getClassLoader().getResource(resourceName),
                missingMessage);
        return Path.of(resource.toURI());
    }

    private TestQueryCatalog copyPackagedQueryCatalog() throws IOException, URISyntaxException {
        Path root = temporaryDirectory.resolve("query-catalog-" + copyNumber.incrementAndGet());
        Files.createDirectories(root);
        Path source = packagedQueryCatalogRoot();
        Path catalogPath = root.resolve(RetrievalQueryCatalogLoader.CATALOG_FILENAME);
        Files.copy(source.resolve(RetrievalQueryCatalogLoader.CATALOG_FILENAME), catalogPath);
        Files.copy(
                source.resolve(RetrievalQueryCatalogLoader.CATALOG_SHA256_FILENAME),
                root.resolve(RetrievalQueryCatalogLoader.CATALOG_SHA256_FILENAME));
        return new TestQueryCatalog(root, catalogPath);
    }

    private static void rewriteCatalog(TestQueryCatalog catalog, UnaryOperator<String> change) throws IOException {
        Files.writeString(catalog.catalogPath(), change.apply(Files.readString(catalog.catalogPath())));
        Files.writeString(
                catalog.root().resolve(RetrievalQueryCatalogLoader.CATALOG_SHA256_FILENAME),
                EvidenceIntegrity.sha256(Files.readAllBytes(catalog.catalogPath())) + "\n");
    }

    private record TestQueryCatalog(Path root, Path catalogPath) {}
}
