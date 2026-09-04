# Three pending decisions resolved

The project owner reviewed
`docs/logs/2026-09-04-pending-decisions-options-memo.md` and decided all three
open items it laid out. This record captures the decisions and what was done
to close each one.

## 1. Explicit reasoning policy in the existing suites

**Decision: status quo.** The chat matrix, Anthropic portability matrix,
Phase 5 answer matrix, and fact-check suites keep sending
`PROVIDER_DEFAULT`. No `ChatGenerationOption` constant was added, no
`manifestSettings()` factory changed, and no suite schema changed.

Updated `docs/DEFERRED-WORK.md` ("Reasoning Policy in the Existing Suites")
to record that the first follow-up question is now decided rather than
deferred. The recorded limitation and the two 2026-09-03/2026-09-04
diagnostic runs remain the only measurement of the default-policy and
chat-boundary link for this artifact.

## 2. The LFM tool-capability diagnostic against closed Phase 1 and Phase 2

**Decision: as is.** No change to Phase 1/Phase 2's recorded status or
interpretation. The existing cross-reference paragraph in
`docs/DEFERRED-WORK.md` ("Completed Phase 1 Small-Model Tool-Compatibility
Boundary") stands as sufficient; Phase 1 and Phase 2 keep their `PROVIDER_FAILURE`,
cause-unidentified status exactly as written.

Updated `docs/DEFERRED-WORK.md` to record that this was reviewed and decided,
so a future reader does not need to re-treat it as an open item.

## 3. The ignored-evidence loss

**Decision: waive**, in the same shape as the 2026-08-02 vision Prompt v1/v2
waiver (`docs/logs/2026-08-02.md`).

### Read-only recovery search performed before recording the waiver

Mirroring the 2026-08-02 search scope, a read-only check covered:

- **Repository workspace.** `setaccio-lab/local/evidence/` contains only five
  suite directories (`evaluation-matrix`, `lfm-tool-capability`,
  `retrieval-embedding`, `thinking-diagnostic`, `tool-compatibility`); no
  `chat-matrix`, `anthropic-chat-matrix`, `retrieval-answer`,
  `retrieval-relevancy`, `retrieval-evaluation`, or `vision-matrix` directory
  exists. `evaluation-matrix` and `tool-compatibility` exist but have no run
  subdirectories. A broader search also found stray pre-durable-root
  `setaccio-lab/build/evaluation-matrix/` and
  `setaccio-lab/build/tool-compatibility/` directories; both are empty.
- **`~/.Trash`**: no matching directory found.
- **`/tmp` and `$TMPDIR`**: no matching directory found.
- **Spotlight (`mdfind`)**: queries for suite directory names and dated
  run-id patterns (e.g. `chat-matrix`, `2026-08-05`, `tool-compatibility`)
  returned only compiled Gradle task classes, test-result XML files, and
  unrelated documents — no saved evidence directories.
- **Time Machine**: `tmutil listbackups` reports no machine directory
  configured for this host (no backup destination). `tmutil
  listlocalsnapshots /` failed with an OS permission error, so a
  local-snapshot copy could not be definitively ruled out.

No copy of the missing evidence was found through any accessible channel.

### What the waiver does and does not do

The waiver closes the second evidence-loss gap recorded in
`docs/DEFERRED-WORK.md`'s Evidence Retention Status section. It does not
withdraw any of the affected closeouts — Phase 1, Phase 2, Phase 3, the
Phase 4 five-arm breakpoint study, Phase 5 R3/R5/R6, the fact-check A5 run,
the chat matrix, the vision matrices, and the Anthropic O3 portability run
all stand exactly as their public-safe interpretations already recorded them,
including the Phase 2 `inconclusive` T2.5 decision, the Slice A6 fact-check
interpretation, and the T3.4/T3.5/T3.6 cohort interpretations. No
actual-human `adopt`/`revise`/`reject` judgment is claimed for any of them
beyond what each closeout already recorded.

None of the named runs may be recreated under their original run names or
represented as the original immutable evidence. Any future re-run of one of
these suites needs its own new scope-start request, fresh clean-baseline
commit, and new dated run directory under
`setaccio-lab/local/evidence/<suite>/`, exactly like every other deferred
re-run gate already in `docs/DEFERRED-WORK.md`.

Updated `docs/DEFERRED-WORK.md`'s Evidence Retention Status section to
record the waiver, the search performed, and its scope.

## What this record does not authorize

No model call, model pull, run, evidence mutation, remote-provider access,
credential, Docker use, release, tag, or push occurred while recording these
decisions. No closed evidence or closeout was rerun, repaired, replaced, or
reinterpreted.

### Verification

Ran
`./gradlew :setaccio-core:test :setaccio-lab:test :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build --rerun-tasks`.
19 tasks completed successfully. Verification remained offline: no Ollama
model call, model pull, remote-provider request, or Docker/Testcontainers
runtime occurred. Ran `git diff --check`.
