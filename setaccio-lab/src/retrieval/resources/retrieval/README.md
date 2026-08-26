# Public retrieval corpus contract, version 1

This resource defines the Phase 5 R0 corpus contract. It contains only
short, fictional, repository-authored Markdown documents. It contains no
personal data, imported articles, private Setaccio material, query fixtures,
answers, embeddings, model output, or relevance judgments.

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
`APPROVED_PUBLIC_SAFE` after reviewing the text and its provenance. That change
also changes the pinned catalog digest, so it is a versioned, reviewable source
change. Future content changes require a new catalog version, updated document
and catalog digests, and a new human public-safety review; they must not alter
saved evidence from an earlier version.

The R0 contract deliberately stops before query fixtures, lexical ranking,
embeddings, answer generation, or `RelevancyEvaluator`. Later slices must use
the catalog and document SHA-256 identities to retain actual retrieved
documents, rather than describing ordinary fixture context as retrieval.
