package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetrievalCorpusLoaderTest {

    private static final String PACKAGED_CATALOG_SHA256 =
            "6337149171869af7cfee23302234ffc0c33366e877a168108974f5c10ac3c6bd";

    @TempDir
    Path temporaryDirectory;

    private final RetrievalCorpusLoader loader = new RetrievalCorpusLoader();

    @Test
    void loadsThePinnedPublicSafeCorpusWithoutStartingSpringOrContactingAProvider() throws Exception {
        RetrievalCorpus corpus = loader.load(packagedCorpusRoot());

        assertThat(corpus.catalogId()).isEqualTo(RetrievalCorpusLoader.CATALOG_ID);
        assertThat(corpus.catalogVersion()).isEqualTo(RetrievalCorpusLoader.CATALOG_VERSION);
        assertThat(corpus.catalogSha256()).isEqualTo(PACKAGED_CATALOG_SHA256);
        assertThat(corpus.privacyReviewState()).isEqualTo(RetrievalPrivacyReviewState.PENDING_HUMAN_REVIEW);
        assertThat(corpus.documents()).extracting(RetrievalDocument::documentId).containsExactly(
                "garden-compost-basics",
                "garden-tool-shed",
                "garden-water-schedule",
                "library-borrowing-rules",
                "library-study-room",
                "library-workshop-calendar",
                "trail-access-notice",
                "trail-bird-observation",
                "trail-weather-guidance",
                "workshop-bike-check",
                "workshop-membership",
                "workshop-route-map");
        assertThat(corpus.documents()).allSatisfy(document -> {
            assertThat(document.sourceType()).isEqualTo(RetrievalDocumentSource.REPOSITORY_AUTHORED);
            assertThat(document.privacyReviewState()).isEqualTo(RetrievalPrivacyReviewState.PENDING_HUMAN_REVIEW);
            assertThat(document.content()).endsWith("\n");
        });
    }

    @Test
    void requiresAnExplicitHumanPublicSafetyApprovalBeforeFormalCorpusUse() throws Exception {
        Path pendingCorpus = packagedCorpusRoot();

        assertThatThrownBy(() -> loader.loadApproved(pendingCorpus))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");

        TestCorpus approvedCorpus = writeValidCorpus();
        rewriteCatalog(approvedCorpus, catalog -> catalog.replace(
                "PENDING_HUMAN_REVIEW", "APPROVED_PUBLIC_SAFE"));

        assertThat(loader.loadApproved(approvedCorpus.root()).privacyReviewState())
                .isEqualTo(RetrievalPrivacyReviewState.APPROVED_PUBLIC_SAFE);
    }

    @Test
    void rejectsAPathThatDoesNotUseTheStableDocumentIdFilenameRule() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        rewriteCatalog(corpus, catalog -> catalog.replace(
                "documents/sample-01.md", "../private.md"));

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relativePath");
    }

    @Test
    void rejectsDuplicateDocumentIdsEvenWhenTheCatalogDigestIsRefreshed() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        rewriteCatalog(corpus, catalog -> catalog.replaceFirst(
                "\"documentId\": \"sample-02\"", "\"documentId\": \"sample-01\""));

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate retrieval documentId");
    }

    @Test
    void rejectsNonRepositoryAuthoredMaterialEvenWhenItsDigestIsValid() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        rewriteCatalog(corpus, catalog -> catalog.replaceFirst(
                "\"sourceType\": \"REPOSITORY_AUTHORED\"", "\"sourceType\": \"PRIVATE\""));

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository-authored");
    }

    @Test
    void rejectsDocumentContentDigestDrift() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        Files.writeString(corpus.root().resolve("documents/sample-01.md"), "# Changed\n\nChanged text.\n");

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256 does not match content");
    }

    @Test
    void rejectsCatalogDigestDriftBeforeParsingTheChangedContract() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        Files.writeString(corpus.catalogPath(), Files.readString(corpus.catalogPath()).replace(
                "public-safe-retrieval-corpus", "changed-catalog"));

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest does not match catalog.json");
    }

    @Test
    void rejectsUnexpectedCorpusFiles() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        Files.writeString(corpus.root().resolve("documents/unlisted.md"), "# Unlisted\n\nUnexpected.\n");

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected file");
    }

    @Test
    void rejectsSymbolicLinksBeforeLoadingAReferencedDocument() throws Exception {
        TestCorpus corpus = writeValidCorpus();
        Path document = corpus.root().resolve("documents/sample-01.md");
        Path external = temporaryDirectory.resolve("external.md");
        Files.writeString(external, "# External\n\nExternal text.\n");
        Files.delete(document);
        Files.createSymbolicLink(document, external);

        assertThatThrownBy(() -> loader.load(corpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic link");
    }

    @Test
    void rejectsUnknownAndDuplicateCatalogFields() throws Exception {
        TestCorpus unknownFieldCorpus = writeValidCorpus();
        rewriteCatalog(unknownFieldCorpus, catalog -> catalog.replace(
                "  \"documents\": [", "  \"unexpected\": true,\n  \"documents\": ["));
        assertThatThrownBy(() -> loader.load(unknownFieldCorpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse retrieval catalog");

        TestCorpus duplicateFieldCorpus = writeValidCorpus();
        rewriteCatalog(duplicateFieldCorpus, catalog -> catalog.replace(
                "  \"catalogVersion\": 1,", "  \"catalogVersion\": 1,\n  \"catalogVersion\": 1,"));
        assertThatThrownBy(() -> loader.load(duplicateFieldCorpus.root()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse retrieval catalog");
    }

    private static Path packagedCorpusRoot() throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                RetrievalCorpusLoaderTest.class.getClassLoader().getResource("retrieval/corpus-v1"),
                "Packaged retrieval corpus resource is missing");
        return Path.of(resource.toURI());
    }

    private TestCorpus writeValidCorpus() throws IOException {
        Path root = temporaryDirectory.resolve("corpus-v1");
        Path documents = root.resolve("documents");
        Files.createDirectories(documents);

        List<String> entries = new ArrayList<>();
        for (int number = 1; number <= RetrievalCorpusLoader.MINIMUM_DOCUMENT_COUNT; number++) {
            String documentId = "sample-%02d".formatted(number);
            String content = "# Sample " + number + "\n\nRepository-authored public fixture " + number + ".\n";
            Path document = documents.resolve(documentId + ".md");
            Files.writeString(document, content);
            entries.add("""
                    {
                      "documentId": "%s",
                      "relativePath": "documents/%s.md",
                      "contentSha256": "%s",
                      "title": "Sample document %d",
                      "topic": "sample-topic",
                      "sourceType": "REPOSITORY_AUTHORED",
                      "privacyReviewState": "PENDING_HUMAN_REVIEW"
                    }""".formatted(
                    documentId,
                    documentId,
                    EvidenceIntegrity.sha256(Files.readAllBytes(document)),
                    number));
        }
        String catalog = """
                {
                  "schemaVersion": 1,
                  "catalogId": "public-safe-retrieval-corpus",
                  "catalogVersion": 1,
                  "privacyReviewState": "PENDING_HUMAN_REVIEW",
                  "documents": [
                %s
                  ]
                }
                """.formatted(String.join(",\n", entries));
        Path catalogPath = root.resolve(RetrievalCorpusLoader.CATALOG_FILENAME);
        writeCatalog(catalogPath, catalog);
        return new TestCorpus(root, catalogPath);
    }

    private static void rewriteCatalog(TestCorpus corpus, UnaryOperator<String> change) throws IOException {
        writeCatalog(corpus.catalogPath(), change.apply(Files.readString(corpus.catalogPath())));
    }

    private static void writeCatalog(Path catalogPath, String catalog) throws IOException {
        Files.writeString(catalogPath, catalog);
        Path digestPath = catalogPath.resolveSibling(RetrievalCorpusLoader.CATALOG_SHA256_FILENAME);
        Files.writeString(digestPath, EvidenceIntegrity.sha256(Files.readAllBytes(catalogPath)) + "\n");
    }

    private record TestCorpus(Path root, Path catalogPath) {}
}
