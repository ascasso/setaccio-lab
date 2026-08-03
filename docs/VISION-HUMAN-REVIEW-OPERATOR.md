# Vision Prompt Human-Review Operator Sheet

**Authored by Grok 4.5**

This is an operator-focused companion to
[`docs/VISION-HUMAN-REVIEW.md`](VISION-HUMAN-REVIEW.md). That guide remains the
canonical policy and rubric source. Use this committed companion when you want
a single top-to-bottom path: prepare the worksheet, fill each section, decide,
and close out publicly without private detail.

This review is fixed before the candidate raw responses are read. It is not an
automated score, a model ranking, or a replacement for the saved-run verifier.
It must be executed by a human. Any agent-produced pass must be labeled
**agent-assisted review** and must not be represented as completed human
review.

## Current cycle status

As of 2026-08-02, the ignored saved-run directories for this Prompt v1/v2 pair
are unavailable. The project owner closed that review prerequisite through a
documented evidence-loss waiver without recreating or replacing the runs. No
actual-human comparative judgment or `adopt` / `revise` / `reject` decision is
claimed. Prompt v1 remains the operational interactive default, and Prompt v2
remains experimental and unadopted.

Use the historical commands below only if both exact saved runs are restored.
Any future Prompt v2 decision based on replacement evidence requires a
separately authorized paired protocol, new run names, preserved evidence, and
actual human review.

## Before you start

**Privacy (read once):**

- Keep source images, raw responses, reference observations, expected concepts,
  unsupported-detail notes, filenames, and local paths private.
- Edit only the ignored worksheet under
  `setaccio-lab/build/vision-human-review/`.
- Do not move the worksheet into tracked documentation.

**Workload for the documented locked pair (Prompt v1 vs v2):**

- 3 models × 4 cases = **12** pair sections
- Each section: private image → baseline judgment → candidate judgment → pair
  comparison
- Then one final adopt / revise / reject decision

**What the prepare task does and does not do:**

- Does: verify both saved runs, check deterministic comparability and corpus
  input identity, write one private `HUMAN-REVIEW.md`
- Does not: start Spring, call Ollama, select runs automatically, score
  semantics, or make the prompt decision
- Does not overwrite an existing worksheet; if the file already exists, open it
  instead of re-running blindly

Do not use earlier agent-assisted findings as your answers.

## 1. Prepare the worksheet

Run these steps from the repository root. The bare command
`./gradlew visionHumanReviewPrepare` is not enough: the task will not guess
which private evidence to compare.

### 1. Confirm the exact saved runs

```bash
ls -d \
  setaccio-lab/build/vision-matrix/2026-07-25-controlled-four-case \
  setaccio-lab/build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case
```

Both directories must print. If either reports `No such file or directory`,
stop and restore those exact saved runs before continuing.

### 2. Generate the worksheet

```bash
./gradlew :setaccio-lab:visionHumanReviewPrepare \
  --baseline-run-dir=build/vision-matrix/2026-07-25-controlled-four-case \
  --candidate-run-dir=build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case \
  --corpus-dir=local/vision-corpus
```

### 3. Open the worksheet

On macOS:

```bash
open \
  setaccio-lab/build/vision-human-review/2026-07-25-controlled-four-case--vs--2026-07-26-prompt-v2-controlled-four-case/HUMAN-REVIEW.md
```

On other systems, open the path printed by the successful task, or the same
`HUMAN-REVIEW.md` path above. Use your editor's Markdown preview if you want
the private source images shown beside the reference fields.

## 2. Review each model/case

The worksheet is already ordered by model and case. Work top to bottom. For
every `## model / case` section, complete the steps below in order.

### Step A — `### Private case reference`

Before reading either model response:

1. Inspect the private source image.
2. Read reference observation, expected concepts, unsupported details, and
   limitations.

Judge only against that human-authored reference, not against memory of other
cases or prior agent notes.

### Step B — Baseline responses, then `#### Human judgment for baseline`

1. Read the baseline response text (shared response if repetitions matched;
   both repetitions if they differ).
2. Fill the baseline judgment fields using the formats below.

### Step C — Candidate responses, then `#### Human judgment for candidate`

1. Read the candidate response text the same way.
2. Fill the candidate judgment fields independently. Do not copy the baseline
   marks without re-checking.

### Step D — `### Pair-level human comparison`

Answer each pair question briefly with the suggested vocabulary, then one
optional notes sentence.

### Step E — Repeat

Repeat Steps A–D for all 12 model/case sections before the final decision.

## 3. How to fill each judgment field

### Primary-concept retention

Select exactly one:

- `retained`
- `partially retained`
- `not retained`

Measure against the case's approved **expected concepts** only. A useful
visible extra detail may appear without turning an unsupported detail into a
fact.

### Unsupported specificity

Use one line per material issue, or write `none`.

**Format:**

```text
category: disposition — brief note
```

**Categories:** `location` | `identity` | `event` | `time` | `other`

**Dispositions:**

- `avoided` — the response does not assert the unsupported detail
- `unknown` — the response explicitly marks it unknown / unsupported
- `claimed` — the response asserts or clearly implies the unsupported detail

Use `other` only for a material limitation or quality assertion outside the
four prompt-target categories.

**Public-safe worked example** (illustrative only; not a real case):

```text
location: claimed — names a specific city not supported by the image
identity: unknown — correctly refuses who the person is
time: avoided — no date or time asserted
```

If several issues appear, stack multiple lines. Prefer short notes over
narrative.

### Excessive `unknown`

Mark **yes** only when `unknown` replaces a primary concept that is genuinely
visible in the image.

Do **not** mark it merely because the response correctly refused an unsupported
exact location, identity, event, or time.

If yes, name the suppressed visible concept in the worksheet field.

### Repetition finding

- Shared successful response shown once → record that repetitions matched.
- Separate repetitions shown → read both and describe the consistency
  difference.
- Never choose a preferred repetition.

### Pair-level answers (keep them comparable)

| Question | Prefer |
|---|---|
| Did the candidate retain the primary expected concepts? | `better` / `same` / `worse` |
| Did the candidate reduce unsupported specificity? | `reduced` / `same` / `increased` |
| Did `unknown` suppress useful visible detail? | `no` / `yes` (which concept) |
| Did repetition consistency change materially? | `same` / `changed` |
| Human notes | one sentence maximum |

## 4. Final human decision

Complete `## Final human decision` only after all 12 pair sections.

Select exactly one checkbox:

- **Adopt** the candidate prompt
- **Revise** the candidate prompt
- **Reject** the candidate prompt

**Decision order:**

1. Is there a material loss of useful visible concepts across the pair
   sections? → lean **Reject** or **Revise**.
2. Does the candidate reduce targeted unsupported specificity without that
   loss? → **Adopt**.
3. Is the candidate directionally useful, but a bounded failure pattern
   remains that another prompt change should address? → **Revise**.
4. Otherwise, if there is no meaningful improvement → **Reject**.

Then write:

- **Evidence-backed rationale** using aggregate counts and qualitative
  patterns only (for example: `X of 12 retained`, `Y pairs reduced location
  claims`, with `X` and `Y` filled from the completed human worksheet). Avoid
  private case narrative.
- **Next bounded hypothesis** as one concrete prompt or protocol change to try
  next, if any.

Completing the private worksheet does **not** change the interactive default
prompt. That remains a separate explicit implementation decision.

## 5. Public closeout checklist

Before anything leaves the private worksheet:

1. Summarize only safe aggregate counts and qualitative patterns by review
   dimension.
2. Label every semantic statement **human review**.
3. Confirm the public text contains no source images, raw responses, reference
   observations, expected concepts, unsupported-detail notes, filenames, or
   local paths.
4. Do not name an aggregate model winner.
5. Publish the adopt / revise / reject decision only after a private-detail
   scrub.
6. Do not flip the interactive default prompt unless the complete evidence
   supports a separate implementation change.

Keep deterministic evidence (invocation success, structural completion, repeat
matching, tokens, latency, infrastructure failures) distinct from these human
judgments.

## Appendix A — Path rule for a future pair

The commands above are canonical for the current Prompt v1 vs v2 controlled
four-case review. For a later pair:

1. Keep shell paths rooted at `setaccio-lab/build/...`.
2. Keep Gradle option values rooted at `build/...` (the task runs from the
   `setaccio-lab` module directory).
3. Replace only the baseline directory name, candidate directory name, and the
   matching worksheet folder name under `vision-human-review/`.

Do not ask the prepare task to auto-select the newest runs.

## Appendix B — Judgment vocabulary (quick reference)

| Field | Allowed values |
|---|---|
| Primary-concept retention | `retained`, `partially retained`, `not retained` |
| Unsupported category | `location`, `identity`, `event`, `time`, `other` |
| Unsupported disposition | `avoided`, `unknown`, `claimed` |
| Excessive `unknown` | `no`, `yes` + suppressed visible concept |
| Pair concept outcome | `better`, `same`, `worse` |
| Pair specificity outcome | `reduced`, `same`, `increased` |
| Final decision | `Adopt`, `Revise`, `Reject` |

## Appendix C — Relationship to the original guide

| Document | Role |
|---|---|
| [`VISION-HUMAN-REVIEW.md`](VISION-HUMAN-REVIEW.md) | Canonical policy and rubric source |
| This operator sheet | Committed execution-focused companion authored by Grok 4.5 |

If the two documents ever disagree on policy substance, follow
`VISION-HUMAN-REVIEW.md`. Update policy there first, then align this companion
in the same change so the workflow does not drift.
