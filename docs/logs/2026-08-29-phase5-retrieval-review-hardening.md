# Phase 5 retrieval evidence review hardening

## Scope

This provider-free follow-up strengthens the R6 evaluator-model identity check
and corrects R5/R6 deterministic timeout summaries. It does not invoke a model,
change a formal protocol, or modify preserved evidence.

## Changes

- R6 offline analysis now compares every retained nonblank provider-reported
  response model with the locked effective evaluator model. A mismatch is an
  integrity failure, and the evidence writer rejects it before creating a raw
  artifact.
- R5 and R6 summaries now render the exact ISO-8601 duration reconstructed from
  `requestTimeoutMillis`, including fractional-second values such as `PT1.5S`.
- Provider-free tests cover both the rejected model-drift path and exact
  fractional timeout rendering.

## Verification

```bash
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
./gradlew :setaccio-lab:test --rerun-tasks --no-daemon
git diff --check
```

No Ollama model, remote provider, credential, Docker dependency, or formal
retrieval evidence run is used by these checks.

## Boundary

The model check validates attribution only when the provider reports a nonblank
response model. It does not invent unavailable metadata or make evaluator
output a human judgment, answer-correctness result, model-quality result, or
ground truth.
