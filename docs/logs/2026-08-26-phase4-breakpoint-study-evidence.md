# Phase 4 output-budget breakpoint-study evidence

## Execution and verification

On 2026-08-26, the explicitly started five-arm Phase 4 breakpoint study ran
once from clean commit `0d4e00991a2bb6b432b3afcd4a2b3ec4978fbd0b` with the
already-installed local judge `gemma4:e2b` and full digest
`7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`.

Each of the `64`, `96`, `128`, `192`, and `256` maximum-output-token arms
executed the locked six-fixture, two-repetition sequential schedule: 12 rows
per arm and 60 retained rows total. Every arm used temperature `0.0`, seeds
`42`/`43`, timeout `PT2M`, one attempt per row, loopback Ollama, and pull
strategy `never`.

The five new ignored arm directories verified immediately after execution and
again through standalone offline verification and deterministic reanalysis.
The deterministic five-way aggregate report also passed its strict non-budget
parity gate. No row was retried, replaced, or repaired.

## Observed aggregates

| Aggregate observation | 64 | 96 | 128 | 192 | 256 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Valid normalized verdicts | 2/12 | 2/12 | 2/12 | 6/12 | 12/12 |
| Empty responses | 10/12 | 10/12 | 10/12 | 6/12 | 0/12 |
| Agreement among valid verdicts | 2/2 | 2/2 | 2/2 | 6/6 | 12/12 |
| Consistent fixture repetitions | 1/6 | 1/6 | 1/6 | 3/6 | 6/6 |
| Rows at configured token maximum | 10/12 | 10/12 | 10/12 | 6/12 | 0/12 |

All saved rows retained completion-token metadata. Counts at the configured
maximum are an output-limit proxy only: this evidence does not record a
provider finish reason.

## Boundary and owner decision

This is a descriptive result for the exact judge digest, runtime, prompt,
fixtures, settings, and two repetitions. It does not select a judge, measure
general factuality or reliability, establish a causal mechanism, or provide a
backend-normalized latency comparison. The raw ignored evidence remains local.

No human interpretation decision is recorded by this closeout. The project
owner may review the verified aggregate report and record a bounded conclusion
separately; no arm may be rerun, replaced, repaired, or altered for that
decision.
