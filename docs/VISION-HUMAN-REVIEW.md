# Vision Prompt Human-Review Guide

This rubric governs human review of an offline-verified paired vision-prompt
comparison. It is fixed before the candidate raw responses are read. It is not
an automated score, a model ranking, or a replacement for the saved-run
verifier.

This rubric is intended to be executed by a human. Any agent-produced pass
against it must be labeled explicitly as agent-assisted review and must not be
substituted for, or represented as, completed human review.

For the same workflow as a compact top-to-bottom checklist, use the committed
[`VISION-HUMAN-REVIEW-OPERATOR.md`](VISION-HUMAN-REVIEW-OPERATOR.md) companion.
This document remains authoritative if the two ever disagree on policy.

## Before you start

- Keep source images, raw responses, reference observations, expected concepts,
  unsupported-detail notes, filenames, and local paths private.
- Edit only the ignored worksheet under
  `setaccio-lab/build/vision-human-review/`; do not move it into tracked
  documentation.
- The current Prompt v1/v2 pair contains 3 models × 4 cases, so the worksheet
  has 12 model/case sections followed by one final decision.
- The preparation task verifies both saved runs, checks deterministic
  comparability and corpus input identity, and writes the private worksheet. It
  does not start Spring, contact Ollama, select runs automatically, score
  semantics, or make the prompt decision.
- The task will not overwrite an existing worksheet. If the target worksheet
  already exists, open it and continue there instead of re-running the task.

Do not use earlier agent-assisted findings as your answers.

## Prepare the worksheet

Run these steps from the repository root. The bare command
`./gradlew visionHumanReviewPrepare` is not sufficient because the task will
not guess which private evidence should be compared.

1. Confirm that the exact saved baseline and candidate runs are present:

   ```bash
   ls -d \
     setaccio-lab/build/vision-matrix/2026-07-25-controlled-four-case \
     setaccio-lab/build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case
   ```

   The command must print both directories. If either directory reports
   `No such file or directory`, stop: the ignored evidence required for this
   review is not present in this checkout. Restore those exact saved runs
   before continuing.

2. Run this command exactly as written:

   ```bash
   ./gradlew :setaccio-lab:visionHumanReviewPrepare \
     --baseline-run-dir=build/vision-matrix/2026-07-25-controlled-four-case \
     --candidate-run-dir=build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case \
     --corpus-dir=local/vision-corpus
   ```

3. On macOS, open the generated worksheet with this exact command:

   ```bash
   open \
     setaccio-lab/build/vision-human-review/2026-07-25-controlled-four-case--vs--2026-07-26-prompt-v2-controlled-four-case/HUMAN-REVIEW.md
   ```

   On other systems, open the same path or the path printed by the successful
   task. Use your editor's Markdown preview if you want the private source
   images displayed beside the reference information.

## Perform the review

The worksheet is already organized in model/case order. Work from top to
bottom. For every model/case section, complete this sequence:

1. Under **Private case reference**, inspect the private image and read the
   reference observation, expected concepts, unsupported details, and
   limitations before reading either response.
2. Read the baseline response: use the shared response when repetitions match,
   or read both repetitions when they differ. Complete **Human judgment for
   baseline** using the formats below.
3. Read the candidate response the same way and complete **Human judgment for
   candidate** independently. Do not copy the baseline marks without
   re-checking the candidate.
4. Complete **Pair-level human comparison** using the preferred vocabulary
   below, then add at most one concise human-notes sentence.
5. Repeat steps 1–4 for all 12 current model/case sections before making the
   final decision.

## Fill the judgment fields

### Primary-concept retention

Select exactly one result for each prompt:

- `retained`
- `partially retained`
- `not retained`

Measure only against the case's approved expected concepts. A response may add
a useful visible detail without turning an unsupported detail into a fact.

### Unsupported specificity

Use one line for each material unsupported detail from the private reference or
response, or write `none`. Use this format:

```text
category: disposition — brief note
```

Choose one category: `location`, `identity`, `event`, `time`, or `other`.
Choose one disposition:

- `avoided` — the response neither asserts nor implies the unsupported detail
- `unknown` — the response explicitly marks the detail unknown or unsupported
- `claimed` — the response asserts or clearly implies the unsupported detail

Use `other` only for a material limitation or quality assertion outside the
four prompt-target categories. For example:

```text
location: claimed — names a specific city not supported by the image
identity: unknown — correctly refuses to identify the person
time: avoided — does not assert a date or time
```

This example is illustrative and does not describe a private case.

### Excessive `unknown`

Mark `yes` only when `unknown` replaces a primary concept genuinely visible in
the image, and name the suppressed visible concept. Do not mark it merely
because the response correctly refuses an unsupported exact location,
identity, event, or time. With only four private cases, do not invent a
percentage threshold or aggregate recall score.

### Repetition finding

- When the worksheet shows one shared successful response, record that the
  repetitions matched.
- When it shows separate repetitions, read both and describe the consistency
  difference.
- Do not choose a preferred repetition.

### Pair-level comparison

Keep answers comparable across all sections:

| Question | Preferred values |
|---|---|
| Did the candidate retain the primary expected concepts? | `better`, `same`, or `worse` |
| Did the candidate reduce unsupported specificity? | `reduced`, `same`, or `increased` |
| Did `unknown` suppress useful visible detail? | `no`, or `yes` plus the concept |
| Did repetition consistency change materially? | `same` or `changed` |
| Human notes | one sentence maximum |

Keep deterministic evidence—invocation success, structural completion, repeat
matching, tokens, latency, and infrastructure failures—distinct from these
human judgments.

## Make the final human decision

Complete **Final human decision** only after every model/case section. Select
exactly one outcome:

- **Adopt** when the candidate reduces targeted unsupported specificity while
  retaining useful visible concepts without a material regression.
- **Revise** when the candidate is directionally useful but has a bounded
  failure pattern that another prompt change should address.
- **Reject** when the candidate provides no meaningful improvement or
  introduces a material loss of useful visible information.

Write an evidence-backed rationale and one next bounded hypothesis. Because the
worksheet is private, the rationale may identify the affected case sections
needed to audit the decision. The public closeout must use only scrubbed
aggregate counts and qualitative patterns.

Completing the worksheet does not itself change the interactive default prompt.
That remains a separate explicit implementation decision.

## Public closeout

After completing every worksheet section:

1. Summarize only safe aggregate counts and qualitative patterns by review
   dimension.
2. Label every semantic statement as **human review**.
3. Do not publish source images, raw responses, reference observations,
   expected concepts, unsupported-detail notes, filenames, or local paths.
4. Do not name an aggregate model winner.
5. Record the adopt/revise/reject decision separately in public documentation
   only after checking that the wording contains no private case detail.
6. Do not change the interactive default prompt unless that is supported by the
   complete evidence and made as a separate explicit implementation decision.
