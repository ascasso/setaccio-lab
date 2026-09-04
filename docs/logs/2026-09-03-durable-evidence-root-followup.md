# Durable evidence root follow-up

On 2026-09-03, the project owner reported two defects in the durable-evidence
migration and authorized their correction: stale Gradle defaults for both
human-review preparation tasks, and parent symbolic links that could redirect
formal evidence outside its lexical durable root.

## Done

- Updated `toolCompatibilityHumanReviewPrepare` to pass the fixed ignored root
  `local/evidence/tool-compatibility-human-review` and
  `visionHumanReviewPrepare` to pass
  `local/evidence/vision-human-review`. Both values are resolved from the
  `setaccio-lab` project directory.
- Strengthened the shared `EvidenceSuiteRoot` contract. Before allocating a
  new run, resolving a saved durable or legacy run, or accepting a fixed
  human-review root, it now rejects every existing symbolic link from the
  project-relative evidence root through the requested target. This covers
  durable `local`, `local/evidence`, suite, and run components, and the
  equivalent legacy `build` components.
- Added provider-free regression coverage for those durable and legacy parent
  links and for a fixed worksheet root above a symbolic-link parent.

## Verification

```bash
./gradlew :setaccio-lab:test --offline
./gradlew :setaccio-core:build :setaccio-lab:build --offline
./gradlew :setaccio-lab:chatMatrixTest :setaccio-lab:localEvaluationTest \
  :setaccio-lab:localEvaluationBudgetTest :setaccio-lab:retrievalFixtureTest \
  :setaccio-lab:thinkingDiagnosticTest :setaccio-lab:toolCompatibilityTest \
  :setaccio-lab:toolSearchSmokeTest :setaccio-lab:visionMatrixTest --offline
git diff --check
```

The focused test, core/lab builds, and every migrated provider-free suite test
passed after the expected leaf-link assertion was updated to match the earlier
shared guard. A Gradle configuration assertion also confirmed both
human-review task properties resolve to the fixed durable roots. No Ollama,
Anthropic, or other provider was contacted.

## Boundaries

No formal run evidence was allocated, read, verified, reanalyzed, repaired,
replaced, moved, or published. This change does not authorize a live run,
model pull, remote provider, credentials, Docker, release, tag, or push.
