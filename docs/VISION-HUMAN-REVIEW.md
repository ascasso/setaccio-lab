# Vision Prompt Human-Review Guide

This rubric governs human review of an offline-verified paired vision-prompt
comparison. It is fixed before the candidate raw responses are read. It is not
an automated score, a model ranking, or a replacement for the saved-run
verifier.

This rubric is intended to be executed by a human. Any agent-produced pass
against it must be labeled explicitly as agent-assisted review and must not be
substituted for, or represented as, completed human review.

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

   Use your editor's Markdown preview if you want the private source images
   displayed beside the reference information.

## Perform the review

The worksheet is already organized in model/case order. Work from top to
bottom; do not use the earlier agent-assisted findings as your answer.

1. For the first model/case section, inspect the private image and read its
   reference observation, expected concepts, unsupported details, and
   limitations before judging either response.
2. Read the baseline response. Under **Human judgment for baseline**, select
   one primary-concept result: `retained`, `partially retained`, or `not
   retained`.
3. In the same baseline block, record any unsupported specificity. Use one of
   `location`, `identity`, `event`, `time`, or `other`, and state whether the
   response `avoided`, marked `unknown`, or `claimed` that detail.
4. Read the candidate response and complete the same fields under **Human
   judgment for candidate**.
5. Mark **Excessive unknown** only when `unknown` replaced a primary concept
   that is genuinely visible in the image. Do not mark it merely because the
   response correctly refused an unsupported exact detail.
6. If the worksheet shows one shared response, record that the repetitions
   matched. If it shows separate repetitions, read both and describe the
   consistency difference; do not choose a preferred repetition.
7. Complete the **Pair-level human comparison** questions: concept retention,
   unsupported-specificity reduction, excessive `unknown`, repetition change,
   and any concise human notes.
8. Repeat steps 1–7 for every model/case section.
9. At **Final human decision**, select exactly one outcome:

   - **Adopt** when the candidate reduces the targeted unsupported specificity
     while retaining useful visible concepts without a material regression.
   - **Revise** when the candidate is directionally useful but has a bounded
     failure pattern that another prompt change should address.
   - **Reject** when it provides no meaningful improvement or introduces a
     material loss of useful visible information.

10. Write a short evidence-backed rationale and one next bounded hypothesis.
    This completes the private human worksheet; it does not itself change the
    interactive default prompt.

## Scope and inputs

- Use `visionHumanReviewPrepare` to verify the explicitly selected baseline and
  candidate runs, check deterministic comparability and corpus input identity,
  and generate the ignored private `HUMAN-REVIEW.md` worksheet. The task
  organizes evidence only; it does not perform any judgment in this rubric.
- Review each model and approved case against the immutable baseline and the
  verified candidate evidence.
- Use the private human-authored reference metadata only in the ignored local
  corpus. Do not copy source images, reference observations, expected concepts,
  unsupported-detail notes, paths, filenames, or raw response text into public
  documentation.
- Keep deterministic evidence (invocation success, structural completion,
  repeat matching, tokens, latency, and infrastructure failures) distinct from
  the human judgments below.

## Judgment definitions

Record the following human-review judgments for both prompt versions:

1. **Primary-concept retention:** `retained`, `partially retained`, or `not
   retained`, measured against the case's approved expected concepts. A useful
   visible detail may be present without turning an unsupported detail into a
   fact.
2. **Unsupported specificity:** classify each asserted or implied unsupported
   detail as location, identity, event, time, or other. Record whether the
   response avoids it, explicitly marks it `unknown`, or makes the unsupported
   claim. `other` is reserved for a material limitation or quality assertion
   not covered by the four prompt-target categories.
3. **Excessive `unknown`:** mark this when `unknown` replaces a
   human-confirmed primary visible concept. With only four private cases, do
   not invent a percentage threshold or aggregate recall score; publish only
   the count of affected model/case judgments and a qualitative explanation.
4. **Repetitions:** when repetitions match exactly, review the shared result
   once and record the match. When they differ, review both and record the
   difference as a consistency finding rather than selecting a preferred
   response.

The generated worksheet is intentionally non-overwriting. Save your edits only
in its ignored `setaccio-lab/build/vision-human-review/` location, and do not
move it into tracked documentation because it contains private metadata and raw
responses.

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
