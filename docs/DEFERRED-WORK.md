# Deferred Work

Status: current after the authorized Phase 3 small-model cohort execution and
bounded T3.4 interpretation on 2026-08-24.

This is the tracked index for completed boundaries and work intentionally
outside the completed August scope. It distinguishes completed, deferred,
failed, and partially completed work. No listing here authorizes a live model
call, Docker use, a credential, spending, a release, or a tag.

## Completed Boundary

The local fact-check cycle is complete: its versioned prompt and
human-confirmed fixture contract, dedicated recording judge boundary, offline
evidence lifecycle, one controlled host-Ollama run, and bounded A6
interpretation are recorded in
[LOCAL-AI-EVALUATION-PLAN.md](LOCAL-AI-EVALUATION-PLAN.md). The saved raw A5
evidence remains ignored.

## Completed Phase 2 Boundary

On 2026-08-04, the project owner explicitly selected the existing public-safe
chat surface for the Phase 2 reuse proof and later portability proof. Slice S1
completed on `feature/chat-evidence-reuse`, based on `develop` commit `2b47c07`.
It extracted shared saved-evidence file operations across vision, Tool Search,
and local evaluation without merging their suite-specific schemas or changing
saved formats. Slice S2 then added the minimal provider-neutral chat invocation
contract and its only Phase 2 adapter, Ollama. The contract records common
model, prompt, generation, option-support, response, usage, latency, attempt,
and failure fields while keeping the full digest specific to Ollama. Model
construction remains loopback, no-pull, and one-attempt. The existing
interactive endpoint is unchanged. Slice S3 has a dedicated implementation:
one tracked versioned three-prompt catalog, a sequential six-row Ollama runner,
shared-v1 evidence, deterministic analysis, and standalone offline
verify/reanalyze tasks. On 2026-08-05, one clean-baseline local run from
commit `51025cf` used `gemma4:e2b` with its full digest, `128` output tokens,
`PT2M`, and the locked six-row one-attempt schedule. All six invocations
completed with complete usage metadata and empty responses; no model,
timeout, or provider failure occurred. The ignored saved evidence verified and
reanalyzed offline, and the preserved Phase 1 evidence still verifies. This
closes Phase 2 as a contract-reuse proof, not a quality, reliability, or model
ranking conclusion.

Phase 2 extracted only proven shared invocation/evidence contracts, added one
minimal provider-neutral chat boundary with an Ollama adapter, and executed one
dedicated sequential six-row chat matrix. It remained local and no-pull, with
all default tests offline; the existing chat endpoint remains unchanged because
no parity-tested migration was requested. The matrix remains an explicit
opt-in task outside `test`, `check`, `build`, application startup, and CI.

Phase 2 closure does not authorize Anthropic credentials or calls, another
provider or benchmark surface, an automatic model pull, Docker, spending, a
release, a tag, or a push.

## Completed Phase 1 Small-Model Tool-Compatibility Boundary

The T0.1 documentation packet and provider-free T1.1-T1.8 implementation for
the tracked [Small-Model Tool-Calling Compatibility
Plan](SmallModelToolCallingCompatibilityPlan.md) are complete. Phase 1 locks
one untreated already-installed LFM2.5 model, the standard
`ToolCallingAdvisor`, all eight ordered canonical public-safe cases, two
repetitions with seeds `42`/`43`, temperature `0.0`, `512` output tokens per
provider turn, one `PT2M` whole-row deadline, one logical attempt with ordered
provider turns, no pull, a 16-row sequential schedule, the exact
`tool-case-oracle` call/argument contract, suite-specific evidence, and
standalone offline verification/reanalysis.

The clean baseline run completed on 2026-08-20 from commit `62181fb` with
requested and effective model
`hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0` and full digest
`2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed`. Its
ignored run directory is
`setaccio-lab/build/tool-compatibility/2026-08-20-lfm-baseline/`. All 16
planned logical row attempts were executed without a timeout or retry. No
logical row attempt completed successfully. Each first provider turn was
classified `PROVIDER_FAILURE`; no tool call, callback, final response, usage,
output-limit, or visible-reasoning evidence was observed. The raw evidence
verified and deterministic reanalysis reproduced `SUMMARY.md`.

This closes Phase 1 as a bounded provider-turn compatibility observation. It
does not establish tool-calling quality, reliability, production suitability,
or a model ranking, and the safe evidence does not identify the underlying
provider failure cause.

Phase 1 is closed. The dedicated source sets and tasks remain provider-free by
default, and the saved live evidence remains ignored. Phase 2 execution and
deterministic closeout are also complete: both conditions were verified,
reanalyzed, and strictly compared from one interleaved run. Every row reached
the same first `PROVIDER_FAILURE` boundary, so no prompt-effect conclusion is
claimed. On 2026-08-23, the owner completed the ignored T2.5 worksheet with an
`inconclusive` decision bound to the preserved runs and comparison. This
selects untreated cohort operation with the limitation recorded and authorizes
no prompt-effect claim. The owner also separately authorized
dependency-independent Phase 3 provider-free foundations and read-only
installed-model inspection. Exact identity lock, model invocation, and live
execution remain deferred until owner identity approval and the remaining live
runner gate are complete.

## Deferred From the August Cycle

| Item | Status and reason | Required gate before work begins |
| --- | --- | --- |
| Prompt v2 decision | Deferred. The historical paired evidence is unavailable, so no actual-human `adopt`, `revise`, or `reject` decision is claimed. Prompt v1 remains the operational default and Prompt v2 remains experimental. | Separately authorize a new paired protocol with new run names, preserved evidence, and actual human review. Do not treat agent-assisted inspection as that review. |
| Phase 2 human interpretation | Completed on 2026-08-23. The owner recorded `inconclusive` in the ignored T2.5 worksheet and bound it to both run identities, the prompt catalog, comparison report, and actual review date. The executable cohort policy is untreated operation with the limitation recorded; no prompt-effect claim is authorized. | No remaining Phase 2 gate. Preserve the binding and keep private raw evidence ignored. |
| Small-model compatibility cohort through T3.6 | Completed through the authorized clean-baseline run from commit `e897edf` on 2026-08-24, bounded T3.4 interpretation, provider-free T3.5 comparison, and provider-free T3.6 frontier analysis on 2026-08-25. The owner-approved five peers plus separately labelled `qwen3.8:27b-mlx` reference ran once under Ollama `0.32.15`, the untreated policy, and the locked 96-row sequential protocol. All rows remain in ignored verified evidence. T3.5 found reference-only pass counts of `16`, `2`, `4`, `2`, and `2` in peer order, with no peer-only or neither-pass rows. T3.6 found exactly one all-pass qualifier: the separately labelled reference passed `16/16` at a recorded installed-artifact size of `18174721847` bytes. The narrow frontier is measurable only among these tested installed artifacts under this exact protocol. It is not a rank, correctness oracle, selection, general-capability, semantic-correctness, backend-normalized performance, or general smallest-capable result. | Preserve the immutable evidence. Any new cohort analysis rule or run requires separate explicit authorization; do not rerun, repair, replace, pull, substitute, or customize a model. |
| Fact-check output-budget compatibility | F1 implementation is complete as a provider-free boundary: the dedicated paired task fixes fresh `64` and `256` arms, the original fact-check identities, `PT2M`, one attempt, no pull, and same-clean-commit checks. No formal arm was executed in this slice, and A5 remains contextual evidence only. F2 paired evidence, F3 offline comparison, and F4 interpretation remain deferred. | Begin F2 with a separately authorized formal run only after reviewing the dedicated F1 task. Use two new ignored evidence directories from one clean Git commit; never reuse A5, salvage a changed-baseline arm, selectively retry a row, or replace evidence. |
| Testcontainers fact-check path | Deferred. The host-Ollama runner, provenance, and offline verification worked; container provisioning would not answer the observed verdict-yield question. | Establish a distinct service-connection or provisioning question. Keep any typed Ollama dependency, container task, and Docker behavior in `setaccio-testcontainers`, opt-in, and outside normal `test`, `build`, and CI. |
| Relevancy evaluation and retrieval | Deferred until R0 is explicitly started. Ordinary fixture context is not a retrieval flow. Standing Phase 4/5 authorization permits liberal use of already-installed loopback Ollama models during requested Phase 5 work, without another call or run approval. | Add a real retrieval path that preserves retrieved documents. R0–R3 formal tasks and evidence remain provider-free by methodology; R4–R6 may use recorded local Ollama embedding, answer, and evaluator identities. Pulls, remote providers, credentials, and Docker remain separately gated. |
| Release, tag, and promotion | Deferred. The August feature branch is not a release decision. | Use a separately authorized promotion/release workflow after the appropriate integration review; decide version, changelog release entry, and tag together. |

## Completed Phase 3 Boundary

Phase 3 completed on `feature/anthropic-portability` with one bounded
architecture-portability proof behind the Phase 2 chat contract. Slice O1 added
the provider-free Anthropic adapter; Slice O2 added the raw-output-free evidence
projection; Slice O3 added and executed the opt-in runner. The selected model
was the pinned hosted ID `claude-haiku-4-5-20251001`; no fabricated local digest
is claimed. Temperature and maximum output tokens were supported directly,
timeout was translated to the SDK client timeout, exactly one attempt was
translated to SDK `maxRetries=0`, seed was rejected as unsupported and never
simulated, and no common option was silently ignored.

Because the earlier ignored Phase 2 run was absent, the owner separately
authorized one replacement local baseline. From clean commit `215ea18`, the
installed `gemma4:e2b` digest
`7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`
completed the locked six-row, `128`-token, `PT2M`, one-attempt, no-pull schedule;
all six rows retained usage metadata and empty responses. That ignored evidence
verified and reanalyzed offline.

Immediately before O3, official Claude API pricing was rechecked at `$1` per
million input tokens and `$5` per million output tokens. The locked ceiling of
`1,536` input and `768` output tokens produced a `$0.005376` worst-case estimate.
The owner authorized up to `$5`; the task used a stricter `$3` ceiling. From
clean commit `3810a19`, all six sequential Anthropic calls completed with
non-empty outputs and complete usage metadata, with no retry or replacement.
Usage-derived cost was `$0.001870`. Ignored evidence under
`build/anthropic-chat-matrix/2026-08-05-claude-haiku-4-5-o3/` verifies and
reanalyzes offline.

The prompt inputs, common invocation settings, and framework versions matched,
so the architecture-portability contract is compatible. The unseeded Anthropic
repetitions are not protocol-identical to Ollama's seeded repetitions, and a
hosted provider ID has different reproducibility semantics from an immutable
local digest. No answer-quality, performance, statistical reliability, model
ranking, endpoint migration, or future remote-call authorization is claimed.

## Deferred Through the Current Roadmap

The following do not enter the current three-phase path unless the plan is
explicitly revised:

- additional remote providers beyond the one authorized Anthropic proof;
- additional model types such as embeddings, image generation, audio
  transcription, text to speech, and moderation;
- MCP, RAG, vector stores, and other retrieval infrastructure;
- new Tool Search indexes or a broad tool-calling expansion beyond the prepared
  narrow standard-advisor compatibility study;
- container runtime work other than a separately justified
  `setaccio-testcontainers` slice;
- automatic model pulls, default-lifecycle live provider calls, or publication
  of raw ignored evidence.

Before any expansion, add provider-free tests for the new boundary, preserve
the public/private split, and update the affected environment, test-plan,
changelog, and dated-log documentation in the same change.

## Documentation Follow-up

`docs/ENVIRONMENT.md` lists provider variables and model-type names as future
integration inputs only; their presence does not enable a provider or grant
authorization to use credentials. The detailed local Ollama setup guide is
also deferred until a dedicated documentation slice can provide tested setup,
manual model-installation, and opt-in live-run instructions without weakening
the no-pull default.

## Related Documents

- [Local AI-Judged Evaluation Plan](LOCAL-AI-EVALUATION-PLAN.md): completed
  fact-check contract and evidence boundary.
- [Environment Guide](ENVIRONMENT.md): current configuration and opt-in
  execution rules.
- [Test Plan](TEST-PLAN.md): default-test and future coverage boundaries.
- [Vision Human Review](VISION-HUMAN-REVIEW.md): evidence-loss waiver and the
  required conditions for any future Prompt v2 decision.
