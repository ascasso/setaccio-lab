# Small-model tool-calling protocol closeout

## Scope

The owner-approved Phase 0–5 protocol closeout records completed authorized
work without starting a new experiment. It preserves the historical packets and
ignored evidence unchanged.

## Retained verified evidence

The following saved evidence was verified offline on 2026-08-30. No verifier
started Spring, contacted Ollama, or reanalyzed evidence.

| Slice | Retained aggregate observation | Offline verification |
| --- | --- | --- |
| R3 lexical retrieval | 12/12 matching fixtures retrieved the expected supporting document in top 1 and top 3; 2/2 no-match fixtures were correct; all 14 rows were stable across the immediate repeat. | `retrievalEvaluationVerify` passed. |
| R5 grounded answers | All 14 preserved rows were retained: 10 completed, 4 were empty responses, and 2 used exact `NO_SUPPORT`. | `retrievalAnswerVerify` passed. |
| R6 relevancy evaluation | All 14 preserved R5 rows were retained: 8 eligible evaluator calls completed, 2 missing-context rows and 4 unavailable-answer rows were not attempted. | `retrievalRelevancyVerify` passed. |

R5 and R6 are the completed local-model executions. Their aggregates do not
establish answer correctness, semantic support or relevance, human support,
evaluator ground truth, model quality, ranking, or selection. An R6 evaluator
result remains distinct from retrieval expectation, human support, and answer
correctness.

## R4 deferred boundary

Formal embedding execution is deferred. Retained eligibility evidence did not
establish an already-installed local model advertising Ollama's literal
`embedding` capability. An `embedding length` field is insufficient for this
gate.

Before a future R4 slice may begin, it needs separate explicit authorization,
a read-only eligibility check showing the literal capability and complete model
digest, and a clean-baseline lock of the tag, corpus/query identities,
chunking, normalization, distance metric, top-K, timeout, one attempt, and
no-pull policy. The opt-in runner must create fresh non-overwriting ignored
evidence, retain every scheduled outcome without retry or replacement, and be
verified offline before separate analysis. Meeting those prerequisites does not
by itself authorize execution.

## Still unauthorized

This closeout does not authorize model inspection or selection for R4, model
pulls or installs, provider invocation, credentials, remote providers,
spending, Docker, evidence reanalysis or mutation, formal-matrix reruns,
repairs or replacements, raw-output publication, human semantic review,
release, tag, push, branch promotion, or successor work.
