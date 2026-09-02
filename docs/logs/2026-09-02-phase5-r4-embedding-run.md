# Phase 5 R4 embedding retrieval formal run

On 2026-09-02, the deferred Phase 5 R4 embedding-retrieval slice was explicitly
started by the project owner and completed one formal clean-baseline run using
the prioritized `qwen3-embedding:0.6b` candidate.

## Candidate provenance

The prioritized candidate was **not** already installed when the slice was
started. The project owner pulled `qwen3-embedding:0.6b` on 2026-09-02 and then
authorized the run.

This is a deliberate, owner-approved departure from the earlier framing in
[2026-08-28-phase5-r4-embedding-retrieval.md](2026-08-28-phase5-r4-embedding-retrieval.md)
and [2026-09-01-r4-qwen3-embedding-priority.md](2026-09-01-r4-qwen3-embedding-priority.md),
which described selecting among already-installed candidates and directed a stop
rather than a pull if the tag was absent. It is recorded here because the run
evidence itself cannot express it: the manifest's `pullModelStrategy: never`
describes the run's own behavior, which is accurate, and carries no statement
about how the tag came to be installed beforehand.

No model was pulled, substituted, or downloaded by the run. No other model was
pulled at any point.

## Eligibility gate

Read-only `ollama show qwen3-embedding:0.6b` inspection preceded any evidence
directory allocation and satisfied the R4 gate:

- Capabilities: `tools`, `thinking`, `embedding`. The literal `embedding`
  capability is present, which is the condition the gate requires.
- The separate `embedding length` field reported `1024`; consistent with the
  standing rule, that field alone was not treated as sufficient.
- Full digest:
  `ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d`.
- Architecture `qwen3`, `595.78M` parameters, context length `32768`,
  quantization `Q8_0`.

The runner re-resolved the installed identity after the embedding request and
required the requested/effective tag and digest to be unchanged before writing
evidence.

## Run

```text
./gradlew :setaccio-lab:retrievalEmbedding --no-daemon \
  --ollama-base-url=http://localhost:11434 \
  --embedding-model=qwen3-embedding:0.6b \
  --top-k=5 \
  --output-dir=build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b
```

- Clean full Git baseline `4c13b4ac24f4b0d39497f662b9df0c930169f35f`, captured
  before the request and re-verified unchanged before the evidence write.
- Loopback-only endpoint; `endpointCategory` `loopback-local`.
- One batch of 12 approved corpus documents followed by 14 confirmed queries,
  exactly one attempt, 120000 ms timeout, `pullModelStrategy` `never`.
- Vector dimension `1024`; `whole-document-v1` chunking, `unit-l2-v1`
  normalization, `cosine-descending-document-id` ranking.
- Corpus `public-safe-retrieval-corpus` v1
  (`2c3f72f153cfb097caeef73ae210a66265af054b585b1b2a292162f289087b9d`); query
  catalog `public-safe-retrieval-query-fixtures` v1
  (`ced4a31b13542a47d171a88879400fe649a0de985eeecd4ca58fea4feefb59b5`).
- Framework versions recorded by runtime detection: Spring Boot `4.1.1`, Spring
  AI `2.0.1`.

Ignored evidence was written to
`setaccio-lab/build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b/`:
raw `retrieval-embedding-results.json`
(`aa56b1add65bdd9506676170f33df3fedd580f6d16b9dc48b9a80b04362b716a`),
`SUMMARY.md`
(`d47408f5139c0183e536b58b761d8c7d4e79b918af0c7c54e32e599f602f7662`),
and the shared v1 `manifest.json`.

## Top K selection

Top K `5` was selected for this run against the 12-document corpus. It is the
only R4 run parameter not fixed in `RetrievalEmbeddingProtocol`, and no prior
repository run or log recorded a top-K precedent to inherit. The value retains
the single expected supporting document of each confirmed fixture plus margin
above the two-entry `forbiddenDocumentIds` lists. It is a retention setting: the
analyzer bounds it to the corpus size and otherwise recomputes ranking
independently of it. It is not a tuned, compared, or optimized value.

## Verification

```text
./gradlew :setaccio-lab:retrievalEmbeddingVerify --no-daemon \
  --run-dir=build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b
```

Generation-time integrity analysis passed: envelope, provider metadata,
document and query vector unit-L2 normalization, vector count and dimension,
and independent recomputation of every retained top-K row. Offline verification
then passed with no provider call and no Spring context. The working tree
remained clean throughout; the evidence path is ignored by `.gitignore`.

## Documentation updates

Current-status references that asserted R4 was deferred were updated to record
the completed run; historical and dated records were preserved unchanged:

- [DEFERRED-WORK.md](../DEFERRED-WORK.md): status line, R4 preamble, and the
  R4 row's status and gate columns. The gate now preserves ignored
  R3/R4/R5/R6 evidence and requires a new explicit scope-start request, fresh
  capability and digest check, and a new dated directory for any further run.
- [TEST-PLAN.md](../TEST-PLAN.md): header status and the `retrievalEmbedding`
  gating bullet.
- [SmallModelToolCallingCompatibilityPlan.md](../SmallModelToolCallingCompatibilityPlan.md):
  Phase 5 status bullets, the closeout paragraph, the future-R4-gate preamble,
  and the R4 packet marker, which moves from `Status: deferred` to
  `Status: completed 2026-09-02`.
- `AGENTS.md`: the two Phase 5 closeout statements that described R4 as
  deferred.
- `CHANGELOG.md`: one `Added` entry under `[Unreleased]`.

Preserved as written: the dated `Closeout status (2026-08-30)` blockquote in
the protocol plan, which is superseded by an appended dated update note rather
than rewritten; the 2026-09-01 `CHANGELOG.md` candidate-priority entry; and all
earlier `docs/logs/` entries, including
[2026-08-28-phase5-r4-embedding-retrieval.md](2026-08-28-phase5-r4-embedding-retrieval.md)
and [2026-08-30-small-model-tool-calling-protocol-closeout.md](2026-08-30-small-model-tool-calling-protocol-closeout.md).

No code, test fixture, or retained evidence file was modified. The stale Spring
AI `2.0.0` version references tracked separately for the 2.0.1 follow-up are
not part of this change.

## Interpretation boundary

This records one local embedding-generation and ranking configuration and its
offline-verifiable evidence. It does not set a retrieval-support threshold,
score no-match behavior, generate answers, establish semantic relevance or
answer correctness, or compare embedding models, tags, or retrieval methods. It
is not a model-quality, ranking, or selection conclusion. Retained R3, R5, and
R6 evidence is unchanged; nothing was rerun, repaired, replaced, mutated, or
published.

No Docker, credential, remote provider, release, tag, or push was used.
