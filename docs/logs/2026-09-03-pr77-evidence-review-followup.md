# PR 77 evidence-review follow-up

On 2026-09-03 the project owner asked for an assessment of the review findings
on PR 77 and authorized repairs that proved valid on the current branch.

## Findings accepted

All four review findings were valid.

- Legacy `build/<suite>/<run-id>` paths were accepted by every offline runner.
  That is correct for verification, comparison, and source-evidence consumption,
  but incorrect for reanalysis because reanalysis atomically replaces the
  derived `SUMMARY.md`.
- The thinking-diagnostic analyzer validated its schedule, arms, catalog
  envelope, and selected row fields, but did not lock all retained protocol and
  prompt identity fields to the tracked contract.
- It did not recompute each retained document and claim BLAKE3 identity from
  the tracked fact-check fixture catalog.
- It counted retained row outcomes without first proving that an outcome agreed
  with the recorded invocation, content, thinking-presence, and one-attempt
  fields.

## Repair

`EvidenceSuiteRoot` now exposes a durable-only saved-run resolver for operations
that rewrite derived artifacts. Every offline reanalyzer entry point uses it;
their verifier paths continue to use the read-only resolver, preserving legacy
verification, comparison, and source-evidence use. A legacy reanalysis fails
before a suite reads or writes its artifacts.

Thinking-diagnostic analysis now requires the locked provider, endpoint,
execution strategy, no-pull strategy, temperature, seed, one-attempt policy,
timeout, and tracked prompt identity. It recomputes BLAKE3 identities for every
fixture document and claim and validates that each completed outcome follows
from retained content and thinking presence. Failed rows must retain a known
failure outcome and no normalized verdict or expectation-match field.

The persisted row schema deliberately does not retain the lower-level failure
subtype that produced a failed invocation. Offline analysis can therefore
reject an unclassified or internally contradictory failure but cannot recreate
whether a coherent saved failure was originally model-unavailable, timed out,
or provider-failed. This repair does not claim otherwise and does not alter
existing raw evidence.

## Verification and boundaries

The added provider-free tests cover legacy reanalysis refusal, top-level
protocol/prompt drift, fixture-hash drift, attempt drift, and outcome drift.
No Ollama, Anthropic, or other provider was contacted. No formal evidence was
allocated, read, reanalyzed, repaired, replaced, or published. No model pull,
remote provider, credential, Docker use, release, tag, or push is authorized by
this repair.
