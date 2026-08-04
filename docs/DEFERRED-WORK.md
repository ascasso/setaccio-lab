# Deferred Work

Status: current after the completed August 2026 local fact-check cycle.

This is the tracked index for work intentionally outside the completed August
scope. It distinguishes a deferred item from a failed or partially completed
item. A listing here does not authorize implementation, a live model call,
Docker use, a credential, spending, a release, or a tag.

## Completed Boundary

The local fact-check cycle is complete: its versioned prompt and
human-confirmed fixture contract, dedicated recording judge boundary, offline
evidence lifecycle, one controlled host-Ollama run, and bounded A6
interpretation are recorded in
[LOCAL-AI-EVALUATION-PLAN.md](LOCAL-AI-EVALUATION-PLAN.md). The saved raw A5
evidence remains ignored. This document lists only work that deliberately did
not enter that cycle.

## Deferred From the August Cycle

| Item | Status and reason | Required gate before work begins |
| --- | --- | --- |
| Prompt v2 decision | Deferred. The historical paired evidence is unavailable, so no actual-human `adopt`, `revise`, or `reject` decision is claimed. Prompt v1 remains the operational default and Prompt v2 remains experimental. | Separately authorize a new paired protocol with new run names, preserved evidence, and actual human review. Do not treat agent-assisted inspection as that review. |
| Fact-check output-budget compatibility | Deferred. A5 had ten empty outputs at its explicit `64`-token limit and two valid outputs at two completion tokens. This is an association, not a causal finding. | Pre-register a new experiment that changes only the explicit positive token limit, uses a new ignored evidence directory, and retains the immutable judge digest, prompt, fixtures, order, temperature, seeds, one-attempt policy, and no-pull behavior. |
| Testcontainers fact-check path | Deferred. The host-Ollama runner, provenance, and offline verification worked; container provisioning would not answer the observed verdict-yield question. | Establish a distinct service-connection or provisioning question. Keep any typed Ollama dependency, container task, and Docker behavior in `setaccio-testcontainers`, opt-in, and outside normal `test`, `build`, and CI. |
| Relevancy evaluation and retrieval | Deferred. Ordinary fixture context is not a retrieval flow. | Add a real retrieval path that preserves the retrieved documents and can be evaluated without presenting fixture context as RAG evidence. |
| Release, tag, and promotion | Deferred. The August feature branch is not a release decision. | Use a separately authorized promotion/release workflow after the appropriate integration review; decide version, changelog release entry, and tag together. |

## Planned Only After a New Start Decision

These are roadmap candidates, not active work.

### September: one chat reuse proof

The candidate is an existing public-safe Ollama chat surface. It should reuse
the established invocation/evidence contract rather than create a parallel
framework, and place Ollama behind a narrow provider-neutral boundary.

Before it starts, the project owner must select chat as the surface. The work
must remain local, no-pull, and provider-free; it must preserve August evidence
verification and not migrate the existing chat endpoint unless request/response
parity is tested.

### October: one Anthropic portability proof

The candidate is exactly one Anthropic adapter behind the September boundary.
It remains deferred until the September work is complete, framework
compatibility is reviewed, credentials are explicitly authorized, and a maximum
spend is approved immediately before any live call. Without that authorization,
only offline/mock implementation may be described as buildable; the live proof
is deferred.

## Deferred Through the Current Roadmap

The following do not enter the August, September, or narrow October path unless
the plan is explicitly revised:

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
