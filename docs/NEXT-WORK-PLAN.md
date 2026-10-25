# Next Work: Cleanup, Then MCP

Status: cleanup selected on 2026-10-11; merged-branch cleanup completed on
2026-10-24. The working branch remains `feature/cleanup-mcp-plan`, created
from `origin/develop` at `a2f7754`. M0 is the next planning deliverable;
tag publication remains a separate decision.

The authorized Phase 0-5 work remains closed. This is a successor plan, not an
extension, repair, or replacement of any closed experiment.

## Cleanup First

| Item | Status and next action |
| --- | --- |
| Fresh branch | Completed: `feature/cleanup-mcp-plan`, created from `origin/develop` at `a2f7754` on 2026-10-11. |
| Release documentation | Corrected in this change. Release `0.1.0` landed on `develop` through PR #81 and on `master` through PR #82 on 2026-09-04. The original release authorization is preserved in its dated log, with a later promotion note. |
| Roadmap wording | Corrected in this change: the completed path is Phase 0-5, including bounded embedding retrieval, answer generation, and relevancy evaluation. MCP is the selected next planning topic. |
| Merged branch cleanup | Completed on 2026-10-24. All seven listed local branches were ancestors of refreshed `origin/develop` and unused by worktrees, then removed. Six remote branches were already absent; the remaining `feature/tool-compatibility-plan` remote branch was removed. Local and remote absence verified. |
| Tag publication | Decision pending. Local annotated `v0.1.0` targets `016eef300764af4fa69898eba0eefb677a07b96f`; GitHub has no matching tag as checked on 2026-10-11. Push only if explicitly requested in that session; do not recreate or move the tag. |
| Detailed Ollama setup guide | Retained documentation follow-up. Cover tested loopback setup, manual installation, explicit installed models, and opt-in calls with no automatic pull. It can be scheduled separately and is not a prerequisite for provider-free MCP planning. |

The removed merged local branches were:

- `feature/anthropic-portability`
- `feature/chat-evidence-reuse`
- `feature/local-fact-checking-evaluation`
- `feature/vision-evidence-lifecycle`
- `feature/vision-prompt-experiment`
- `plan`
- `feature/tool-compatibility-plan`, now superseded as the working branch

PR #84 promoted the Gradle wrapper update from `develop` to `master` on
2026-10-11. At this checkpoint, `origin/develop` (`a2f7754`) and
`origin/master` (`c9a838d`) have identical file trees. Their different commit
histories do not imply an outstanding content promotion.

## MCP Planning Next

The first question is whether the same public-safe fixture tools retain their
discovery, argument, execution, and failure behavior when reached through MCP
instead of the existing direct Spring AI tool boundary. Keep model behavior
separate from transport and server-lifecycle behavior.

Start with one repository-authored local server and a small explicit subset
of the existing deterministic fixture tools. Use the same tool names,
descriptions, schemas, inputs, and expected callback results for both paths.
Do not add remote servers, another provider, new Tool Search indexes, or a
general MCP gateway to this first comparison.

| Slice | Deliverable and completion condition |
| --- | --- |
| M0: detailed MCP contract plan | Inspect the current Java 25, Spring Boot `4.1.1`, and Spring AI `2.0.1` baseline and official version-specific MCP documentation. Select one local transport, exact client/server dependencies, fixture subset, lifecycle ownership, timeouts, and request/response contracts. State whether any dependency change is needed instead of assuming an upgrade. Define the failure taxonomy, evidence schema, and acceptance checks before implementation. |
| M1: provider-free contract tests | Specify and add tests before the new boundary: discovery/name/schema parity, valid arguments, malformed or missing arguments, unknown tools, deterministic callback failure, transport failure, timeout, shutdown, and repeated startup without leaked resources. Use fake model responses; no Ollama or remote provider is needed. |
| M2: local MCP boundary | Implement only the selected transport and fixture surface in `setaccio-lab`, satisfying M1. Prove the local client/server path without a model. Keep the core Spring-free, Docker absent, and MCP startup outside normal application startup and default build/test/CI lifecycles. |
| M3: optional controlled direct-versus-MCP run | Propose only after the provider-free path passes. Lock a fresh clean baseline, explicit installed model/full digest, runtime versions, tool/catalog identities, ordered paired schedule, generation settings, whole-row deadline, one-attempt rules, and a new suite directory under `setaccio-lab/local/evidence/`. Retain all attempted rows and classified failures; separate saved-evidence integrity from invocation success. Provide independent offline verify and reanalyze tasks. Execute only when that formal run is requested. |
| M4: bounded interpretation | Compare discovery, validated arguments, callback results, completion, and classified failures separately. Treat usage and latency as recorded observations with explicit comparability limits. Do not convert transport parity into semantic correctness, model quality, reliability, ranking, or selection. |

M0 is the next planning deliverable after the cleanup pass. The table is a
work sequence, not a claim that an MCP surface, task, or test already exists
and not an instruction to execute every slice now. Detailed design can proceed
while the independent tag-publication decision remains pending.

## Embedding Proposal Considered

The owner supplied a separate proposal for an embedding endpoint and matrix.
Its useful principles are explicit opt-in execution, fixed public fixtures,
input identities, immutable evidence, and offline analysis. Those principles
are already part of this repository's evidence model.

Its premise that embeddings and downstream retrieval are unimplemented is
outdated. Phase 5 R4 already has an
[Ollama embedding adapter](../setaccio-lab/src/retrieval/java/com/setaccio/lab/retrieval/OllamaRetrievalEmbeddingClient.java)
using Spring AI's `OllamaApi`, plus `retrievalEmbedding`,
`retrievalEmbeddingVerify`, and `retrievalEmbeddingReanalyze`. R5 answer
generation and R6 relevancy evaluation also exist. The existing adapter is not
a generic Spring `EmbeddingModel` service, and there is no interactive
`POST /api/lab/embeddings`; those would be distinct additions requiring a
specific API or experiment purpose, not prerequisites for MCP.

Do not add placeholder providers, assume a suggested model is installed, or
encode vectors as base64 without a defined representation contract. Fixed
fixtures and deterministic summaries do not by themselves promise identical
provider vectors across runtimes. Evidence verification must distinguish
intact recorded failures from corrupted evidence; verification and rewriting
a summary remain separate operations. The repository uses Groovy
`setaccio-lab/build.gradle`, and closeout must cover all affected tracked docs,
the changelog, dated log, build, and affected provider-free tests.

The embedding proposal does not replace the owner's cleanup-then-MCP choice.

## Completion Boundaries

Each implemented slice follows the standing verify/document/log/commit loop,
then one normal branch-push attempt after its completed batch of one or more
commits, under [AGENTS.md](../AGENTS.md). Report failure without automatic
retry, and verify the remote branch SHA on success. Tags remain separately
requested. Existing endpoints, closed protocols,
prompt defaults, reasoning policies, and retained evidence are unchanged by
this plan. Model pulls, remote providers, credentials, spending, Docker,
publication outside the existing permitted summaries/manifests, and new
releases or tags keep their existing gates.
