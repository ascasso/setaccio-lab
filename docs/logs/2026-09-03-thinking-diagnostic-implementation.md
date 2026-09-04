# Reasoning diagnostic implementation

On 2026-09-03 the project owner explicitly started a slice to diagnose the
cross-surface empty-response observation. This record covers the provider-free
implementation half. The source-inspection half is recorded separately in
[2026-09-03-thinking-field-inspection.md](2026-09-03-thinking-field-inspection.md)
and stands whether or not any later run succeeds.

No model was invoked. No evidence directory was allocated. No retained evidence
was read, reinterpreted, rerun, repaired, replaced, reanalyzed, or mutated.

## Decisions raised to the owner before implementation

Three related decisions were put to the owner with their measured impact,
because they were not the agent's to make. The measurements came from reading
the existing verifiers, not from assumption:

- serialized rows are per-suite records that project from the in-memory
  outcome, so adding fields to `ChatInvocationOutcome` alone changes no saved
  evidence;
- raw JSON is read with `FAIL_ON_UNKNOWN_PROPERTIES` enabled, but old JSON
  merely omits new fields, so additive nullable row fields still deserialize;
- `SUMMARY.md` is regenerated and compared byte-for-byte, so any report change
  fails every retained run of that suite;
- manifest settings are compared as an exact JSON string, so adding one key
  fails every retained manifest of that suite — the hazard `AGENTS.md` already
  records for the Tool Search tool-call limits;
- adding a constant to `ChatGenerationOption` would make retained chat and
  answer raw JSON **undeserializable**, because `ChatProviderOptionSupport`
  requires every constant to be classified exactly once and old JSON classifies
  a new one as neither. This one is not fixable by nullability.

The owner selected: new fields serialized only in the new diagnostic suite; the
reasoning policy recorded as a protocol setting of that new protocol only; and
the capture implemented in both shared boundaries rather than duplicated.

## What was implemented

**Shared, provider-neutral.** `ChatReasoningPolicy` has `ENABLED`, `DISABLED`,
and `PROVIDER_DEFAULT`. `PROVIDER_DEFAULT` sends nothing, which is deliberately
not the same as `DISABLED`, because Spring AI documents that a thinking-capable
model auto-enables thinking when the option is unset.
`ChatThinkingPresence` distinguishes `PRESENT`, `ABSENT`, and `UNAVAILABLE`;
`ChatReasoningSupport` distinguishes `APPLIED`, `NOT_REQUESTED`, and
`UNSUPPORTED`.

`ChatResponseCapture` is the single extraction point for both boundaries. It
records assistant content, any reasoning field, reasoning presence, the
generation finish reason, the evaluated output-token count, the requested
policy, and the adapter's handling of it. Its invariants keep those states
consistent, and content and reasoning are separate values that are never
concatenated, substituted, or merged.

`OllamaReasoningOptions` maps the policy onto Spring AI's `ThinkOption`. That
Spring-specific type appears in the Ollama adapter only; the provider-neutral
chat contract does not mention it.

**Both boundaries.** `OllamaChatInvocation` — used by the chat matrix and the
Phase 5 answer matrix — and `LocalFactCheckJudgeBoundary`'s `RecordingChatModel`
— which does **not** go through the chat invocation boundary, so instrumenting
only the former would have missed the fact-check path entirely — now both take
an explicit policy, apply it to the options they actually send, and build the
same capture from the response before anything downstream consumes it. Both
default to `PROVIDER_DEFAULT`, preserving existing behavior exactly.

**Backward compatibility, by construction.** `ChatInvocationOutcome` and
`LocalFactCheckJudgeResult` each gained one trailing nullable capture component
plus an overload preserving the previous argument order. Their consuming row
records — `ChatMatrixRow`, the answer row, `LocalEvaluationRow` — were not
changed, so no existing suite's serialized schema, manifest settings, protocol
version, or regenerated summary moved. `ChatGenerationOption` was deliberately
left alone.

**The new suite.** `thinkingDiagnostic`, `thinkingDiagnosticVerify`, and
`thinkingDiagnosticReanalyze` write and inspect a new
`ollama-thinking-diagnostic` suite under
`local/evidence/thinking-diagnostic/`. Its protocol locks five arms in a fixed
order, the tracked public-safe fact-check fixture catalog in its confirmed
order, temperature `0.0`, seed `42`, `PT2M`, one attempt per row, pull strategy
`never`, and 30 retained rows. Each paired subject arm holds prompt, fixture
order, seed, temperature, timeout, and every non-reasoning setting constant, so
the only difference inside a pair is the reasoning policy.

Reusing the fact-check catalog and prompt is deliberate: it keeps the
diagnostic directly comparable to the retained A5 observation. It is a new
diagnostic protocol with its own suite, schema, row shape, and manifest
settings. It is not a rerun, repair, replacement, or reanalysis of Phase 4
evidence, and it never writes into the Phase 4 suite.

`ThinkingDiagnosticOutcome` separates `EMPTY_CONTENT_WITH_THINKING` from
`EMPTY_CONTENT_WITHOUT_THINKING` — the distinction no existing suite can make.

## Scope choice worth stating plainly

The live schedule runs through the fact-check judge boundary only. That is the
path with the strongest retained signal (ten of twelve empty responses at `64`
tokens), it exercises `RecordingChatModel` end to end, and it reuses tracked
reviewed inputs with no new prompt to author. The chat invocation boundary is
covered by provider-free tests in this slice, not by a live arm. Whether the
chat-matrix and Phase 5 answer empties share the same mechanism is therefore
consistent-but-untested by this diagnostic, and the closeout must say so.

## Verification

Provider-free throughout. No Ollama, Anthropic, or other provider was
contacted.

```
./gradlew :setaccio-core:build :setaccio-lab:build --offline
./gradlew :setaccio-lab:retrievalFixtureTest :setaccio-lab:chatMatrixTest \
  :setaccio-lab:localEvaluationTest :setaccio-lab:localEvaluationBudgetTest \
  :setaccio-lab:toolCompatibilityTest :setaccio-lab:toolSearchSmokeTest \
  :setaccio-lab:visionMatrixTest :setaccio-lab:thinkingDiagnosticTest --offline
```

All tasks passed. `./gradlew :setaccio-lab:build --dry-run` reaches no
diagnostic task, so the suite stays outside the default lifecycle.
`git diff --check` reported no whitespace errors.

New provider-free coverage: reasoning present with content, present with empty
content, absent, and blank-treated-as-absent; finish-reason and
evaluated-output-token capture; unavailable capture on failure; strict
content/reasoning separation; explicit enabled, explicit disabled, and
send-nothing `ThinkOption` mapping; the fact-check `RecordingChatModel` path
including policy propagation and the unchanged no-policy default; the protocol
lock and paired-arm invariance; all four outcome combinations and retention of
failed rows; the evidence round trip with tamper, drift, extra-artifact,
incomplete-run, and schedule-drift rejection; and evidence compatibility for
the chat and fact-check suites that were deliberately left alone.

## What this does not claim

No run has happened. This slice produces no evidence about any model's
behavior, confirms no mechanism, and explains nothing about any retained
observation. It closes a recording gap and pre-registers a protocol.

The Phase 4 output-budget curve remains a valid observation of visible-verdict
yield under maximum output-token budgets. No closeout is withdrawn or
reinterpreted, and no rerun of any retained evidence is authorized.
