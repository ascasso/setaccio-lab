# Agent guide consistency and retention status

On 2026-09-02 the project owner asked for the outstanding repository-guidance
issues to be addressed. This is a documentation-only change. No code, test,
fixture, or evidence file was touched, and no experiment was started.

## Standing Work Loop

`AGENTS.md` stated the commit rule three times and never stated the
documentation or logging requirement as a general rule. The convention was real
— 59 dated logs and a changelog entry for changes as small as a dependency bump
— but it lived only in the commit history, so an agent following the file
literally could finish work undocumented.

The three restatements are replaced by one `## Standing Work Loop` section
covering verify, document, log, commit, with pushing named as separately gated.
The hard stop keeps the push prohibition and drops its duplicated commit
sentence; the Git Workflow section now points at the loop instead of repeating
it.

## Publication Boundary reconciliation

Three statements contradicted the Publication Boundary added earlier that day:

- the pre-commit checklist item "Confirm no private docs or generated outputs
  are staged", which forbade exactly what the boundary authorizes;
- the standing local Ollama authorization's gate list;
- the near-term plan's gate list.

All three now defer to the boundary: publication of ignored output remains
gated except for the deterministic summaries and manifests it permits.

## Stale R4 status

`AGENTS.md` contradicted itself. Two Phase 5 statements recorded the completed
2026-09-02 R4 run, while a third, inside the R5 snapshot bullet, still read
"R4 formal embedding execution remains deferred because retained eligibility
evidence did not establish an already-installed local model advertising
Ollama's literal `embedding` capability."

The 2026-09-02 R4 log recorded updating "the two Phase 5 closeout statements
that described R4 as deferred"; this third one was missed. It now records the
completed run and the gate for any further one. The state snapshot heading also
moves from 2026-09-01 to 2026-09-02, and gains a bullet for the tracked
documentation split introduced that day.

## Evidence retention status

`docs/DEFERRED-WORK.md` gains an `## Evidence Retention Status` section.

The index asserts in several places that retained evidence "verifies offline".
Those statements were true at their closeouts and are not currently checkable:
the only formal run evidence present on the maintainer's host is the Phase 5 R4
run. The new section says so once, at the top, so every downstream statement
reads as a record of its closeout rather than a claim about today.

This is deliberately additive. No closeout was rewritten, no result withdrawn,
and no waiver claimed. The owner previously decided to leave the individual
statements as written, and that decision stands; a per-closeout rewrite remains
available as separately requested work.

The section also names the distinction the project had conflated: manifests,
artifact hashes, and non-overwriting directories protect a saved run from
alteration and silent replacement, not from deletion. Evidence lives under
ignored `build/`, which ordinary Gradle cleaning removes. Durable retention
outside `build/` remains separately authorized future work.

## Verification

- `./gradlew :setaccio-core:build :setaccio-lab:build --offline` passes.
- `AGENTS.md` contains no remaining statement that R4 is deferred.
- Every relative link in the changed documents resolves.

## Boundary

This change authorizes no experiment, run, rerun, repair, replacement,
reanalysis, evidence mutation, model pull, remote provider, credential, Docker
use, spending, release, tag, or push.
