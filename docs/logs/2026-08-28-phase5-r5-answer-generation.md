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

## Not executed or authorized by this implementation

No Ollama answer model was selected or invoked. No R5 output directory or
formal answer evidence was created. This change does not authorize a model
pull, remote answer provider, credentials, spending, Docker, endpoint
migration, `RelevancyEvaluator`, release, tag, or push.
