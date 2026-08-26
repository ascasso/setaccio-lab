# Phase 5 R0 retrieval contract design

## Implementation

On 2026-08-26, the project owner explicitly started Phase 5 R0. This change
adds the isolated `retrieval` and `retrievalTest` source sets plus the
provider-free `retrievalFixtureTest` verification task.

The first public corpus is twelve short fictional Markdown documents in four
overlapping topic groups: community garden, neighborhood library, coastal
trail, and bicycle workshop. Every document is repository-authored and has a
stable lowercase-hyphen document ID, a matching stable filename, a title and
topic, a source classification, an explicit privacy-review state, and a
SHA-256 identity for its exact UTF-8 bytes. `catalog.json` has its own pinned
SHA-256 in `catalog.sha256`.

The loader rejects unsafe or symbolic paths, symbolic links anywhere in the
corpus, unexpected files or directories, duplicate IDs/paths/content digests,
catalog drift, document-content drift, non-repository-authored sources, and
unknown or duplicate catalog JSON fields. It preserves the exact validated
document text and its identity for a later retrieval-evidence slice.

## Public-safety gate and boundary

The corpus is deliberately marked `PENDING_HUMAN_REVIEW`. This records that
the content is staged for owner review; it is not an agent-created human
privacy approval. `loadApproved` rejects the corpus until a human changes both
the catalog and every document state to `APPROVED_PUBLIC_SAFE` in one reviewed
source change.

No query fixture, retrieval ranking, embedding, vector store, model call,
answer generation, evaluator, remote request, credential, Docker dependency,
or formal retrieval evidence was added. R1 remains blocked until the project
owner completes the public-safety sign-off; ordinary fixture context is still
not described as retrieval.

## Verification

`./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon`
passed. The provider-free suite covers the pinned staged corpus, approval gate,
unsafe paths, duplicate IDs, non-repository provenance, document and catalog
digest drift, unexpected files, symbolic links, unknown fields, and duplicate
JSON keys. No Ollama service or other provider was contacted.
