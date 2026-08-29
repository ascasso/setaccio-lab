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

## Not executed or authorized by this implementation

No Ollama evaluator model was selected or invoked. No R6 output directory or
formal relevancy evidence was created. This change does not authorize a model
pull, remote evaluator, credentials, spending, Docker, endpoint migration,
release, tag, or push.
