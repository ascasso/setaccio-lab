# Reasoning-default and execution-boundary diagnostic implementation

On 2026-09-04 the project owner explicitly started a new diagnostic slice to
measure the two limitations recorded by the 2026-09-03 reasoning diagnostic:
the retained suites used `PROVIDER_DEFAULT`, not `ENABLED`, and the prior live
diagnostic exercised only the fact-check recording boundary. This implementation
does not rerun, repair, replace, reanalyze, or reinterpret the earlier diagnostic
or any Phase 4 evidence.

## Evidence-compatibility decision

Current protocol v1 bound deserialization, analyzer rules, manifest settings,
manifest execution engine, and deterministic report rendering to current static
constants. Bumping the protocol directly would therefore strand the only saved
reasoning diagnostic. The owner agreed to preserve it.

The implementation adds an explicit v1/v2 wire codec and version-specific
validation:

- v1 reads and writes its original top-level, arm, and row JSON shapes;
- its original fact-check manifest engine, settings keys and ordering, derived
  enabled/disabled pairs, and `explicit-per-arm` source are reconstructed;
- its locked five-arm schedule, 30-row analyzer, and deterministic report format
  remain separate from v2;
- v2 requires its comparison settings and new arm/row boundary fields;
- `thinkingDiagnosticVerify` and `thinkingDiagnosticReanalyze` keep their CLI
  names and select the correct path from the raw `protocolVersion`.

The explicit compatibility acceptance after the implementation commit is to run
both offline tasks against
`local/evidence/thinking-diagnostic/2026-09-03-thinking-empty-content`. The
reanalyzer must regenerate the same summary bytes; no raw artifact or manifest
is rewritten.

## Pre-registered protocol v2

All arms use the tracked six-fixture catalog in its confirmed order. The arm
order is fixed before evidence allocation:

| Order | Arm | Model role | Boundary | Policy | Budget |
| --- | --- | --- | --- | --- | --- |
| 1 | `fact-check-subject-provider-default-64` | subject | fact-check evaluator recording | `PROVIDER_DEFAULT` | 64 |
| 2 | `fact-check-subject-enabled-64` | subject | fact-check evaluator recording | `ENABLED` | 64 |
| 3 | `fact-check-subject-disabled-64` | subject | fact-check evaluator recording | `DISABLED` | 64 |
| 4 | `chat-subject-provider-default-64` | subject | provider-neutral chat invocation | `PROVIDER_DEFAULT` | 64 |
| 5 | `chat-subject-enabled-64` | subject | provider-neutral chat invocation | `ENABLED` | 64 |
| 6 | `chat-subject-disabled-64` | subject | provider-neutral chat invocation | `DISABLED` | 64 |
| 7 | `chat-control-provider-default-64` | control | provider-neutral chat invocation | `PROVIDER_DEFAULT` | 64 |

This produces 42 sequential rows. Every row uses temperature `0.0`, seed `42`,
timeout `PT2M`, one logical attempt, and pull strategy `never`. No row may be
retried, repaired, replaced, or omitted. The subject is `gemma4:e2b` at the
required full digest `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`;
the control is `granite4.1:3b` at
`6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb`.
Both identities and the Git baseline must be checked before allocation and
again after execution. Neither model may be pulled, renamed, or substituted.

Every arm names a non-null policy. `PROVIDER_DEFAULT` is valid only when the arm
also marks it as an explicitly measured pre-registered condition. The manifest's
reasoning-policy source and all pairs are derived from the recorded arm list,
not injected from a mutable current pair constant. The policy comparisons within
each subject boundary are all three pairings among default, enabled, and
disabled. Matching default, enabled, and disabled subject arms are also paired
across the two boundaries.

## Prompt and row contract

The fact-check evaluator's tracked template is rendered once from the same
document and claim substitution rule and that identical rendered string is sent
through `OllamaChatInvocation`. The prompt is therefore held constant across
the boundary axis. Policy within a boundary is the primary controlled contrast;
the matching-policy boundary axis is controlled for prompt and recorded
settings and remains descriptive for runtime observations.

Every row records its execution boundary, requested policy, adapter support,
assistant content, separate reasoning field and presence, finish reason,
evaluated output tokens, classified outcome, latency, and attempt count. Chat
rows retain `documentBlake3`, `claimBlake3`, and `expectedVerdict` as fixture
input provenance. They intentionally record `normalizedJudgeVerdict` and
`expectedVerdictMatched` as null because no evaluator interprets that response.
An absent chat judge verdict is not a failure; outcome classification depends on
invocation success, content, and reasoning presence.

## Pre-registered interpretation

- If fact-check `PROVIDER_DEFAULT` shows empty content, populated reasoning, all
  64 evaluated tokens, and finish reason `length`, matching the enabled arm, the
  link from this mechanism to inherited-default suites becomes measured for this
  exact artifact, prompt, catalog, seed, and budget.
- If fact-check `PROVIDER_DEFAULT` instead matches the disabled arm, the mechanism
  does not explain the retained empty responses and the deferred-work limitation
  must be revised rather than confirmed.
- Chat observations are reported separately by policy and compared only within
  this suite. No result decides a policy for a closed suite.

## Provider-free verification

Before the implementation commit, the required provider-free acceptance
completed successfully:

```text
./gradlew :setaccio-lab:test --offline
./gradlew :setaccio-core:build :setaccio-lab:build --offline
./gradlew :setaccio-lab:chatMatrixTest :setaccio-lab:localEvaluationTest \
  :setaccio-lab:localEvaluationBudgetTest :setaccio-lab:retrievalFixtureTest \
  :setaccio-lab:thinkingDiagnosticTest :setaccio-lab:toolCompatibilityTest \
  :setaccio-lab:toolSearchSmokeTest :setaccio-lab:visionMatrixTest --offline
git diff --check
```

The tests cover a registered provider-default arm, rejection of an implicit or
unregistered default, per-arm and per-row boundaries, identical rendered prompt
delivery, judge-free chat rows, v1 offline round-trip compatibility, v2 offline
integrity, failure retention, and default-lifecycle isolation from Ollama.

No live model call or new evidence belongs to the implementation commit. The
new controlled run starts only from that clean commit after v1 compatibility
acceptance succeeds. No remote provider, credential, Docker use, model pull,
release, tag, push, or ignored raw-evidence publication is authorized.
