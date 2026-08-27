package com.setaccio.lab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DeterministicLexicalRetrieverTest {

    private static final Map<String, Score> CONFIRMED_FIXTURE_SCORES = Map.ofEntries(
            Map.entry("garden-compost-accepted-materials", new Score(2, 4)),
            Map.entry("garden-shed-tool-inventory", new Score(2, 3)),
            Map.entry("garden-rain-watering-schedule", new Score(4, 5)),
            Map.entry("library-borrowing-renewal-condition", new Score(2, 3)),
            Map.entry("library-study-room-equipment", new Score(3, 5)),
            Map.entry("library-repair-workshop-exclusions", new Score(2, 2)),
            Map.entry("trail-dune-shortcut-closure", new Score(3, 3)),
            Map.entry("trail-bird-observation-rules", new Score(7, 10)),
            Map.entry("trail-weather-closure-alerts", new Score(3, 3)),
            Map.entry("workshop-bicycle-safety-inspection", new Score(3, 3)),
            Map.entry("workshop-first-visit-membership", new Score(3, 4)),
            Map.entry("workshop-route-map-limit", new Score(3, 5)));

    private final DeterministicLexicalRetriever retriever = new DeterministicLexicalRetriever();

    @Test
    void ranksHandCalculatedCoverageAndRecordsExactIdentities() {
        RetrievalCorpus corpus = corpus(
                document("alpha-document", "alpha beta gamma\n"),
                document("beta-document", "alpha beta\n"),
                document("other-document", "delta epsilon\n"));

        RetrievalLexicalResult result = retriever.retrieve(
                "hand-calculated-ranking",
                "alpha beta gamma theta",
                corpus);

        assertThat(result.queryId()).isEqualTo("hand-calculated-ranking");
        assertThat(result.query()).isEqualTo("alpha beta gamma theta");
        assertThat(result.corpusCatalogId()).isEqualTo(corpus.catalogId());
        assertThat(result.corpusCatalogVersion()).isEqualTo(corpus.catalogVersion());
        assertThat(result.corpusCatalogSha256()).isEqualTo(corpus.catalogSha256());
        assertThat(result.retainedQueryTerms()).containsExactly("alpha", "beta", "gamma", "theta");
        assertThat(result.hits()).extracting(RetrievalLexicalHit::documentId)
                .containsExactly("alpha-document", "beta-document");
        assertThat(result.hits()).extracting(RetrievalLexicalHit::rank).containsExactly(1, 2);

        RetrievalLexicalHit first = result.hits().getFirst();
        assertThat(first.contentSha256()).isEqualTo(corpus.documents().getFirst().contentSha256());
        assertThat(first.matchedTermCount()).isEqualTo(3);
        assertThat(first.retainedQueryTermCount()).isEqualTo(4);
        assertThat(first.matchedTerms()).containsExactly("alpha", "beta", "gamma");
        assertThat(first.score()).isEqualTo(0.75);

        RetrievalLexicalHit second = result.hits().get(1);
        assertThat(second.matchedTermCount()).isEqualTo(2);
        assertThat(second.retainedQueryTermCount()).isEqualTo(4);
        assertThat(second.score()).isEqualTo(0.5);
        RetrievalLexicalParameters parameters = result.parameters();
        assertThat(parameters.methodId()).isEqualTo("exact-distinct-query-coverage");
        assertThat(parameters.methodVersion()).isEqualTo(1);
        assertThat(parameters.tokenizerId()).isEqualTo("ascii-lower-alphanumeric-v1");
        assertThat(parameters.lowercaseLocale()).isEqualTo("ROOT");
        assertThat(parameters.tokenPattern()).isEqualTo("[a-z0-9]+");
        assertThat(parameters.stopWordsId()).isEqualTo("english-structural-v1");
        assertThat(parameters.stopWords()).containsExactly(
                "a", "an", "and", "are", "as", "at", "be", "because", "been", "before", "being",
                "both", "but", "by", "can", "did", "do", "does", "during", "each", "for", "from",
                "had", "has", "have", "how", "in", "into", "is", "it", "its", "may", "no", "not",
                "of", "on", "one", "only", "or", "other", "s", "should", "so", "than", "that", "the",
                "their", "them", "then", "there", "these", "they", "this", "those", "through", "to",
                "under", "up", "was", "were", "what", "when", "where", "which", "while", "who", "why",
                "will", "with", "without");
        assertThat(parameters.maximumDocumentFrequency()).isEqualTo(2);
        assertThat(parameters.minimumMatchedTerms()).isEqualTo(2);
        assertThat(parameters.minimumCoverageNumerator()).isEqualTo(1);
        assertThat(parameters.minimumCoverageDenominator()).isEqualTo(2);
        assertThat(parameters.tieBreak()).isEqualTo("document-id-ascending");
    }

    @Test
    void breaksEqualScoreTiesByStableDocumentId() {
        RetrievalCorpus corpus = corpus(
                document("z-document", "red blue\n"),
                document("a-document", "red blue\n"),
                document("other-document", "green amber\n"));

        RetrievalLexicalResult result = retriever.retrieve("tie", "red blue violet", corpus);

        assertThat(result.hits()).extracting(RetrievalLexicalHit::documentId)
                .containsExactly("a-document", "z-document");
        assertThat(result.hits()).allSatisfy(hit -> {
            assertThat(hit.matchedTermCount()).isEqualTo(2);
            assertThat(hit.retainedQueryTermCount()).isEqualTo(3);
        });
    }

    @Test
    void appliesLockedTokenStopWordFrequencyAndThresholdRules() {
        RetrievalCorpus corpus = corpus(
                document("target-document", "common needle signal\n"),
                document("common-document-b", "common noise\n"),
                document("common-document-c", "common static\n"));

        RetrievalLexicalResult qualified = retriever.retrieve(
                "token-rules",
                "THE common, needle signal extra",
                corpus);
        assertThat(qualified.retainedQueryTerms()).containsExactly("needle", "signal", "extra");
        assertThat(qualified.hits()).extracting(RetrievalLexicalHit::documentId)
                .containsExactly("target-document");
        assertThat(qualified.hits().getFirst().matchedTerms()).containsExactly("needle", "signal");

        assertThat(retriever.retrieve("one-term", "needle absent", corpus).hits()).isEmpty();
        assertThat(retriever.retrieve(
                "below-half",
                "needle signal third fourth fifth",
                corpus).hits()).isEmpty();
    }

    @Test
    void returnsNoHitsForEmptyStructuralOrUnmatchedQueries() {
        RetrievalCorpus corpus = corpus(document("sample-document", "alpha beta\n"));

        assertThat(retriever.retrieve("empty", "", corpus).hits()).isEmpty();
        assertThat(retriever.retrieve("structural", "the and what", corpus).hits()).isEmpty();
        assertThat(retriever.retrieve("unmatched", "missing absent", corpus).hits()).isEmpty();
    }

    @Test
    void retrievesOnlyTheConfirmedSupportAcrossTheLockedFixtureCatalog() throws Exception {
        RetrievalCorpus corpus = new RetrievalCorpusLoader().loadApproved(packagedCorpusRoot());
        RetrievalQueryCatalog queryCatalog = new RetrievalQueryCatalogLoader()
                .loadConfirmed(packagedQueryCatalogRoot(), corpus);

        for (RetrievalQueryFixture fixture : queryCatalog.fixtures()) {
            RetrievalLexicalResult result = retriever.retrieve(corpus, fixture);
            assertThat(result.queryId()).isEqualTo(fixture.caseId());
            assertThat(result.query()).isEqualTo(fixture.query());
            assertThat(result.corpusCatalogSha256()).isEqualTo(corpus.catalogSha256());

            if (fixture.expectedNoMatch()) {
                assertThat(result.hits()).as(fixture.caseId()).isEmpty();
            } else {
                assertThat(result.hits()).extracting(RetrievalLexicalHit::documentId)
                        .as(fixture.caseId())
                        .containsExactlyElementsOf(fixture.expectedSupportingDocumentIds());
                assertThat(result.hits()).extracting(RetrievalLexicalHit::documentId)
                        .doesNotContainAnyElementsOf(fixture.forbiddenDocumentIds());
                Score expectedScore = CONFIRMED_FIXTURE_SCORES.get(fixture.caseId());
                assertThat(expectedScore).as(fixture.caseId()).isNotNull();
                assertThat(result.hits().getFirst().matchedTermCount()).isEqualTo(expectedScore.numerator());
                assertThat(result.hits().getFirst().retainedQueryTermCount()).isEqualTo(expectedScore.denominator());
            }
        }
    }

    @Test
    void isRepeatableAndReturnsImmutableCollections() {
        RetrievalCorpus corpus = corpus(
                document("alpha-document", "alpha beta gamma\n"),
                document("beta-document", "alpha beta\n"));
        RetrievalLexicalResult first = retriever.retrieve("repeatable", "alpha beta gamma theta", corpus);

        for (int repetition = 0; repetition < 100; repetition++) {
            assertThat(retriever.retrieve("repeatable", "alpha beta gamma theta", corpus)).isEqualTo(first);
        }
        assertThatThrownBy(() -> first.retainedQueryTerms().add("changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.hits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.hits().getFirst().matchedTerms().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsUnconfirmedFixturesAndUnapprovedCorpora() {
        RetrievalCorpus approvedCorpus = corpus(document("sample-document", "alpha beta\n"));
        RetrievalQueryFixture pendingFixture = new RetrievalQueryFixture(
                "pending-fixture",
                "alpha beta?",
                List.of("sample-document"),
                List.of("sample-document"),
                List.of(),
                false,
                RetrievalQueryReviewState.PENDING_HUMAN_REVIEW);
        assertThatThrownBy(() -> retriever.retrieve(approvedCorpus, pendingFixture))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("human-confirmed");

        RetrievalCorpus pendingCorpus = new RetrievalCorpus(
                approvedCorpus.catalogId(),
                approvedCorpus.catalogVersion(),
                approvedCorpus.catalogSha256(),
                RetrievalPrivacyReviewState.PENDING_HUMAN_REVIEW,
                approvedCorpus.documents().stream()
                        .map(document -> new RetrievalDocument(
                                document.documentId(),
                                document.relativePath(),
                                document.contentSha256(),
                                document.title(),
                                document.topic(),
                                document.sourceType(),
                                RetrievalPrivacyReviewState.PENDING_HUMAN_REVIEW,
                                document.content()))
                        .toList());
        assertThatThrownBy(() -> retriever.retrieve("pending-corpus", "alpha beta", pendingCorpus))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");
    }

    private static RetrievalCorpus corpus(RetrievalDocument... documents) {
        return new RetrievalCorpus(
                "test-retrieval-corpus",
                1,
                "a".repeat(64),
                RetrievalPrivacyReviewState.APPROVED_PUBLIC_SAFE,
                List.of(documents));
    }

    private static RetrievalDocument document(String documentId, String content) {
        return new RetrievalDocument(
                documentId,
                "documents/" + documentId + ".md",
                EvidenceIntegrity.sha256(content.getBytes(StandardCharsets.UTF_8)),
                documentId,
                "test-topic",
                RetrievalDocumentSource.REPOSITORY_AUTHORED,
                RetrievalPrivacyReviewState.APPROVED_PUBLIC_SAFE,
                content);
    }

    private static Path packagedCorpusRoot() throws URISyntaxException {
        return resourcePath("retrieval/corpus-v1", "Packaged retrieval corpus resource is missing");
    }

    private static Path packagedQueryCatalogRoot() throws URISyntaxException {
        return resourcePath("retrieval/query-fixtures-v1", "Packaged retrieval query catalog is missing");
    }

    private static Path resourcePath(String resourceName, String missingMessage) throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                DeterministicLexicalRetrieverTest.class.getClassLoader().getResource(resourceName),
                missingMessage);
        return Path.of(resource.toURI());
    }

    private record Score(int numerator, int denominator) {}
}
