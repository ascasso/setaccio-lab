# Phase 4 fact-check output-budget compatibility closeout

## Human outcome selection

On 2026-08-26, the project owner reviewed the verified paired aggregates and
selected **Outcome A** from the
[small-model tool-calling compatibility plan](../SmallModelToolCallingCompatibilityPlan.md):
the `256`-token arm produced substantially more valid verdicts than the `64`-token arm.

This records the owner decision for Phase 4. It does not select a judge,
establish general factuality or reliability, or authorize another model run.

## Verified paired evidence

- Both fresh arms ran from clean commit
  `b758dcb31d59bb0d49f73218e4a104f24550beaf`.
- The installed judge was `gemma4:e2b`, with digest
  `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`.
- Each arm retained the locked public-safe fact-check protocol: six fixtures,
  two sequential repetitions, temperature `0.0`, seeds `42` and `43`, a
  `PT2M` timeout, one attempt per row, loopback Ollama, and pull strategy
  `never`.
- The fresh arms differ only in maximum output tokens: `64` and `256`.
- Ignored saved evidence for both arms verified and deterministically
  reanalyzed offline. The F3 comparison also passed its strict shared-protocol
  gate before rendering any aggregate.
- The earlier A5 run remains contextual only; it is not an arm of this
  comparison.

## Outcome A observations

| Aggregate observation | 64 tokens | 256 tokens |
| --- | ---: | ---: |
| Valid normalized verdicts | 2/12 | 12/12 |
| Empty responses | 10/12 | 0/12 |
| Agreement among valid verdicts | 2/2 | 12/12 |
| Supported / `yes` verdicts | 0/12 | 6/12 |
| Unsupported / `no` verdicts | 2/12 | 6/12 |
| Consistent fixture repetitions | 1/6 | 6/6 |
| Incomplete fixture comparisons | 5/6 | 0/6 |

All ten empty `64`-token responses recorded `64` completion tokens. Every
`256`-token row recorded completion tokens below its configured maximum. The
saved contract does not capture a provider finish reason, so completion-token
counts are only an output-limit proxy.

Observed per-row median latency was `800.5 ms` in the `64`-token arm and
`2232.5 ms` in the `256`-token arm. These values describe this deployed
model, digest, runtime, and protocol; they are not a backend-normalized
performance comparison.

Within this exact controlled pair, the only material protocol change was the
maximum output-token budget, and the `256`-token arm retained more valid
verdicts. This supports the pre-registered output-budget compatibility
hypothesis for this deployment: the `64`-token configuration was associated
with lower retained verdict yield. It does not establish a general causal
mechanism or apply beyond this model, digest, prompt, fixture catalog, runtime,
and two repetitions.

## Limitations

- Two repetitions are not a reliability estimate.
- Expected-verdict agreement covers only the six repository-authored,
  human-confirmed fixture cases. It is not a general factuality result.
- No malformed-verdict, provider-failure, or timeout explanation is inferred
  from this pair beyond the retained classified outcomes.
- Raw responses and ignored evidence remain private local artifacts; this log
  reports aggregates only.
- No row was retried, replaced, repaired, or compared against historical A5
  evidence.

## Registered follow-up

Outcome A justifies a later, separately planned breakpoint study using the
pre-registered token levels `64`, `96`, `128`, `192`, and `256`. That study is
not authorized or started by this closeout. It requires a fresh protocol,
fresh ignored evidence, and explicit scope start; it must not reuse or alter
this pair.
