# Public retrieval corpus contract, version 1

The `corpus-v1/` resource defines the Phase 5 R0 corpus contract. It contains
only short, fictional, repository-authored Markdown documents. It contains no
personal data, imported articles, private Setaccio material, answers,
embeddings, model output, or relevance judgments.

The version-one corpus root is `retrieval/corpus-v1/` and must contain exactly:

- `catalog.json`;
- `catalog.sha256`, a single lowercase SHA-256 line for the exact bytes of
  `catalog.json`; and
- `documents/<documentId>.md` for every catalog entry, with no additional
  files or directories.

`catalog.json` has this exact schema. Unknown JSON fields and duplicate JSON
keys are rejected.

```json
{
  "schemaVersion": 1,
  "catalogId": "public-safe-retrieval-corpus",
  "catalogVersion": 1,
  "privacyReviewState": "PENDING_HUMAN_REVIEW | APPROVED_PUBLIC_SAFE",
  "documents": [
    {
      "documentId": "lowercase-hyphen-id",
      "relativePath": "documents/lowercase-hyphen-id.md",
      "contentSha256": "64 lowercase hexadecimal characters",
      "title": "Short public-safe title",
      "topic": "lowercase-hyphen-topic",
      "sourceType": "REPOSITORY_AUTHORED",
      "privacyReviewState": "PENDING_HUMAN_REVIEW | APPROVED_PUBLIC_SAFE"
    }
  ]
}
```

The catalog has 12--20 documents. Document IDs and relative paths are unique;
document SHA-256 values must also be unique. Every document path is exactly
`documents/<documentId>.md`, every document is a regular non-symbolic file,
and its exact UTF-8 bytes must match `contentSha256`. Markdown uses LF line
endings and ends with one LF. The catalog review state and every document
review state must agree.

A status of `PENDING_HUMAN_REVIEW` permits contract validation but prevents
formal retrieval use. A human must change the catalog and every document to
`APPROVED_PUBLIC_SAFE` after reviewing the text and its provenance. The project
owner completed that review for corpus version 1 on 2026-08-27. The approved
catalog and every document now carry `APPROVED_PUBLIC_SAFE`, and the catalog's
new pinned SHA-256 captures that reviewed state. Future content changes require
a new catalog version, updated document and catalog digests, and a new human
public-safety review; they must not alter saved evidence from an earlier
version.

## Query fixture contract, version 1

The `query-fixtures-v1/` resource defines the Phase 5 R1 retrieval-only
questions. It contains exactly `catalog.json` and `catalog.sha256`. The query
catalog is bound to the exact approved corpus catalog ID, version, and SHA-256;
changing the corpus makes the query catalog invalid until a new reviewed query
version is created.

Each fixture has this exact schema:

```json
{
  "caseId": "stable-lowercase-hyphen-id",
  "query": "One retrieval question?",
  "expectedSupportingDocumentIds": ["required-best-support-id"],
  "allowedSupportingDocumentIds": ["required-best-support-id"],
  "forbiddenDocumentIds": ["explicit-distractor-id"],
  "expectedNoMatch": false,
  "humanReviewState": "PENDING_HUMAN_REVIEW | CONFIRMED"
}
```

`expectedSupportingDocumentIds` identifies the document that must be retrieved
for a matching version-one case. `allowedSupportingDocumentIds` is the complete
set that may count as supporting and must include every expected ID.
`forbiddenDocumentIds` records explicit distractors and must be disjoint from
the allow-list; an unlisted document is not accepted as support merely because
it is not an explicit distractor. Every ID must link to the bound corpus.

Version 1 has twelve matching fixtures in corpus-document order, so every
document is the expected support exactly once. It also has two topical no-match
fixtures. A no-match fixture has empty expected and allowed lists and records
all twelve corpus documents as forbidden. The catalog contains no expected
answer text: it labels retrieval support only.

The query catalog and every fixture are initially
`PENDING_HUMAN_REVIEW`. This permits schema and linkage validation, while
`loadConfirmed` rejects formal use until a human confirms every query and its
expected, allowed, forbidden, and no-match judgment against the pinned corpus.
Confirmation changes every query review state to `CONFIRMED` and repins the
query catalog digest in one reviewable source change.

The project owner confirmed all fourteen version-one judgments on 2026-08-27,
bound to pre-confirmation catalog SHA-256
`e50e22266a1806ab94034f985248a51bb6428b27292feb2e99956af2606c8204`.
The catalog and every fixture now carry `CONFIRMED`; the resulting catalog
SHA-256 is
`ced4a31b13542a47d171a88879400fe649a0de985eeecd4ca58fea4feefb59b5`.
Any query or relevance-label change requires a new digest and new human review.

## Deterministic lexical baseline, version 1

R2 adds one plain-Java exact-term coverage baseline. It lowercases with
`Locale.ROOT`, extracts ASCII alphanumeric terms, removes the fixed
`english-structural-v1` stop-word set, keeps distinct query terms in first-use
order, and ignores query terms found in more than two corpus documents. Unseen
query terms remain in the score denominator.

A document qualifies after matching at least two retained query terms and at
least half of all retained query terms. Its exact score is
`matchedTermCount / retainedQueryTermCount`. Ranking uses descending matched
count, which is equivalent to descending coverage for a single query, then
ascending stable document ID. Empty retained queries return no documents.

Each result retains query ID and text; corpus ID, version, and digest; all
method parameters; retained query terms; and each hit's rank, document ID,
content digest, exact score fields, and matched terms. The confirmed v1 fixture
tests pin one expected hit for each of twelve supported queries and no hit for
both no-match queries.

R2 stops before embeddings, answer generation, `RelevancyEvaluator`, provider
calls, or formal retrieval evidence. R3 must retain these identities plus the
actual retrieved document text rather than describing ordinary fixture context
as retrieval.
