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

After those checks, it atomically reserves the specified fresh output directory
before the single embedding request. It then re-resolves the requested model
from Ollama's installed inventory and requires the full identity/digest to be
unchanged before writing evidence. If generation or this post-request identity
check fails, the reserved directory remains as a non-reusable diagnostic marker
rather than permitting the same path to be reused; it is not a completed run.

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

## Embedding capability rule

Ollama's native `/api/embed` endpoint accepts one string or an ordered batch and
returns one unit-L2-normalized vector per input. A model is eligible for R4 only
when `ollama show <tag>` lists the literal `embedding` capability. The separate
`embedding length` field reports a model's vector dimension; it is not evidence
that the tag supports the endpoint. This distinction is now enforced in the R4
preflight before any evidence directory or provider request is allocated.

Ollama currently recommends dedicated `embeddinggemma`, `qwen3-embedding`, and
`all-minilm` models for this use. Its embedding-model catalog also lists
`nomic-embed-text` and `mxbai-embed-large`. These are catalog examples, not a
selection or authorization to download one. Sources: [Ollama embeddings](https://docs.ollama.com/capabilities/embeddings),
[embedding-model catalog](https://ollama.com/library?type=embedding),
[EmbeddingGemma](https://ollama.com/library/embeddinggemma), and
[Qwen3 Embedding tags](https://ollama.com/library/qwen3-embedding/tags).

## Local eligibility diagnostic, not formal evidence

The upgraded local Ollama CLI reported version `0.33.1`. Read-only `show`
inspection of all twenty installed non-cloud artifacts found no advertised
`embedding` capability. The cloud-only `gpt-oss:120b-cloud` entry was excluded
because it is not an installed local artifact. In particular,
`qwen3.5:0.8b` reports an embedding length of `1024` but advertises only
completion, vision, tools, and thinking. A disposable direct `/api/embed`
request against that small chat candidate returned the runner's
embedding-disabled response. An isolated `ollama serve --embeddings` start
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
