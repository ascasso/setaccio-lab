# Phase 5 R6 relevancy-evaluation boundary

## Scope completed

R6 adds an explicit, opt-in Spring AI `RelevancyEvaluator` boundary over a
verified R5 answer run. It does not rerun retrieval or answer generation. Each
row retains its exact R5 answer row and therefore its complete R3-derived
retrieved public document text, document IDs, ranks, content SHA-256 values,
lexical observations, and fixture labels. The evaluator receives only that
preserved retrieved context; missing context is rejected before any evaluator
call.

The `retrievalRelevancyMatrix` task requires a clean current Git baseline, a
verified clean R5 run directly under `build/retrieval-answer/`, a loopback-only
Ollama URL, an explicit already-installed evaluator model tag, explicit output
tokens, seed, timeout, and a fresh dated output directly under ignored
`build/retrieval-relevancy/`. It resolves the requested/effective evaluator
model and full digest before reserving output, applies temperature `0.0`, one
attempt, and pull strategy `never`, then rechecks model identity and the clean
code baseline before it writes evidence.

The tracked `retrieval-relevancy-evaluator-v1` prompt drives Spring AI's
`RelevancyEvaluator` through a narrow recording wrapper. An attempted row
retains prompt/model identity, raw evaluator text, Spring evaluator pass/score,
normalized `YES`/`NO` verdict, safe response metadata, available usage,
latency, and diagnostic category. A row with an unavailable R5 answer is also
recorded as explicitly not attempted. The shared-v1 manifest and summary have
dedicated offline `retrievalRelevancyVerify` and
`retrievalRelevancyReanalyze` tasks.

## Deliberate evidence boundary

R6 preserves five distinct fields: deterministic retrieval expectation,
evaluator invocation/outcome, evaluator score/verdict, human support judgment,
and answer correctness. It also records whether the answer and evaluator
models are the same deployed artifact as `SELF_EVALUATION` rather than
normalizing that relationship away.

An evaluator verdict is not a fixture-expectation match, ground truth, a human
support finding, an answer-correctness claim, a merged retrieval/answer score,
or a model ranking or selection result.

## Formal execution and closeout

On 2026-08-30, the retained R5 run
`build/retrieval-answer/2026-08-30-r5-gemma4-e2b/` verified offline before R6
execution. From clean commit
`f704d989429a10769ce334276dc79de5bd7cd308`, the one formal matrix selected
the already-installed local `granite4.1:3b` tag solely as this protocol's
operational evaluator. The requested and effective tags matched, with full
digest `6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb`
under Ollama `0.33.2`. This was a different deployed artifact from R5's answer
model, recorded as `SEPARATE_EVALUATOR`; that relationship does not establish
evaluator independence or ground truth.

The run locked `retrieval-relevancy-evaluator-v1`, temperature `0.0`, seed
`42`, maximum output tokens `64`, timeout `PT2M`, exactly one attempt per
eligible preserved R5 row, and pull strategy `never`. Its fresh ignored
evidence directory is
`build/retrieval-relevancy/2026-08-30-r6-granite4-1-3b/`; raw evaluator output
remains there and is not published.

All 14 preserved R5 rows were retained without retry, repair, replacement, or
omission. Eight eligible rows completed with evaluator outcome `NONE`. Two
rows with missing retrieved context were retained as
`NOT_ATTEMPTED_MISSING_CONTEXT`, and four unavailable R5-answer rows were
retained as `NOT_ATTEMPTED_NO_ANSWER`; none of those six rows invoked the
evaluator. No row recorded `EVALUATOR_MODEL_UNAVAILABLE`, `TIMEOUT`,
`PROVIDER_FAILURE`, `EMPTY_RESPONSE`, or `MALFORMED_VERDICT`. All 14 rows
recorded `SEPARATE_EVALUATOR`, with no `SELF_EVALUATION` row.

`retrievalRelevancyVerify` passed, deterministic
`retrievalRelevancyReanalyze` regenerated the saved summary successfully, and
the provider-free `retrievalFixtureTest` plus `git diff --check` passed. These
are bounded evaluator-invocation and not-attempted-row observations only. They
do not reinterpret an individual verdict, convert a verdict into retrieval
expectation, human support, or answer correctness, establish semantic
correctness or ground truth, rank a model, or initiate R4 or human review.

## Verification

The provider-free retrieval suite passed with mocked Spring AI evaluator
coverage for actual preserved-context propagation, explicit model options,
`YES`/`NO`, empty and malformed output, unavailable evaluator, timeout,
provider failure, missing-context rejection, unavailable-answer suppression,
self-evaluation flagging, evidence integrity, and summary-only repair:

```text
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
git diff --check
```

## Still outside this closeout

This R6 closeout does not start R4 embedding retrieval or human semantic
review. It does not authorize a model pull, remote evaluator, credentials,
spending, Docker, endpoint migration, release, tag, or push.
