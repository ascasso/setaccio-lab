# Phase 5 R6 evaluator evidence integrity hardening

## Scope

This focused follow-up addresses a review finding in the provider-free R6
offline evidence analyzer. It changes no retrieval, answer-generation, or
evaluator invocation behavior.

## Change

For a successful evaluator invocation, the analyzer now derives the normalized
`YES`/`NO` verdict and response diagnostic from the retained raw response and
requires the recorded fields to match. It continues to require the matching
Spring evaluator pass/score for normalized `YES` and `NO` results.

For an attempted failed invocation, the analyzer now requires one of the
recorded provider-failure diagnostics: unavailable model, timeout, or provider
failure. `NONE`, response-format diagnostics, and not-attempted diagnostics
cannot describe a failed invocation.

The response-diagnostic derivation is shared with the live boundary so recorded
and offline semantics cannot drift. Provider-free regression tests construct
both previously accepted inconsistent states and prove that the analyzer—and
the evidence writer before any raw artifact is created—reject them.

## Verification

```bash
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
```

The command passed. It uses fake providers only; no Ollama model, remote
provider, credential, Docker dependency, or formal R5/R6 evidence run was used
or changed.

## Boundary

This hardening makes saved R6 evaluator observations internally verifiable. It
does not make an evaluator verdict ground truth, a human-support judgment, an
answer-correctness result, a model-quality result, or a formal R6 execution.
