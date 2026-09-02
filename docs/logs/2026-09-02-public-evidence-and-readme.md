# Public evidence example and README restructure

On 2026-09-02, the project owner authorized a documentation-only publication
cycle: surface the results the project has actually produced, and track one
worked example of the evidence format. No experiment was run, repeated, or
reinterpreted.

## Authorization boundary

The owner selected, explicitly:

- publish what already exists rather than start new experimental scope;
- track deterministic summaries and manifests only, never raw output;
- limit this cycle to the README and the evidence example, leaving the ignored
  local blog drafts under `docs/blog/` unpublished;
- leave the existing evidence-availability statements in the protocol plan,
  deferred-work index, and `AGENTS.md` as written.

This moves the publication boundary that
[DEFERRED-WORK.md](../DEFERRED-WORK.md) gates. It authorizes nothing else: no
rerun, repair, replacement, reanalysis, evidence mutation, model pull, remote
provider, credential, Docker use, spending, release, tag, or push.

## Tracked evidence example

`docs/evidence/2026-09-02-r4-qwen3-embedding-0-6b/` contains byte-identical
copies of two artifacts from the ignored Phase 5 R4 run directory
`setaccio-lab/build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b/`:

| Artifact | Role | SHA-256 |
| --- | --- | --- |
| `SUMMARY.md` | summary | `d47408f5139c0183e536b58b761d8c7d4e79b918af0c7c54e32e599f602f7662` |
| `manifest.json` | manifest envelope | `9f3e391e3fc13cdfcd4f93540fed150fc65104c24dda50b9ce35f1a9e18fa769` |

The summary hash matches the value the manifest declares for it. The raw
artifact `retrieval-embedding-results.json`
(`aa56b1add65bdd9506676170f33df3fedd580f6d16b9dc48b9a80b04362b716a`, 379792
bytes) was deliberately **not** copied; raw vectors, model output, and per-row
payloads remain ignored.

The source directory was read only. Both source files were re-hashed after the
copy and are unchanged.

Because the published copy omits an artifact its manifest declares,
`retrievalEmbeddingVerify` does not pass against it. That is intended and is
documented in [`docs/evidence/README.md`](../evidence/README.md): verification
checks a complete saved run, and a publication copy is partial by construction.

## README restructure

`README.md` went from 391 to 214 lines and now leads with results rather than
with a slice-by-slice capability narration.

- New `## Findings` section states the two surviving results — the Phase 4
  output-budget yield curve and the T3.6 single all-pass qualifier — with their
  original closeout qualifications carried over rather than paraphrased.
- The same section records the cross-phase empty-response and first-turn
  `PROVIDER_FAILURE` observation explicitly as an open question with no
  closeout, no controlled protocol, and no interpretation of its own.
- New `## How evidence works` section promotes the evidence lifecycle out of
  the capability wall and links the published example.
- `## Current capabilities` is compressed to a surface/endpoint/task table.
- The previous 291-line capability narration moved unchanged in substance to
  [`docs/CAPABILITIES.md`](../CAPABILITIES.md). Two mechanical edits were
  applied: `###` headings promoted to `##` under a new H1, and repo-root
  relative links rewritten for the new `docs/` location. No sentence, figure,
  or closeout qualification was altered.

No Java, Gradle, test, fixture, or evidence file was modified.

## Evidence-availability observation

Recorded here as an observation only; per the authorization above, no
availability claim elsewhere in the documentation was changed.

At the time of this change, the only formal run evidence present under
`setaccio-lab/build/` is the Phase 5 R4 embedding run. Evidence for Phase 1,
Phase 2, Phase 3, the Phase 4 five-arm breakpoint study, Phase 5 R3/R5/R6, the
fact-check A5 run, the chat matrix, the vision matrices, and the Anthropic O3
portability run is not present on this host. Those directories are ignored and
live under `build/`, which ordinary Gradle cleaning removes.

The protocol plan, deferred-work index, and `AGENTS.md` currently state that
retained evidence for several of those phases verifies offline. That statement
described the state at each closeout and was true when written; it is not
currently checkable on this host. The findings and interpretations those
closeouts recorded are unaffected, because each was bounded and recorded in
public-safe documentation at the time.

This is the second occurrence of ignored-evidence loss in the project. The
first was the vision Prompt v1/v2 pair, closed on 2026-08-02 through a
documented evidence-loss waiver. No waiver is claimed here and no claim is
withdrawn; a retention decision remains available as separately authorized
future work.
