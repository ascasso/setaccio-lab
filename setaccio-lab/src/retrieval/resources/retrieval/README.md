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

R0 and R1 stop before lexical ranking, embeddings, answer generation,
`RelevancyEvaluator`, provider calls, or formal retrieval evidence. Later
slices must retain the catalog, query, and document identities plus the actual
retrieved document text, rather than describing ordinary fixture context as
retrieval.
