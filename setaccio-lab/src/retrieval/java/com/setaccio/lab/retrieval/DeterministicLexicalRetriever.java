package com.setaccio.lab.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version-one exact-term coverage baseline for the public retrieval corpus.
 *
 * <p>The implementation is plain Java and deterministic. It makes no network,
 * model, embedding, vector-store, or evaluator call.</p>
 */
public final class DeterministicLexicalRetriever {

    public static final String METHOD_ID = "exact-distinct-query-coverage";
    public static final int METHOD_VERSION = 1;
    public static final String TOKENIZER_ID = "ascii-lower-alphanumeric-v1";
    public static final String LOWERCASE_LOCALE = "ROOT";
    public static final String TOKEN_PATTERN = "[a-z0-9]+";
    public static final String STOP_WORDS_ID = "english-structural-v1";
    public static final int MAXIMUM_DOCUMENT_FREQUENCY = 2;
    public static final int MINIMUM_MATCHED_TERMS = 2;
    public static final int MINIMUM_COVERAGE_NUMERATOR = 1;
    public static final int MINIMUM_COVERAGE_DENOMINATOR = 2;
    public static final String TIE_BREAK = "document-id-ascending";

    private static final Pattern TOKEN = Pattern.compile(TOKEN_PATTERN);
    private static final List<String> STOP_WORDS_IN_ORDER = List.of(
            "a", "an", "and", "are", "as", "at", "be", "because", "been", "before", "being",
            "both", "but", "by", "can", "did", "do", "does", "during", "each", "for", "from",
            "had", "has", "have", "how", "in", "into", "is", "it", "its", "may", "no", "not",
            "of", "on", "one", "only", "or", "other", "s", "should", "so", "than", "that", "the",
            "their", "them", "then", "there", "these", "they", "this", "those", "through", "to",
            "under", "up", "was", "were", "what", "when", "where", "which", "while", "who", "why",
            "will", "with", "without");
    private static final Set<String> STOP_WORDS = Set.copyOf(STOP_WORDS_IN_ORDER);
    private static final RetrievalLexicalParameters PARAMETERS = new RetrievalLexicalParameters(
            METHOD_ID,
            METHOD_VERSION,
            TOKENIZER_ID,
            LOWERCASE_LOCALE,
            TOKEN_PATTERN,
            STOP_WORDS_ID,
            STOP_WORDS_IN_ORDER,
            MAXIMUM_DOCUMENT_FREQUENCY,
            MINIMUM_MATCHED_TERMS,
            MINIMUM_COVERAGE_NUMERATOR,
            MINIMUM_COVERAGE_DENOMINATOR,
            TIE_BREAK);

    /**
     * Returns the complete immutable version-one lexical method contract.
     *
     * @return locked method parameters recorded in retrieval-only evidence
     */
    public static RetrievalLexicalParameters parameters() {
        return PARAMETERS;
    }

    /**
     * Retrieves for one confirmed fixture.
     *
     * @param corpus exact approved corpus
     * @param fixture human-confirmed query fixture
     * @return deterministic ranking and identities
     */
    public RetrievalLexicalResult retrieve(RetrievalCorpus corpus, RetrievalQueryFixture fixture) {
        if (fixture == null || fixture.humanReviewState() != RetrievalQueryReviewState.CONFIRMED) {
            throw new IllegalArgumentException("Retrieval lexical fixture must be human-confirmed");
        }
        return retrieve(fixture.caseId(), fixture.query(), corpus);
    }

    /**
     * Retrieves for an explicit query identity. This overload supports
     * provider-free diagnostics and algorithm contract tests, including the
     * required empty-query behavior.
     *
     * @param queryId stable query identity
     * @param query query text; blank text deterministically returns no hits
     * @param corpus exact approved corpus
     * @return deterministic ranking and identities
     */
    public RetrievalLexicalResult retrieve(String queryId, String query, RetrievalCorpus corpus) {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("Retrieval lexical queryId must not be blank");
        }
        if (query == null) {
            throw new IllegalArgumentException("Retrieval lexical query must not be null");
        }
        if (corpus == null) {
            throw new IllegalArgumentException("Retrieval lexical corpus must not be null");
        }
        RetrievalCorpus approvedCorpus = corpus.requireApprovedPublicSafe();

        Map<RetrievalDocument, Set<String>> documentTerms = new LinkedHashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (RetrievalDocument document : approvedCorpus.documents()) {
            Set<String> terms = distinctTerms(document.content());
            documentTerms.put(document, terms);
            terms.forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }

        List<String> retainedQueryTerms = distinctTerms(query).stream()
                .filter(term -> documentFrequency.getOrDefault(term, 0) <= MAXIMUM_DOCUMENT_FREQUENCY)
                .toList();
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<RetrievalDocument, Set<String>> entry : documentTerms.entrySet()) {
            List<String> matchedTerms = retainedQueryTerms.stream()
                    .filter(entry.getValue()::contains)
                    .toList();
            if (qualifies(matchedTerms.size(), retainedQueryTerms.size())) {
                candidates.add(new Candidate(entry.getKey(), matchedTerms));
            }
        }
        candidates.sort(Comparator
                .comparingInt((Candidate candidate) -> candidate.matchedTerms().size())
                .reversed()
                .thenComparing(candidate -> candidate.document().documentId()));

        List<RetrievalLexicalHit> hits = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            hits.add(new RetrievalLexicalHit(
                    index + 1,
                    candidate.document().documentId(),
                    candidate.document().contentSha256(),
                    candidate.matchedTerms().size(),
                    retainedQueryTerms.size(),
                    candidate.matchedTerms()));
        }
        return new RetrievalLexicalResult(
                queryId,
                query,
                approvedCorpus.catalogId(),
                approvedCorpus.catalogVersion(),
                approvedCorpus.catalogSha256(),
                PARAMETERS,
                retainedQueryTerms,
                hits);
    }

    private static boolean qualifies(int matchedTermCount, int retainedQueryTermCount) {
        return retainedQueryTermCount > 0
                && matchedTermCount >= MINIMUM_MATCHED_TERMS
                && (long) matchedTermCount * MINIMUM_COVERAGE_DENOMINATOR
                >= (long) retainedQueryTermCount * MINIMUM_COVERAGE_NUMERATOR;
    }

    private static Set<String> distinctTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOP_WORDS.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private record Candidate(RetrievalDocument document, List<String> matchedTerms) {}
}
