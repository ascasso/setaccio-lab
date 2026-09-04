# Reasoning-default and execution-boundary diagnostic run

On 2026-09-04 the explicitly authorized protocol-v2 diagnostic ran once from
clean implementation commit `acc397990afb7d8376e6c2f3a5a22e765d306410`
against a loopback Ollama endpoint. Its pre-registered implementation and both
outcome branches are recorded in
[2026-09-04-reasoning-default-boundary-implementation.md](2026-09-04-reasoning-default-boundary-implementation.md).
This run did not read for reinterpretation, rerun, repair, replace, or rewrite
the 2026-09-03 diagnostic or any Phase 4 evidence.

## Retention gate

Before any model inspection or new evidence allocation, the version-aware
reader passed both required commands against
`local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content`:

```text
./gradlew :setaccio-lab:thinkingDiagnosticVerify \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content --offline
./gradlew :setaccio-lab:thinkingDiagnosticReanalyze \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content --offline
```

The retained raw artifact, deterministic summary, and manifest SHA-256 values
were identical before and after. In particular, `SUMMARY.md` remained
`0e88ad33e07c837808d532468226d5929cf87ac3f3cfeabcad68b2b0581f5c48`.
This was the agreed provider-free compatibility acceptance, not a new analysis
or interpretation of v1.

## Run identity

- Ollama runtime `0.33.3`.
- Subject `gemma4:e2b`, full digest
  `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`,
  advertised `thinking` during read-only preflight and both runner identity
  checks.
- Control `granite4.1:3b`, full digest
  `6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb`,
  did not advertise `thinking`.
- Prompt `local-fact-check` v1 and fixture catalog
  `local-fact-check-fixtures` v1 in tracked order.
- Both boundaries received the identical rendered prompt for each fixture.
- Temperature `0.0`, seed `42`, `64` output tokens, timeout `PT2M`, one attempt
  per row, pull strategy `never`.
- Evidence is private and ignored under
  `local/evidence/thinking-diagnostic/2026-09-04-reasoning-default-boundaries/`.

All 42 scheduled rows were retained. Every invocation completed; there was no
model-unavailable, timeout, provider-failure, retry, repair, replacement, or
omission. Both model identities and the clean Git baseline were re-checked after
execution and before evidence was written.

## Public-safe aggregates

Each count is over the six tracked fixtures. No response text, reasoning text,
evaluator output, or per-row payload is reproduced here.

| Boundary | Model role | Policy | Content | Reasoning | At budget | Evaluated tokens | Finish reasons |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fact-check | subject | `PROVIDER_DEFAULT` | 1/6 | 5/6 | 5/6 | 2–64 | `length` 5, `stop` 1 |
| fact-check | subject | `ENABLED` | 1/6 | 5/6 | 5/6 | 2–64 | `length` 5, `stop` 1 |
| fact-check | subject | `DISABLED` | 6/6 | 0/6 | 0/6 | 2 | `stop` 6 |
| chat | subject | `PROVIDER_DEFAULT` | 1/6 | 5/6 | 5/6 | 2–64 | `length` 5, `stop` 1 |
| chat | subject | `ENABLED` | 1/6 | 5/6 | 5/6 | 2–64 | `length` 5, `stop` 1 |
| chat | subject | `DISABLED` | 6/6 | 0/6 | 0/6 | 2 | `stop` 6 |
| chat | control | `PROVIDER_DEFAULT` | 6/6 | 0/6 | 0/6 | 2 | `stop` 6 |

The corresponding classified outcomes were five
`EMPTY_CONTENT_WITH_THINKING` plus one `CONTENT_WITHOUT_THINKING` for every
subject default or enabled arm, and six `CONTENT_WITHOUT_THINKING` for every
disabled subject arm and the provider-default control.

## Bounded observations

**Provider default reproduced the enabled pattern.** At the fact-check boundary,
the pre-registered `PROVIDER_DEFAULT` arm matched `ENABLED` on content presence,
reasoning presence, budget saturation, evaluated-token range, finish-reason
counts, and classified outcomes. This satisfies the pre-registered first result
branch: for this exact subject artifact, prompt, fixture catalog, seed, budget,
and runtime, the inherited-default reasoning mechanism is measured rather than
inferred only from Spring AI documentation.

**The same pattern crossed the chat invocation boundary.** Each subject policy
had identical aggregates at the fact-check and provider-neutral chat boundaries.
The chat default and enabled arms showed the same five reasoning-only,
full-budget, `length` outcomes, while chat disabled returned visible content in
all six rows. This measures the previously unexercised chat boundary under the
same rendered prompt and recorded settings.

**The non-thinking control remained distinct.** The control advertised no
thinking capability and, under provider default at the chat boundary, returned
content in all six rows with no reasoning, no budget saturation, and finish
reason `stop`.

These are aggregate response-shape observations, not semantic judgments.
Matching aggregates do not establish that every boundary transformation is
identical, and an advertised capability describes an artifact manifest rather
than runtime behavior.

## Implication for the owner, not a policy decision

The two gaps registered on 2026-09-03 are closed for this exact protocol:
provider default behaved like enabled for the subject, and the same mechanism
was observed through the chat invocation boundary. This strengthens the
mechanistic explanation for current inherited-default behavior, but it does not
prove the historical cause of any retained Phase 2, Phase 4, or Phase 5 row.

Whether a closed suite should replace `PROVIDER_DEFAULT` with an explicit policy
remains an owner decision and a separately gated protocol change. No adopt,
revise, or reject decision is made here, no closed phase is reopened, and no
retained evidence or closeout is changed.

Nothing generalizes beyond these two exact installed artifacts, this prompt,
this fixture catalog, this seed, the `64`-token budget, and Ollama `0.33.3`.
There is no answer-correctness, factuality, semantic-support, quality,
reliability, ranking, or model-selection claim.

## Offline verification

The new evidence passed both provider-free tasks:

```text
./gradlew :setaccio-lab:thinkingDiagnosticVerify \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-04-reasoning-default-boundaries --offline
./gradlew :setaccio-lab:thinkingDiagnosticReanalyze \
  --run-dir=local/evidence/thinking-diagnostic/2026-09-04-reasoning-default-boundaries --offline
```

The raw result, summary, and manifest hashes were unchanged across reanalysis;
the raw result SHA-256 is
`ef665d6f903e17330ea9d6a8c2090b3907a806f738f17478fc2d776abc399950`
and the deterministic summary SHA-256 is
`0947708dd6e49dc2a41b1ae7398b59fbdf492f68ae4fd3e4baf5d0002d1d8c70`.

The complete provider-free verification set also completed successfully before
the closeout commit:

```text
./gradlew :setaccio-lab:test --offline
./gradlew :setaccio-core:build :setaccio-lab:build --offline
./gradlew :setaccio-lab:chatMatrixTest :setaccio-lab:localEvaluationTest \
  :setaccio-lab:localEvaluationBudgetTest :setaccio-lab:retrievalFixtureTest \
  :setaccio-lab:thinkingDiagnosticTest :setaccio-lab:toolCompatibilityTest \
  :setaccio-lab:toolSearchSmokeTest :setaccio-lab:visionMatrixTest --offline
git diff --check
```

Raw assistant content, reasoning, evaluator output, and per-row payloads remain
in ignored durable evidence only. No raw artifact is published.

No model was pulled, renamed, or substituted. No remote provider, credential,
Docker runtime, release, tag, or push was used or authorized.
