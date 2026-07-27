# Vision Prompt Human-Review Rubric

This rubric governs human review of an offline-verified paired vision-prompt
comparison. It is fixed before the candidate raw responses are read. It is not
an automated score, a model ranking, or a replacement for the saved-run
verifier.

This rubric is intended to be executed by a human. Any agent-produced pass
against it must be labeled explicitly as agent-assisted review and must not be
substituted for, or represented as, completed human review.

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

## Per model and case

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

The generated worksheet is intentionally non-overwriting. Complete it only in
its ignored `build/vision-human-review/` location, and do not move it into
tracked documentation because it contains private metadata and raw responses.

## Public closeout

Publish only safe aggregate counts and qualitative findings by dimension. Label
each semantic statement as human review, do not name an aggregate model winner,
and do not change the interactive default prompt unless the complete evidence
supports a separate explicit decision.
