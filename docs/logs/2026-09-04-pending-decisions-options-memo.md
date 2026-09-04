# Pending decisions options memo

Authorized scope: a documentation-only slice preparing three decisions that
are recorded in the repository as open. This memo verifies the technical
claims behind each decision against current source and current on-disk state,
and lays out the owner's options with their exact costs. It adopts, revises,
and rejects nothing; no code, evidence, or closeout changed while preparing
it.

## 1. Explicit reasoning policy in the existing suites

### Is this blocked on the diagnostic measurement?

No. The narrow reasoning-default and execution-boundary diagnostic named in
`docs/DEFERRED-WORK.md` under "Reasoning Policy in the Existing Suites" has
run twice and is recorded: the v1 run on 2026-09-03
(`docs/logs/2026-09-03-thinking-diagnostic-run.md`) and the v2 run on
2026-09-04 from clean commit `acc3979`
(`docs/logs/2026-09-04-reasoning-default-boundary-run.md`). Both are on disk
and offline-verified: `setaccio-lab/local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content/`
and `.../2026-09-04-reasoning-default-boundaries/` both contain
`manifest.json`, `SUMMARY.md`, and a results file (confirmed present by
directory listing on 2026-09-04). This decision is not blocked; it can be
made now.

### Verifying the two named costs

`docs/DEFERRED-WORK.md` ("Reasoning Policy in the Existing Suites") names two
costs for making reasoning policy explicit in the chat matrix, Anthropic
portability matrix, Phase 5 answer matrix, and fact-check suites:

**Cost 1 — manifest settings compared as an exact JSON string.** Verified,
and it applies uniformly across all four named suites, each with its own
`manifestSettings(result)` factory whose output is diffed against the saved
`manifest.settings()` as a serialized JSON tree:

- `setaccio-lab/src/chatMatrix/java/com/setaccio/lab/chatmatrix/ChatMatrixEvidence.java:247-250`
  (`chatMatrixVerify`) — `expected.toString().equals(actual.toString())`.
- `setaccio-lab/src/chatMatrix/java/com/setaccio/lab/chatmatrix/AnthropicChatMatrixEvidence.java:104-107`
  (`anthropicChatMatrixVerify`) — `sameJsonValue(expectedSettings, actualSettings)`.
- `setaccio-lab/src/localEvaluation/java/com/setaccio/lab/evaluation/LocalEvaluationEvidence.java:233-236`
  (`localEvaluationVerify`).
- `setaccio-lab/src/retrieval/java/com/setaccio/lab/retrieval/RetrievalAnswerEvidence.java:183-186`
  (`retrievalAnswerVerify`).

Each of the four `manifestSettings()` factories already includes a
`runSettings` (or equivalent) entry
(`ChatMatrixProtocol.java:108`, `LocalEvaluationProtocol.java:138`,
`RetrievalAnswerProtocol.java:50`, `AnthropicChatMatrixEvidence.java:296`),
which is the natural place an explicit reasoning policy would go. Adding any
key there changes what current code recomputes as `expected` for every past
run of that suite, so it breaks the JSON-string comparison for **every**
retained manifest of that suite, not a subset.

**Cost 2 — `ChatGenerationOption`/`ChatProviderOptionSupport` classification.**
Verified, but **narrower in scope than the general sentence in
`docs/DEFERRED-WORK.md` implies.** `ChatProviderOptionSupport`'s compact
constructor requires every `ChatGenerationOption` constant to be classified as
supported or unsupported
(`setaccio-lab/src/main/java/com/setaccio/lab/chat/ChatProviderOptionSupport.java:30-37`);
adding a constant (e.g. `REASONING`) makes any already-saved JSON that
predates it fail that check on deserialization, because the constructor loop
now iterates over a value the old JSON never classified. This type is carried
directly in row-level raw evidence for exactly two suites:

- `setaccio-lab/src/chatMatrix/java/com/setaccio/lab/chatmatrix/ChatMatrixRow.java:16`
- `setaccio-lab/src/chatMatrix/java/com/setaccio/lab/chatmatrix/AnthropicChatMatrixRow.java:18`

It is **not** present in the fact-check suite's row
(`setaccio-lab/src/localEvaluation/java/com/setaccio/lab/evaluation/LocalEvaluationRow.java` —
uses an unrelated `LocalFactCheckJudgeSettings`, no reference to
`ChatGenerationOption` anywhere in `src/localEvaluation`), and it is **not**
present in the Phase 5 answer suite's row: `RetrievalAnswerRow.invocation` is
typed `RetrievalAnswerInvocationOutcome`
(`setaccio-lab/src/retrieval/java/com/setaccio/lab/retrieval/RetrievalAnswerInvocationOutcome.java`),
a projection that carries model identity, prompt identity, usage, latency,
and failure category but drops `ChatProviderOptionSupport` entirely — it is
never referenced anywhere under `src/retrieval` outside test files. So a new
`ChatGenerationOption` constant cannot make retained fact-check or Phase 5
answer raw JSON undeserializable; only retained chat-matrix and
Anthropic-chat-matrix row JSON is exposed to this specific failure mode.

### What is actually on disk today, per suite

This matters because Cost 1 and Cost 2 only fire against a file that exists.
`docs/DEFERRED-WORK.md`'s Evidence Retention Status section already records
that the chat matrix, Anthropic O3 portability run, and fact-check A5 run
were not present on the maintainer's host as of 2026-09-02, and that
"verifies offline" statements describe what was true at closeout, not what
is present today. A direct listing of `setaccio-lab/local/evidence/` on
2026-09-04 confirms nothing has changed since: no `chat-matrix` or
`anthropic-chat-matrix` directory exists at all, and `evaluation-matrix`
(fact-check) exists but is empty. Phase 5's answer suite has no
`retrieval-answer` directory either.

A 2026-09-04 top-level listing of `setaccio-lab/local/evidence/` shows only
five suite directories: `evaluation-matrix`, `lfm-tool-capability`,
`retrieval-embedding`, `thinking-diagnostic`, and `tool-compatibility`. There
is no `chat-matrix`, `anthropic-chat-matrix`, `retrieval-answer`,
`retrieval-relevancy`, `retrieval-evaluation`, or `vision-matrix` directory
at all.

| Suite | Retained run named in `docs/DEFERRED-WORK.md` | On disk today (`setaccio-lab/local/evidence/`) | Exposed to Cost 1 today | Exposed to Cost 2 today |
| --- | --- | --- | --- | --- |
| Chat matrix (Ollama) | 2026-08-05, commit `51025cf`; replacement baseline (undated commit `215ea18` in the Phase 3 boundary text) | No `chat-matrix/` directory present | No file to break | No file to break |
| Anthropic chat matrix | 2026-08-05, commit `3810a19` (`claude-haiku-4-5-20251001`) | No `anthropic-chat-matrix/` directory present | No file to break | No file to break |
| Fact-check (A5) | 2026-08-03, commit `5d41362`, judge `gemma4:e2b` | `evaluation-matrix/` exists, empty | No file to break | Not applicable (type not used) |
| Phase 5 R5 (answer) | 2026-08-30, commit `c724e5a93c89...` | No `retrieval-answer/` directory present | No file to break | Not applicable (type not used) |

So the practical, present-tense cost of changing any of the four
`manifestSettings()` factories or adding a `ChatGenerationOption` constant is
**zero already-saved files broken today** — there is nothing left on disk in
any of these four suites for the change to invalidate. The real cost is
prospective: it would (a) permanently foreclose ever re-verifying the
original named runs against current code if any of that evidence is later
recovered (the Evidence Retention Status section says recovery is "not
currently possible," not impossible), and (b) invalidate the frozen
protocol-identity language already written into each closeout's prose in
`docs/DEFERRED-WORK.md`, which would then describe a settings shape current
code no longer reproduces, even though the prose itself would remain
factually accurate as a historical record.

A fresh future run of any of these suites is not exposed to either cost:
`manifestSettings()` and row serialization both run from current code on
write and on verify, so a new run's own manifest and rows are always
self-consistent regardless of what the enum or settings map contain.

### Options

1. **Leave `PROVIDER_DEFAULT` in all four suites (status quo).** No schema
   change, no verification risk of any kind, present or future. The
   limitation stays exactly as recorded: these suites' protocol identity
   does not distinguish default-thinking from explicit policy, and the
   2026-09-03/2026-09-04 diagnostics remain the only place that link is
   measured for this artifact. This authorizes nothing and changes nothing.

2. **Add an explicit reasoning policy directly to the four suites' existing
   schemas.** Requires a new `ChatGenerationOption` constant (touching Cost 2
   for chat matrix and Anthropic chat matrix) and a `manifestSettings()`
   change in all four factories (Cost 1, all four). Given the table above,
   this breaks no currently-present file, but permanently closes the door on
   ever re-verifying the originally named runs against current code if that
   evidence is ever recovered, and makes every existing closeout paragraph
   describe a settings shape current code can no longer regenerate. It would
   not, by itself, rerun, repair, or reinterpret any closed evidence.

3. **Version the affected suites' schemas (v1/v2) rather than mutating them
   in place**, following the precedent already used for the reasoning
   diagnostic itself (`docs/DEFERRED-WORK.md`, "A version-aware protocol v2 is
   implemented ... It preserves v1 reading, exact manifest reconstruction,
   deterministic analysis, and report bytes"). A v2 reader would keep the
   existing v1 `manifestSettings()`/`ChatGenerationOption` shape intact for
   already-written and any later-recovered evidence, while a new v2 shape
   carries the explicit policy for future runs only. This avoids both named
   costs entirely at the price of a second reader/writer path per affected
   suite, mirroring work already done once for the thinking-diagnostic
   suite. It does not itself decide whether any suite should send an
   explicit policy — only how such a change could be made non-destructive if
   the owner chooses option 2's substance.

4. **Decide per suite rather than uniformly.** Because chat matrix and
   Anthropic chat matrix carry Cost 2 and fact-check/Phase 5 answer do not,
   and because all four evidence directories are currently empty (chat
   matrix, Anthropic, Phase 5 answer) or empty of runs (fact-check), the
   owner could treat these as two different questions with two different
   present costs, independent of which structural approach (in-place vs.
   versioned) is chosen for each.

None of these options changes whether reasoning is enabled at the model
boundary, and none touches the closed suites' retained closeout language.

## 2. The LFM tool-capability diagnostic against closed Phase 1 and Phase 2

`docs/logs/2026-09-03-lfm-tool-capability-check.md` explicitly leaves to the
owner "whether it warrants any change to their recorded status, a new
deferred-work note, or no action at all," and states plainly that it does not
establish the historical cause of the Phase 1/Phase 2 `PROVIDER_FAILURE`
classifications, because it ran under a later Ollama runtime (`0.33.3`
instead of the runtime installed during Phase 1/Phase 2) and is not evidence
about the underlying LFM2.5 architecture's latent tool-calling ability.

### Current state: the connecting note already exists

`docs/DEFERRED-WORK.md`'s "Completed Phase 1 Small-Model Tool-Compatibility
Boundary" section already carries a paragraph describing this diagnostic,
its outcome, and its exact limits, ending: "No Phase 1 or Phase 2 evidence or
closeout was rerun, repaired, replaced, reinterpreted, or rewritten by that
diagnostic." So the "new deferred-work note" option named in the 2026-09-03
log is, in substance, already exercised: Phase 1/Phase 2's own closeout text
is unchanged, and a pointer to the diagnostic with its exact boundary already
sits beside it. The remaining decision is narrower than "add a note" — it is
whether that note's language is sufficient, whether Phase 1/Phase 2's own
status/interpretation text should be edited, or whether further diagnostic
work should be authorized.

### What further verification is possible today

Any attempt to compare the LFM diagnostic against the original Phase 1/Phase
2 requests is constrained by evidence availability, not just runtime
mismatch. `setaccio-lab/local/evidence/tool-compatibility/` exists but is
empty on disk today (confirmed 2026-09-04) — the original Phase 1 (16-row)
and Phase 2 (32-attempt) raw request/response evidence is not present to
compare against, consistent with `docs/DEFERRED-WORK.md`'s Evidence
Retention Status listing "Phase 1, Phase 2, Phase 3" among the suites whose
evidence was absent as of 2026-09-02. Only the LFM diagnostic's own bundle
survives, at
`setaccio-lab/local/evidence/lfm-tool-capability/2026-09-03-lfm-tool-capability/`
(confirmed present, 8 files plus `SHA256SUMS.txt`). So the diagnostic's
finding — that the currently deployed artifact/runtime rejects a tool-bearing
request synchronously at the provider boundary — cannot currently be checked
byte-for-byte against what Phase 1/Phase 2 actually sent, only argued from
consistency (same artifact digest, same class of first-turn failure).

### Options

1. **No action.** Phase 1 and Phase 2 keep their exact recorded status:
   `PROVIDER_FAILURE` on every attempt, cause unidentified. The existing
   cross-reference paragraph in `docs/DEFERRED-WORK.md` stands as the only
   link between the two. This claims nothing beyond what is already written
   and requires no further work.

2. **Strengthen the existing cross-reference note's wording without touching
   Phase 1/Phase 2's own interpretation.** For example, making explicit in
   the Phase 1/Phase 2 status prose itself (not only in the separate
   diagnostic paragraph) that a *plausible, currently-deployed-artifact-level*
   candidate mechanism now has direct evidence, while the *historical* cause
   remains and will likely remain unconfirmed given lost Phase 1/Phase 2
   evidence. This is a documentation-only wording change; it does not rerun,
   repair, or reinterpret any closed run, and does not resolve the runtime
   mismatch or the missing historical evidence.

3. **Change Phase 1/Phase 2's recorded status** — e.g., narrowing "no
   identified cause" to something like "a plausible but unconfirmed
   provider-boundary tool-rejection cause, observed on the same artifact
   under a later runtime." This is a substantive edit to closed-phase
   interpretation language, which `docs/DEFERRED-WORK.md` currently
   describes as unchanged by the diagnostic; it is explicitly the kind of
   call this memo is not authorized to make.

4. **Authorize a new, separately scoped diagnostic** aimed at closing the
   runtime-mismatch gap (e.g., pinning or otherwise reproducing the
   historical Ollama runtime version recorded for Phase 1/Phase 2, if such a
   version is still obtainable). This would need its own scope-start request
   and clean-baseline protocol per the Standing Local Ollama Authorization,
   and, even if run, could still not be checked byte-for-byte against the
   original Phase 1/Phase 2 request/response bodies, since that raw evidence
   is itself gone (see the evidence-loss discussion in Section 3 below and
   `docs/DEFERRED-WORK.md`'s Evidence Retention Status). It would not, by
   itself, restore or repair the original Phase 1/Phase 2 evidence.

## 3. The ignored-evidence loss

`docs/DEFERRED-WORK.md`'s Evidence Retention Status section records that, as
of 2026-09-02, only the Phase 5 R4 embedding run's evidence was present on
the maintainer's host; evidence for Phase 1, Phase 2, Phase 3, the Phase 4
five-arm breakpoint study, Phase 5 R3/R5/R6, the fact-check A5 run, the chat
matrix, the vision matrices, and the Anthropic O3 portability run was not.
This is labeled the project's second ignored-evidence loss; the first was the
vision Prompt v1/v2 pair, closed on 2026-08-02 through a documented
evidence-loss waiver (`docs/logs/2026-08-02.md`). No waiver is claimed for
the second loss, and no closeout is withdrawn: each recorded interpretation
was bounded to what its evidence supported at the time it was verified.

A 2026-09-04 directory listing of `setaccio-lab/local/evidence/` confirms the
second loss is still current: no directory exists for the chat matrix,
Anthropic chat matrix, Phase 5 R3/R5/R6, or the vision matrices, and the
`evaluation-matrix` (fact-check) and `tool-compatibility` (Phase 1/2/3)
directories exist but are empty. Only the R4 embedding run, the LFM
tool-capability diagnostic, and the two reasoning/thinking diagnostics
(2026-09-03 and 2026-09-04) — all created after the 2026-09-03 durable
evidence root migration, or copied to it — are present.

### The shape of the existing 2026-08-02 waiver

For reference, the first waiver (`docs/logs/2026-08-02.md`, "August Gate 0
evidence-loss closure") had this shape: a read-only recovery search across
the workspace, Trash, common temporary/user-file locations, Spotlight, and
any configured Time Machine destination or local snapshot; a recorded owner
decision to proceed without recreating or replacing the original runs; an
explicit statement that no actual-human `adopt`/`revise`/`reject` judgment is
claimed; and a requirement that any future decision on that surface use a
separately authorized paired protocol with new run names, preserved
evidence, and actual human review — the missing artifacts must never be
recreated under their original names or represented as the original
immutable evidence.

### Options

1. **Extend the same waiver treatment to the second loss, in the same
   shape.** A documented waiver stating: a read-only recovery attempt was
   made (or explicitly was not, if the owner elects to skip it) across the
   same locations as 2026-08-02; the missing runs (Phase 1, Phase 2, Phase 3,
   the Phase 4 breakpoint study, Phase 5 R3/R5/R6, fact-check A5, the chat
   matrix, the vision matrices, the Anthropic O3 run) will not be recreated
   under their original names; and no actual-human judgment beyond what was
   already recorded at each original closeout is claimed. **What this would
   restore:** nothing — it is a closure of the retention gap itself, not a
   restoration of evidence. **What it would cost:** effectively none beyond
   the documentation, since no closeout is currently withdrawn and none of
   the bounded public-safe interpretations depend on the raw evidence being
   re-inspectable. **What it would not do:** it would not make any of the
   lost runs re-verifiable by a third party or the maintainer, and it would
   not authorize recreating any of them under their original run names.

2. **Leave it exactly as currently recorded.** `docs/DEFERRED-WORK.md`
   already states plainly that no waiver is claimed and no closeout is
   withdrawn, and that third-party/maintainer re-verification of the named
   runs is "not currently possible" rather than declared permanently closed.
   **What this preserves:** the option to later decide the recovery question
   differently for different suites (e.g., waive some, re-run others) without
   having pre-committed to a blanket waiver. **What it costs:** the
   Evidence Retention Status section remains an open-ended finding rather
   than a closed item, and every future reader must keep re-deriving that
   "verifies offline" is historical, not current, for nine-plus named runs.

3. **A separately authorized re-run programme**, suite by suite, each under
   its own new scope-start request, fresh clean-baseline commit, and new
   dated run-id under `setaccio-lab/local/evidence/<suite>/`, per the
   Standing Local Ollama Authorization and each suite's existing "any further
   run needs a new scope-start request" language already in
   `docs/DEFERRED-WORK.md`. **What this would restore:** current, freshly
   verifiable evidence for whichever suites are re-run, under new run
   identities. **What it would not do:** it would not recreate or validate
   the *original* named runs — new runs would use new commits, new dates, and
   (for Ollama suites) whatever runtime version is installed now, so they
   cannot retroactively confirm what the original runs actually contained
   (the same limitation already noted for the LFM diagnostic in Section 2).
   For suites bound to a human-confirmed artifact — the fact-check fixture
   review, the vision corpus, the Phase 3 T3.1 cohort approval — a re-run
   would not need new human confirmation of the *catalog* (those are
   independent tracked artifacts, still present), but any new *interpretation*
   of a re-run's results would need its own review, mirroring the original
   effort (e.g., the T2.5/T3.4 worksheets, the A6 interpretation). **Cost in
   scope:** this is the only option that requires new local model
   invocations; all are already covered by the Standing Local Ollama
   Authorization for already-installed models, but several of the original
   cohorts (e.g., the Phase 3 96-row cohort, the Phase 5 R5/R6 matrices) are
   multi-model, multi-row protocols that would take meaningfully more
   execution time than a single diagnostic call, and re-running Phase
   1/Phase 2 specifically would still not resolve the runtime-version
   mismatch identified in Section 2.

4. **Split the treatment by suite** rather than choosing one option for all
   nine-plus lost runs — for example, waiving suites whose closeout already
   reads as fully bounded and self-contained (Phase 1, Phase 2, the Phase 4
   breakpoint study, the Anthropic O3 run), while authorizing a fresh re-run
   only for a suite the owner considers still load-bearing for near-term
   decisions (e.g., Phase 5 R5/R6 if retrieval work continues). This is not a
   fourth mechanism so much as applying options 1 and 3 selectively; its cost
   is the added bookkeeping of tracking which named runs fall under which
   treatment.

None of these options changes any existing closeout's public-safe
interpretation, and none authorizes a model pull, credential, Docker use,
remote provider, release, tag, or push.

## What this memo does not do

It does not adopt, revise, or reject any of the three decisions above. It
does not edit `docs/DEFERRED-WORK.md`, any closed suite's evidence or
closeout language, or any other tracked plan — none of those became stale by
writing this memo, since it introduces no new fact that contradicts what they
already record; it only adds citation-level precision the owner can use to
choose among the options above. It performs no model call, run, evidence
mutation, model pull, remote-provider access, Docker use, release, tag, or
push.
