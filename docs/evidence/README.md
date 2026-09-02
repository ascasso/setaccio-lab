# Published Evidence Examples

This directory holds **publication copies** of deterministic run summaries and
their evidence manifests. It exists so a reader can see the exact shape of what
this lab produces without having to run anything.

## What is here, and what is not

Each subdirectory is copied from an ignored run directory under
`setaccio-lab/build/`. Only two artifact roles are copied:

- `SUMMARY.md` — the deterministic, regenerable summary.
- `manifest.json` — the versioned evidence envelope.

Raw result artifacts are **not** copied. Raw model output, embedding vectors,
evaluator responses, and per-row payloads stay ignored, exactly as every
completed closeout requires. Nothing here was rerun, repaired, replaced,
reanalyzed, or edited; the files are byte-identical copies.

## These are not runnable run directories

A published copy will **not** pass the suite's offline verification task. For
the R4 example, `retrievalEmbeddingVerify` fails by design, because the
manifest declares a `raw-result` artifact that is deliberately absent here.
That is the intended behavior: verification checks a complete saved run, and
this is a partial publication copy.

## What a reader can check

The manifest is self-describing, so two things are independently checkable
without any model, network, or Spring context:

```bash
# The summary matches the SHA-256 the manifest declares for it.
shasum -a 256 docs/evidence/2026-09-02-r4-qwen3-embedding-0-6b/SUMMARY.md
# -> d47408f5139c0183e536b58b761d8c7d4e79b918af0c7c54e32e599f602f7662
```

The manifest also records the code baseline commit, framework versions,
execution engine, the complete locked protocol settings, and the full immutable
Ollama model digest, so the run's identity can be inspected directly.

## Current examples

### `2026-09-02-r4-qwen3-embedding-0-6b`

One Phase 5 R4 local embedding-retrieval run: 12 corpus documents and 14
confirmed queries embedded in a single batch through `qwen3-embedding:0.6b` at
digest `ac6da0df…`, producing 1024-dimension unit-L2 vectors and deterministic
top-K `5` cosine rankings.

Its interpretation boundary, quoted from the summary itself:

> This records one local embedding-generation and ranking configuration. It
> does not set a support threshold, score no-match behavior, generate answers,
> establish semantic relevance, or compare models or retrieval methods.

The full run context is recorded in
[`docs/logs/2026-09-02-phase5-r4-embedding-run.md`](../logs/2026-09-02-phase5-r4-embedding-run.md).
