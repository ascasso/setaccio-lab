# Phase 5 R5 answer-generation boundary

## Scope completed

R5 adds a provider-neutral answer-generation boundary over verified R3
retrieval evidence. It does not rerun retrieval: every answer row copies the
complete R3 retrieval row, including returned public document text, IDs,
ranks, content SHA-256 values, lexical score fields, and confirmed fixture
labels. The new tracked `retrieval-grounded-answer-v1` prompt receives exactly
that preserved context.

The opt-in `retrievalAnswerMatrix` task requires a clean current Git baseline,
a verified clean R3 run directly under `build/retrieval-evaluation/`, a
loopback-only Ollama URL, explicit already-installed answer-model tag, explicit
maximum output tokens, seed, timeout, and fresh dated output directly under
ignored `build/retrieval-answer/`. It resolves the requested/effective local
model and full digest before reserving output, runs one sequential answer per
R3 row with temperature `0.0`, one attempt, and pull strategy `never`, then
rechecks the model identity and code baseline before writing evidence.

The raw answer evidence retains prompt/model identity, complete rendered
prompt, raw answer, safe provider response identifier, available usage,
latency, invocation outcome, bracketed document-reference behavior, and exact
`NO_SUPPORT` abstention. Its shared-v1 manifest and summary have dedicated
offline `retrievalAnswerVerify` and `retrievalAnswerReanalyze` tasks. A failed
run after output reservation retains its non-reusable diagnostic directory.

## Deliberate evidence boundary

R5 records reference syntax only: a bracketed retrieved document ID is not a
semantic support finding. Every row records unsupported-assertion assessment as
`NOT_ASSESSED`, while retaining the answer and source text for a later bounded
R6 or human review. Retrieval success, reference behavior, answer correctness,
semantic relevance, and any evaluator result are not merged into a score.

## Formal execution and closeout

On 2026-08-30, the retained R3 run
`build/retrieval-evaluation/2026-08-28-r3-baseline/` verified offline before
R5 execution. From clean commit
`c724e5a93c89eb5de8a11e9d1774a523f77bda37`, the one formal matrix selected
the already-installed local `gemma4:e2b` tag solely as this protocol's
operational answer model. The requested and effective tags matched, with full
digest `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`
under Ollama `0.33.2`.

The run locked `retrieval-grounded-answer-v1`, temperature `0.0`, seed `42`,
maximum output tokens `256`, timeout `PT2M`, exactly one sequential attempt
per preserved R3 row, and pull strategy `never`. Its fresh ignored evidence
directory is `build/retrieval-answer/2026-08-30-r5-gemma4-e2b/`; raw answers
remain there and are not published.

All 14 planned answer rows were retained without retry, repair, replacement,
or omission. Ten rows completed with invocation outcome `NONE`; four retained
`EMPTY_RESPONSE`. No row recorded `MODEL_UNAVAILABLE`, `TIMEOUT`,
`AUTHENTICATION`, `RATE_LIMIT`, or `PROVIDER_FAILURE`. Two rows used the exact
`NO_SUPPORT` abstention. Four rows had retrieved-document-only bracketed
references, while four had no document reference; no malformed or unreturned
document reference was observed.

`retrievalAnswerVerify` passed, deterministic `retrievalAnswerReanalyze`
regenerated the saved summary successfully, and the provider-free
`retrievalFixtureTest` plus `git diff --check` passed. These are bounded
invocation, abstention, and reference-syntax observations. They do not assess
assertion support or answer correctness, establish semantic relevance, rank or
select a model, or initiate R4 or R6.

## Verification

The provider-free retrieval suite passed with fake chat-invocation coverage for
grounded answers, explicit abstention, unreturned-document references, empty
responses, malformed reference syntax, timeouts, provider failures, model
identity drift, saved-evidence integrity, and
summary-only repair:

```text
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
git diff --check
```

## Still outside this closeout

This R5 closeout does not start R4 embedding retrieval or R6 relevancy
evaluation. It does not authorize a model pull, remote answer provider,
credentials, spending, Docker, endpoint migration, release, tag, or push.
