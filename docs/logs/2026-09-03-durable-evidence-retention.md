# Durable evidence retention

On 2026-09-03 the project owner explicitly started a new slice: move the formal
evidence lifecycle off ignored Gradle `build/` output and onto a durable
private root, and preserve the only remaining formal run before changing any
path behavior.

## Why

Formal run evidence was written under ignored `build/<suite>/` directories,
which ordinary Gradle cleaning removes. Integrity and retention are different
properties. The versioned manifest, per-artifact SHA-256 values, and
non-overwriting run directories protect a saved run from being altered or
silently replaced; none of them protects it from being deleted.

That gap has cost the project twice: the vision Prompt v1/v2 pair, closed on
2026-08-02 through a documented evidence-loss waiver, and, per the Evidence
Retention Status section of [DEFERRED-WORK.md](../DEFERRED-WORK.md), everything
except the Phase 5 R4 embedding run.

## Authorization boundary

Authorized: the Step 0 retention copy, the path migration, documentation, one
focused commit.

Not authorized and not done: any model call, run, rerun, reanalysis of
historical evidence, evidence mutation, model pull, remote provider,
credential, Docker use, release, tag, or push. No suite verifier or reanalyzer
was invoked during Step 0.

## Step 0 — preserving the surviving run

The ignore rule `/setaccio-lab/local/evidence/` was added to `.gitignore`
before anything was created under that path. No existing ignore rule was
weakened.

Source SHA-256 values were recorded first, from
`setaccio-lab/build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b/`:

| File | SHA-256 | Bytes |
| --- | --- | --- |
| `manifest.json` | `9f3e391e3fc13cdfcd4f93540fed150fc65104c24dda50b9ce35f1a9e18fa769` | 1928 |
| `retrieval-embedding-results.json` | `aa56b1add65bdd9506676170f33df3fedd580f6d16b9dc48b9a80b04362b716a` | 379792 |
| `SUMMARY.md` | `d47408f5139c0183e536b58b761d8c7d4e79b918af0c7c54e32e599f602f7662` | 1597 |

All three files, including the 379 KB raw artifact, were copied to
`setaccio-lab/local/evidence/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b/`.

Confirmation performed on the copy:

- The manifest records hashes for `retrieval-embedding-results.json` and
  `SUMMARY.md`, but not for `manifest.json` itself. Both listed artifacts were
  checked against the **copied** manifest, in the copied run directory, and
  matched on declared size and SHA-256.
- All three source and destination files compared byte-for-byte identical.
- The three source hashes were recomputed after the copy and were unchanged.

This was retention copying and integrity confirmation. It is not a rerun,
repair, replacement, reanalysis, or mutation, and it withdraws no closeout.

## The durable root

New formal evidence is now allocated only under

```
setaccio-lab/local/evidence/<suite>/<run-id>/
```

which is ignored but is not a Gradle output directory, so `clean` does not
remove it. The opt-in tasks run with the `setaccio-lab` module directory as
their working directory, so a command issued from the repository root passes a
module-relative path such as
`local/evidence/retrieval-embedding/<run-id>`.

CLI option names and explicit-path behavior are unchanged. Callers still supply
`--output-dir`, `--run-dir`, `--baseline-run-dir`, `--candidate-run-dir`,
`--baseline-run`, `--candidate-run`, `--source-retrieval-run-dir`,
`--source-answer-run-dir`, `--ollama-run-dir`, `--output-root`, the multi-arm
`--output-dir-<tokens>` and `--run-dir-<tokens>` options, and the budget
`--budget-64-run-dir` / `--budget-256-run-dir` options. Only the root those
values point at changed.

## Centralized contract

`com.setaccio.lab.evidence.EvidenceSuiteRoot` is the single durable suite-root
contract. It owns the root, direct-child, path-traversal, and symbolic-link
policy for all twelve suite roots — `chat-matrix`, `anthropic-chat-matrix`,
`evaluation-matrix`, `retrieval-evaluation`, `retrieval-embedding`,
`retrieval-answer`, `retrieval-relevancy`, `tool-compatibility`,
`tool-compatibility-human-review`, `tool-search-matrix`, `vision-matrix`, and
`vision-human-review` — replacing more than twenty independent
reimplementations that had drifted apart. Each suite keeps only its own date
and run-id rule, because those legitimately differ (a permissive
`YYYY-MM-DD`-substring check in some suites, a strict `LocalDate.parse` in
others), and tightening or loosening either would have changed suite behavior
rather than migrating it.

Migrated writers: `visionMatrix`, `chatMatrix`, `anthropicChatMatrix`,
`localEvaluation`, `localEvaluationBudget`, `localEvaluationBreakpoint`,
`toolSearchMatrixBaseline`, `toolCompatibilityMatrix`,
`toolCompatibilityCohort`, `toolCompatibilityPromptMatrix`,
`retrievalEvaluation`, `retrievalEmbedding`, `retrievalAnswerMatrix`,
`retrievalRelevancyMatrix`, and both human-review worksheet preparers.

Migrated consumers: every `*Verify` and `*Reanalyze` task; the comparison
tasks (`visionMatrixCompare`, `retrievalEvaluationCompare`,
`toolCompatibilityCompare`, `toolCompatibilityCohortCompare`,
`localEvaluationBudgetCompare`, `localEvaluationBreakpointCompare`);
`toolCompatibilityCohortFrontier`; human-review preparation inputs;
baseline/candidate inputs; the downstream R3-to-R5 and R5-to-R6 source-evidence
inputs; the Anthropic `--ollama-run-dir` paired-evidence input; and the
multi-arm budget and breakpoint run-directory arguments.

## Legacy acceptance

Readers still accept `build/<suite>/<run-id>` so evidence saved before this
change can be verified, reanalyzed, compared, and consumed. That acceptance is
read-only: no writer allocates there, and it never authorizes rewriting,
repairing, reanalyzing into, or moving old evidence.

Direct-child, date/run-id, path-traversal, symlink, non-overwrite, and
fresh-allocation safeguards are preserved. One of them needed repair rather
than a straight move: `ToolCompatibilityPreflight.allocate` walked the
symbolic-link check up from a hard-coded three parent directories, which was
the project directory only while the root was `build/<suite>/`. It now derives
that starting point from the durable root's own depth through
`EvidenceSuiteRoot.projectDirectoryOfDurableRun`, so the walk still starts at
the project directory and cannot silently shorten if the root changes again. One safeguard was tightened rather
than preserved: the Tool Search reader and writer previously accepted anything
below `build/tool-search-matrix/` via a prefix check, and now use the same
direct-child rule as every other suite. Every saved Tool Search run was already
a direct child, so nothing readable became unreadable.

Not migrated, deliberately: ordinary interactive endpoint output under
`build/lab-results/` (including `SETACCIO_LAB_OUTPUT_DIR`), compilation output,
and unrelated generated files.

## docs/evidence/

`docs/evidence/` remains a tracked, partial publication copy of permitted
deterministic summaries and manifests. It is not a runnable evidence store, is
never a task input, and is not the source for offline verification. That is now
stated in `docs/evidence/README.md`, `AGENTS.md`, and the Publication Boundary
section of `DEFERRED-WORK.md`.

## Verification

Provider-free throughout. No Ollama, Anthropic, or other provider was
contacted.

```
./gradlew :setaccio-core:build :setaccio-lab:build --offline
./gradlew :setaccio-lab:retrievalFixtureTest :setaccio-lab:chatMatrixTest \
  :setaccio-lab:localEvaluationTest :setaccio-lab:localEvaluationBudgetTest \
  :setaccio-lab:toolCompatibilityTest :setaccio-lab:toolSearchSmokeTest \
  :setaccio-lab:visionMatrixTest --offline
```

All tasks passed. `git diff --check` reported no whitespace errors.

New `EvidenceSuiteRootTest` covers the durable and legacy root strings, unsafe
suite names, new-run allocation restricted to a direct child of the durable
root, refusal of a `build/<suite>/` write target, refusal of nested and
parent-traversing targets, blank and untrimmed values, saved-run reads from
both roots, refusal of missing, symlinked, and foreign-root saved runs, and
fixed worksheet roots pinned to the durable root. Existing suite path tests
were updated to the new contract; each retrieval suite test now asserts
explicitly that a `build/<suite>/` path is rejected as a write target, and the
read-side legacy acceptance is covered once, in `EvidenceSuiteRootTest`.

## What this does not claim

This is a retention and path-policy change. It produces no new evidence, does
not re-verify or reinterpret any historical run, and withdraws no closeout. The
runs recorded as lost in the Evidence Retention Status section remain lost; the
durable root prevents the next loss, it does not recover the previous ones. No
new run is authorized by this change.
