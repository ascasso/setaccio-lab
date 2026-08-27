# Phase 5 R4 embedding retrieval boundary

R4 implements an explicit local embedding-retrieval boundary and its
provider-free evidence contract. It does not create an R4 formal vector run in
this change: no locally installed model was verified as embedding-capable after
the Ollama upgrade, so no model was pulled, substituted, or invoked for formal
evidence.

## Implementation

The opt-in `retrievalEmbedding` task requires a clean full Git baseline, a
loopback-only Ollama URL, one explicit already-installed model tag, one explicit
top K, and a fresh dated output path below ignored
`build/retrieval-embedding/`. Its preflight validates the approved v1 corpus
and confirmed v1 query catalog, resolves the requested/effective installed tag
and its full digest, and requires the model's Ollama `show` response to declare
the `embedding` capability before allocating any evidence directory.

It makes exactly one direct Spring AI `/api/embed` request containing the
twelve corpus documents followed by the fourteen confirmed queries. It disables
input truncation, applies no model options, has a two-minute timeout, does not
start Spring, pulls no model, retries no request, and rejects a response whose
effective model differs from preflight. The raw ignored result retains the
provider and endpoint category, model identity, corpus/query identities,
provider metadata when available, all normalized vectors, and deterministic
top-K cosine ranks. The shared-v1 manifest and deterministic summary are
verified and reanalyzed offline; only the summary may be regenerated.

The retrieval settings are `whole-document-v1`, `unit-l2-v1`, and
`cosine-descending-document-id`; ties resolve by stable document ID. R4 sets no
retrieval-support threshold and intentionally does not score no-match behavior
or the human-confirmed relevance labels.

## Local eligibility diagnostic, not formal evidence

The upgraded local Ollama CLI reported version `0.33.1`. A disposable direct
`/api/embed` request against the selected small chat candidate returned the
runner's embedding-disabled response. Its `show` metadata did not declare the
required `embedding` capability. An isolated `ollama serve --embeddings` start
attempt also failed because this CLI did not accept that flag. These are only
local eligibility diagnostics: they are not vector evidence, retrieval results,
or a model comparison. They did not create a run directory, alter the existing
Ollama process, pull a model, or retry the candidate.

## Verification

The dedicated provider-free suite passed after adding fake and recorded-vector
coverage for one batch and input order, unit normalization, deterministic rank
recomputation, response-model identity drift, vector count/dimension failures,
provider failure propagation, embedding-capability preflight, manifest/layout
integrity, and summary-only repair:

```text
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
git diff --check
```

## Deferred formal run and next gate

The formal R4 run remains deferred until an already-installed local model
advertises `embedding` capability and the loopback `/api/embed` boundary accepts
it. At that point, select and record one explicit tag, preflight full digest,
and top K from a clean commit; then run `retrievalEmbedding` once into a new
ignored dated directory and perform offline verify/reanalyze. This gate does
not authorize a model pull, a remote provider, answer generation, a relevance
evaluator, or R5 work.
