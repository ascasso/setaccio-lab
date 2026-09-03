# Reasoning diagnostic run and closeout

On 2026-09-03 the authorized reasoning diagnostic executed once, from the clean
implementation commit `4e766b7a6345ba1a8af9ee1e354c2ba027e1573a`, against a
loopback Ollama endpoint. This is the closeout for that run. The protocol was
locked in code before the run; the implementation record is
[2026-09-03-thinking-diagnostic-implementation.md](2026-09-03-thinking-diagnostic-implementation.md)
and the source inspection is
[2026-09-03-thinking-field-inspection.md](2026-09-03-thinking-field-inspection.md).

## Run identity

- Ollama runtime `0.33.3`. The 2026-09-02 capability observations were read
  under `0.33.2`; both capability strings were re-checked read-only under
  `0.33.3` before the run and were unchanged.
- Subject `gemma4:e2b`, full digest
  `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`, advertises
  `thinking`. This is the same artifact and digest recorded in the Phase 2 chat,
  fact-check A5, and Phase 5 R5 closeouts.
- Control `granite4.1:3b`, full digest
  `6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb`, does not
  advertise `thinking`. Same artifact and digest as the Phase 5 R6 closeout.
- Prompt `local-fact-check` v1, fixture catalog `local-fact-check-fixtures` v1,
  in tracked catalog order.
- Temperature `0.0`, seed `42`, timeout `PT2M`, one attempt per row, pull
  strategy `never`, 30 rows retained.
- Evidence is ignored and durable, under
  `local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content/`.

All 30 planned rows executed. None timed out, none failed, none was retried,
repaired, replaced, or omitted. Both model identities and the Git baseline were
re-checked after execution and before evidence was written.

## Observations

Every count below is a per-arm aggregate over six fixtures.

| Arm | Policy | Budget | Rows with content | Rows with reasoning | Rows at budget | Evaluated tokens | Finish reasons |
| --- | --- | --- | --- | --- | --- | --- | --- |
| subject | `ENABLED` | 64 | 1 | 5 | 5 | 2, 64 x5 | `length` 5, `stop` 1 |
| subject | `DISABLED` | 64 | 6 | 0 | 0 | 2 x6 | `stop` 6 |
| subject | `ENABLED` | 256 | 6 | 5 | 0 | 2, 157, 165, 194, 203, 208 | `stop` 6 |
| subject | `DISABLED` | 256 | 6 | 0 | 0 | 2 x6 | `stop` 6 |
| control | `DISABLED` | 64 | 6 | 0 | 0 | 2 x6 | `stop` 6 |

Classified outcomes:

- subject `ENABLED` 64: `EMPTY_CONTENT_WITH_THINKING` 5, `CONTENT_WITHOUT_THINKING` 1
- subject `ENABLED` 256: `CONTENT_WITH_THINKING` 5, `CONTENT_WITHOUT_THINKING` 1
- subject `DISABLED` 64, subject `DISABLED` 256, control: `CONTENT_WITHOUT_THINKING` 6 each

Three things follow directly from those numbers.

**The empty-content shape is reproducible and is accompanied by reasoning.** In
the subject arm with reasoning explicitly enabled at `64` tokens, five of six
rows returned empty assistant content, a populated reasoning field, exactly `64`
evaluated output tokens — the whole budget — and finish reason `length`. The lab
would previously have recorded each of those as an empty response and discarded
the reasoning.

**The same artifact at the same budget behaves differently when reasoning is
explicitly disabled.** The paired arm held prompt, fixture order, seed,
temperature, timeout, and budget constant and changed only the reasoning policy.
All six rows returned visible content in two evaluated tokens with finish reason
`stop`, and none reached the budget.

**Raising the budget with reasoning still enabled removed the empty content.**
At `256` tokens the same five rows produced reasoning of 157 to 208 evaluated
tokens plus visible content, and every row finished with `stop` rather than
`length`.

Two secondary observations, both narrow. One fixture returned no reasoning field
in either enabled arm and behaved exactly like the disabled arms, so an
explicitly enabled policy does not guarantee reasoning on every row. And every
visible verdict produced across all five arms — 25 of 30 rows — matched its
expected verdict; that is an observation under one fixed prompt, catalog, seed,
and six fixtures, not a factuality, quality, or reliability claim.

## What this establishes, and what it does not

Established, for this artifact, prompt, catalog, seed, and these budgets: when
reasoning is **explicitly enabled**, a small output budget can be consumed
entirely by reasoning before any assistant content is produced, and the response
then reaches the framework as empty content with finish reason `length`. This is
a mechanism consistent with, and explanatory of, the Phase 4 output-budget
association: it offers a reason why visible-verdict yield rose with budget.

The Phase 4 curve stands exactly as recorded. It was a valid observation of
visible-verdict yield under maximum output-token budgets, it was correctly
bounded when written, and nothing here makes it false or shows that it measured
the wrong thing. This diagnostic explains a plausible cause of the shape that
study observed; it does not replace, rerun, repair, or reanalyze it.

Not established, and important:

- **The retained runs used no policy at all, not `ENABLED`.** Phase 2 chat, A5,
  and R5 sent `PROVIDER_DEFAULT`. This diagnostic contains no
  `PROVIDER_DEFAULT` arm, because the pre-registered protocol did not include
  one. That an unset policy behaves like an enabled one for a thinking-capable
  artifact is Spring AI's documented behavior, not something measured here.
- **Only one boundary was exercised live.** The schedule ran through the
  fact-check judge boundary. Whether the Phase 2 chat and Phase 5 R5 empty
  responses share this mechanism is consistent with these results but untested
  by them; those paths use the chat invocation boundary, which this slice covers
  with provider-free tests only.
- Nothing here generalizes beyond these two artifacts, this prompt, this fixture
  catalog, this seed, and these two budgets. It is not a quality, factuality,
  reliability, ranking, or model-selection claim, and an advertised capability
  describes an artifact manifest rather than runtime behavior.

## Implication for the owner, not a decision

Two questions are now worth an explicit decision, and neither is made here.

First, whether the suites that still send no reasoning policy should keep doing
so. Making it explicit would change their protocol identity and, because their
verifiers compare manifest settings as an exact JSON string and regenerate
`SUMMARY.md` byte-for-byte, would stop every retained manifest in those suites
from verifying. That trade-off is recorded in
[DEFERRED-WORK.md](../DEFERRED-WORK.md).

Second, whether a `PROVIDER_DEFAULT` arm and a chat-boundary arm are worth a
follow-up diagnostic to close the two gaps named above.

No adopt, revise, or reject decision is made for the closed Phase 4 cycle, and
no rerun of any retained evidence is authorized by this record.

## Verification

```
./gradlew :setaccio-lab:thinkingDiagnosticVerify \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content
./gradlew :setaccio-lab:thinkingDiagnosticReanalyze \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content
```

Both completed with no failure: the manifest, raw artifact size and SHA-256, and
the deterministic summary all verified, and reanalysis regenerated a
byte-identical `SUMMARY.md`.

Recorded assistant content, reasoning text, and per-row payloads remain in the
ignored raw artifact only. No raw output is published in tracked files.
