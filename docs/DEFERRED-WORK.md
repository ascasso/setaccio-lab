# Deferred Work

Status: current after the completed Phase 2 local chat reuse proof on 2026-08-05.

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

## Deferred From the August Cycle

| Item | Status and reason | Required gate before work begins |
| --- | --- | --- |
| Prompt v2 decision | Deferred. The historical paired evidence is unavailable, so no actual-human `adopt`, `revise`, or `reject` decision is claimed. Prompt v1 remains the operational default and Prompt v2 remains experimental. | Separately authorize a new paired protocol with new run names, preserved evidence, and actual human review. Do not treat agent-assisted inspection as that review. |
| Fact-check output-budget compatibility | Deferred. A5 had ten empty outputs at its explicit `64`-token limit and two valid outputs at two completion tokens. This is an association, not a causal finding. | Pre-register a new experiment that changes only the explicit positive token limit, uses a new ignored evidence directory, and retains the immutable judge digest, prompt, fixtures, order, temperature, seeds, one-attempt policy, and no-pull behavior. |
| Testcontainers fact-check path | Deferred. The host-Ollama runner, provenance, and offline verification worked; container provisioning would not answer the observed verdict-yield question. | Establish a distinct service-connection or provisioning question. Keep any typed Ollama dependency, container task, and Docker behavior in `setaccio-testcontainers`, opt-in, and outside normal `test`, `build`, and CI. |
| Relevancy evaluation and retrieval | Deferred. Ordinary fixture context is not a retrieval flow. | Add a real retrieval path that preserves the retrieved documents and can be evaluated without presenting fixture context as RAG evidence. |
| Release, tag, and promotion | Deferred. The August feature branch is not a release decision. | Use a separately authorized promotion/release workflow after the appropriate integration review; decide version, changelog release entry, and tag together. |

## Planned Only After Separate Authorization

These remain roadmap candidates, not active work.

### Phase 3: one Anthropic portability proof

Slice O1 is implemented and provider-free on `feature/anthropic-portability`.
It adds exactly one adapter behind the completed Phase 2 chat boundary, using
the pinned Anthropic model ID `claude-haiku-4-5-20251001`. The adapter maps
temperature, maximum output tokens, timeout, and one attempt (`maxRetries=0`),
records returned effective model/usage and a format-validated opaque response
ID, and records seed as unsupported rather than simulating it. It has no
runner, live call, credential lookup, fallback, streaming, tool, or multimodal
path. Credentials remain local-only and the adapter records neither API keys,
headers, nor base URLs.

Slice O2 and the six-call live proof remain deferred. Immediately before any
live call, calculate the current official price-based worst-case estimate and
obtain separate explicit authorization with a maximum USD budget. Until then,
Phase 3 is buildable with mocked verification, not complete.

## Deferred Through the Current Roadmap

The following do not enter the current three-phase path unless the plan is
explicitly revised:

- additional remote providers beyond the one authorized Anthropic proof;
- additional model types such as embeddings, image generation, audio
  transcription, text to speech, and moderation;
- MCP, RAG, vector stores, and other retrieval infrastructure;
- new Tool Search indexes or a broad tool-calling expansion;
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
