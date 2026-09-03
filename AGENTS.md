# AGENTS.md

This file is the repo-local guide for Codex, Claude Code, and other AI agents working in this repository. Follow it unless the user gives a direct conflicting instruction or a higher-priority system rule applies. When in doubt, preserve the public/private boundary, do less, and ask.

## Standing Work Loop

A completed, in-scope change is finished only when all four are done:

1. **Verify.** Run the relevant Gradle build and any affected provider-free
   test task.
2. **Document.** Update every tracked document the change makes stale, in the
   same change: `README.md`, `docs/CAPABILITIES.md`, `docs/ENVIRONMENT.md`,
   `docs/TEST-PLAN.md`, `docs/DEFERRED-WORK.md`, and the relevant plan.
3. **Log.** Add one `## [Unreleased]` entry to `CHANGELOG.md` under the correct
   Keep a Changelog category, and a dated `docs/logs/` entry recording what was
   authorized, what was done, what was verified, and what the change does not
   claim or authorize.
4. **Commit.** One focused commit per inseparable change. Report the modified
   files and the verification run.

Do not push. Pushing is separately gated: push only when the user asks in that
session, never as the tail of another task, and never because an earlier
session was allowed to push.

Read and follow the repository's `.gitignore` before creating, inspecting, or
including files. Treat its rules as authoritative for generated outputs,
local-only configuration, credentials, and other files that must remain
untracked; do not bypass or weaken those rules without explicit instruction.

## Agent Hard Stops

- Never copy private Setaccio product code, docs, deployment details, issue history, roadmap text, API modules, database code, or UI code into this repo.
- Never add Spring, Spring Boot, Spring AI, or Spring annotations to `setaccio-core`.
- Never make default tests call live Ollama, Anthropic, or other remote providers.
- Never add credentials, tokens, API keys, or private endpoint details to tracked files.
- Never add Docker or Testcontainers dependencies to `setaccio-lab`; keep them in `setaccio-testcontainers`.
- Never make `setaccio-lab` depend on `setaccio-testcontainers`.
- Never push without explicit user instruction, in any session, including
  immediately after committing completed work.
- Never allocate new formal run evidence under `build/`. The durable root is
  `setaccio-lab/local/evidence/<suite>/<run-id>/`; `build/` paths are accepted
  for reading already-saved evidence only.

## Standing Local Ollama Authorization

All local Ollama calls to already-installed models are authorized for
explicitly requested work in this repository. This includes starting or
connecting to a loopback Ollama service, inspecting its installed inventory
and model metadata, selecting an installed model, and invoking it. No
additional per-call, per-command, per-model, per-session, or per-run approval
is required.

This authorization does not itself start an unrequested task or slice, and it
does not make live Ollama part of default tests, `check`, `build`, application
startup, or CI. Provider-free tests and offline verification must remain
provider-free. Formal runs must still use their locked clean-baseline,
identity, attempt, evidence-integrity, and no-selective-retry rules. Model
pulls or downloads, removals, renames, silent substitutions, non-loopback
endpoints, remote providers, credentials, spending, Docker, publication of
ignored output beyond the deterministic summaries and manifests the Publication
Boundary in `docs/DEFERRED-WORK.md` permits, pushes, releases, and tags remain
separately gated.

## Repository Purpose

`setaccio-lab` is the public, Apache-2.0 side of the Setaccio split.

This repository is intended to contain:

- `setaccio-core`: a minimal plain Java library for reusable Setaccio primitives.
- `setaccio-lab`: a Spring Boot / Spring AI evaluation app for model, provider, model-type, prompt, tool-calling, and later MCP experiments.
- `setaccio-testcontainers`: an optional Testcontainers-backed integration harness that must not be required by `setaccio-lab`.
- Public-safe AI/file-processing server primitives that make the lab runnable and useful as a technical showcase.

The private Setaccio application code remains outside this repository. Curated, public-safe AI/file-processing server capabilities may move here when they support the lab and showcase goals, but do not copy private product docs, private roadmap text, private deployment details, private API modules, database code, UI code, or private product-specific server code into this repo.

## Current Split Boundary

Public:

- `setaccio-core`
- `setaccio-lab`
- `setaccio-testcontainers`
- Public-facing README, changelog, test plan, examples, and benchmark fixtures that are safe to publish.
- Public environment/setup docs that avoid committing credentials or private deployment details.

Private:

- Closed-source Setaccio application modules.
- Private product docs, deployment docs, logs, workflows, and implementation plans.

The future private repo is expected to depend on this public repo first through a Gradle composite build, then later through published artifacts if/when `setaccio-lab` or `setaccio-core` are published.

## Package Conventions

| Purpose | Module | Base package |
|---|---|---|
| Plain Java primitives | `setaccio-core` | `com.setaccio.core` |
| Spring/Spring AI lab app | `setaccio-lab` | `com.setaccio.lab` |
| Optional container tests | `setaccio-testcontainers` | `com.setaccio.testcontainers` |

## Module Rules

### setaccio-core

Keep `setaccio-core` small and Spring-free.

Allowed:

- Plain Java code.
- Minimal crypto/hash dependencies.
- JUnit tests.
- Small utility types that are generally useful outside Spring.

Current intended runtime dependencies:

- `commons-codec`
- `bcprov-jdk18on`
- `slf4j-api`

Test assertions should use AssertJ.

Not allowed in `setaccio-core`:

- Spring Framework.
- Spring Boot.
- Spring AI.
- Spring annotations such as `@Service`, `@Configuration`, `@Bean`, `@Autowired`, or `@Qualifier`.
- Application config files for Spring.
- Product-specific Setaccio private concepts.

If a future change requires dependency injection or Spring integration, put that wiring in `setaccio-lab` or a consuming private app, not in `setaccio-core`.

### setaccio-lab

`setaccio-lab` is the Spring Boot / Spring AI application.

Allowed:

- Spring Boot.
- Spring AI.
- Local Ollama integration.
- Optional Anthropic integration.
- Future optional provider integrations for OpenAI, Microsoft, Amazon, and Google.
- HTTP endpoints for local evaluation.
- Public benchmark fixtures and prompts.
- Result files written under ignored build directories.

Not allowed in `setaccio-lab`:

- Private Setaccio product modules, APIs, database code, UI code, deployment details, or product-specific server behavior.
- Direct database access or persistence code unless it is a public lab fixture explicitly added for benchmark evaluation.
- Docker or Testcontainers runtime/build requirements.
- Dependencies on `setaccio-testcontainers`.
- Live model/provider calls from default tests or CI.

Do not turn `setaccio-lab` into the private Setaccio product. It should remain a focused evaluation harness and public reference server for reusable AI/file-processing behavior.

### setaccio-testcontainers

`setaccio-testcontainers` is the optional Docker/Testcontainers integration harness.

Allowed:

- Testcontainers dependencies.
- Spring AI Testcontainers support.
- Optional container-backed integration tests.
- Test-only wiring that depends on `setaccio-lab`.

Not allowed:

- `setaccio-lab` depending on `setaccio-testcontainers`.
- Docker or Testcontainers being required for default `setaccio-lab` builds.
- Container tests that run without an explicit task, profile, or property.

## Current State Snapshot (as of 2026-09-03)

This repo was bootstrapped from the Setaccio monorepo but has been intentionally reduced:

- Root Gradle build with Java 25.
- `setaccio-core` is a plain Java BLAKE3 utility library.
- `setaccio-lab` is a Spring Boot / Spring AI app using Spring AI `2.0.1`.
- The local vision benchmark endpoint is wired at `POST /api/lab/vision`; it accepts uploaded images and model names, uses a tracked versioned public-safe prompt through a reusable direct Spring AI invocation boundary, supports optional temperature/seed/token settings, hashes inputs through `setaccio-core`, records prompt/MIME/token/structural/error metadata, returns a neutral `local` host value, and writes JSON under `build/lab-results/`.
- The local vision corpus contract uses a tracked versioned public-safe template
  with six stable non-sensitive case IDs and explicit privacy review fields.
  Personal images and filled metadata belong only under the ignored
  `setaccio-lab/local/vision-corpus/` directory; no local corpus content is
  tracked.
- The opt-in `visionMatrix` task validates that fixed local corpus, then runs
  explicit models/cases/repetitions sequentially with temperature `0.0`, seeds
  `42`/`43`, one explicit token policy, one explicit tracked prompt version,
  and Ollama pull strategy `never`. It may accept an explicit, ordered approved
  case-ID subset for controlled diagnostics; when omitted, it runs the full
  approved corpus. It resolves full immutable Ollama model
  digests, writes suite-specific raw JSON, a shared v1 manifest, and
  deterministic summary under a new dated
  `local/evidence/vision-matrix/` directory.
- `visionMatrixVerify` and `visionMatrixReanalyze` inspect saved vision
  evidence without starting Spring, reading the private corpus, or contacting
  a provider, selecting the saved supported prompt version from raw evidence.
  Human expected-observation and unsupported-detail judgments remain separate
  from deterministic analysis.
- `visionMatrixCompare` compares two already-verified saved runs offline and
  writes a deterministic Markdown report to standard output. It requires
  Spring Boot and Spring AI versions, all non-prompt protocol settings, ordered
  full model digests, and input identities to match; only prompt identity and
  code baseline may differ.
- `visionHumanReviewPrepare` accepts one explicit baseline, candidate, and
  ignored local corpus, verifies the saved evidence and deterministic
  comparability, validates corpus input identities, and writes one private,
  non-overwriting Markdown worksheet under ignored
  `local/evidence/vision-human-review/`. It organizes paired evidence but does not make
  semantic judgments or a prompt decision.
- A controlled local vision matrix completed from clean commit `11e2fa7`
  across three installed model families, four reviewed private cases, and two
  repetitions. All 24 invocations and required-section checks passed, the
  ignored v1 evidence verified offline, and Slice 7 human review is recorded
  separately in public-safe aggregate documentation without ranking models.
- A paired clean Prompt v2 local matrix completed from commit `6b5b970` with
  the same three-model, four-case, two-repetition protocol. All 24 invocations
  and required-section checks passed; ignored evidence verified offline and
  compared against the immutable v1 run. Agent-assisted review against the
  committed rubric found primary concepts retained in 11 of 12 model/case
  judgments and partially retained in one, with no total loss. It also found
  that version 2 reduced some unsupported exact specificity but not uniformly,
  while generic context and low-quality overconfidence persisted. These
  semantic findings have not been human-confirmed. The ignored v1/v2 saved-run
  directories later became unavailable, so the project owner closed that review
  prerequisite through a documented evidence-loss waiver rather than recreate
  or replace the original evidence. No human adopt/revise/reject judgment is
  claimed. Version 1 remains the operational interactive default, version 2
  remains experimental and unadopted, and any future decision requires new
  paired controlled evidence plus actual human review.
- The local chat benchmark endpoint is wired at `POST /api/lab/chat`; it accepts explicit model lists and public-safe prompts, records token usage when available, and keeps live Ollama calls opt-in.
- The Phase 2 start gate closed on 2026-08-04 when the project owner selected
  chat as the reuse and later portability surface. Slice S1 completed on a
  dedicated feature branch by extracting only proven shared saved-evidence
  file operations across vision, Tool Search, and local evaluation; their
  suite-specific schemas remain unchanged. Slice S2 added the minimal
  provider-neutral chat invocation contract and Ollama-only adapter with full
  digest identity, explicit supported-option metadata, classified outcomes,
  and shared loopback/no-pull/one-attempt model construction. Slice S3 has
  one tracked v1 three-prompt catalog, a dedicated sequential six-row runner,
  shared-v1 evidence, deterministic analysis, and standalone offline
  verify/reanalyze tasks. On 2026-08-05, one clean-baseline local
  `gemma4:e2b` run from commit `51025cf` used `128` output tokens, `PT2M`, and
  the locked six-row schedule; all invocations completed with usage metadata
  and empty responses. The ignored evidence verified and reanalyzed offline,
  and the preserved Phase 1 evidence still verifies. Phase 2 is complete as a
  contract-reuse proof, without a quality, reliability, or model-ranking claim.
  At Phase 2 closeout it did not authorize Anthropic credentials or remote calls,
  default-lifecycle live execution, or migration of the existing chat endpoint
  without request/response parity tests.
- Phase 3 completed one bounded architecture-portability proof through the
  shared chat contract. A clean replacement Ollama baseline from commit
  `215ea18` retained the same `gemma4:e2b` digest and six-row settings. The
  Anthropic candidate from clean commit `3810a19` used the pinned hosted ID
  `claude-haiku-4-5-20251001`, six sequential unseeded rows, temperature `0.0`,
  `128` output tokens, `PT2M`, one attempt, and SDK retries disabled. All six
  calls completed with non-empty output and full usage; observed usage-derived
  cost was `$0.001870`, below the `$0.005376` worst-case estimate and `$3` task
  ceiling. Ignored evidence verifies and reanalyzes offline. Common protocol
  fields were architecture-compatible, while seed semantics and local-digest
  versus hosted-ID reproducibility remain explicit limitations. No semantic,
  performance, reliability, ranking, endpoint-migration, or further-provider
  authorization is claimed.
- The local tool benchmark endpoint is wired at `POST /api/lab/tools`; it supports standard tool calling plus an opt-in standard-versus-regex-Tool-Search comparison with paired sequential repetitions, alternating advisor order, explicit case expectations, normalized discovery traces, and named assertions.
- The deterministic fixture evaluation endpoint is wired at `POST
  /api/lab/evaluations`; it exercises Spring AI's `Evaluator` contract without
  calling a model provider and remains distinct from future AI-judged
  evaluation. The first three AI-judged contract slices are implemented
  offline: one versioned fact-check prompt, three repository-authored document pairs with
  balanced supported/unsupported claims, and an actual-human confirmation
  record bound to the exact fixture-catalog digest. A dedicated plain Java
  recording judge boundary now wraps Spring AI's unchanged
  `FactCheckingEvaluator`: it requires complete explicit Ollama settings and a
  loopback endpoint, forces no-pull/one-attempt behavior, and records raw
  response, metadata, available usage, latency, normalized verdict,
  expectation agreement, and classified failures. A suite-specific offline
  lifecycle now locks the counterbalanced twelve-row schedule, BLAKE3
  document/claim identities, prompt/catalog/review/model identity, shared v1
  manifest, deterministic summary, and standalone verify/reanalyze tasks. One
  explicitly invoked `localEvaluation` host-Ollama runner now validates a
  loopback URL, locked contract, installed full model digest, option bounds,
  and fresh dated output before allocation, then executes twelve sequential
  one-attempt/no-pull rows. It is not attached to the default lifecycle;
  `RelevancyEvaluator` waits for a real retrieval flow.
- One clean-baseline controlled fact-check run completed from commit `5d41362`
  with explicit judge `gemma4:e2b`, full digest
  `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`,
  token limit `64`, timeout `PT2M`, and the locked twelve-row schedule. All 12
  one-attempt invocations completed with full usage metadata and no model,
  timeout, or provider failure. Ten rows had empty judge output and two
  unsupported rows returned valid matching `no` verdicts; there were no valid
  mismatches. The ignored evidence verified and reanalyzed byte-for-byte
  offline. No selective retry, replacement row, model pull, or raw-output
  publication occurred.
- Slice A6 closed the cycle by interpreting only that immutable evidence.
  Supported agreement was not measurable because all six supported rows were
  empty; two of six planned unsupported rows were evaluable and both agreed,
  while the other four were empty. One fixture had two valid consistent
  verdicts and five repetition comparisons were incomplete. All ten empty
  responses reached the explicit `64`-token output limit, while both valid
  responses used two completion tokens. This registers a later separately
  designed output-budget compatibility hypothesis without claiming causation,
  statistical reliability, general factuality, verdict-label tendency, or a
  judge ranking. No A5 row was rerun or replaced.
- Public-safe tool cases cover arithmetic, fixed time, catalog lookup, multi-step execution, no-match behavior, abstention, and deterministic callback failure.
- A clean controlled Tool Search refresh completed from commit `08f1cb5` across
  the locked three-model, five-case, two-repetition paired protocol. Offline
  verification and reanalysis passed with no trace-integrity failure; standard
  mode passed all 30 rows while regex Tool Search passed 12 of 30. Discovery
  behavior remains the bounded diagnostic surface, and no alternate index or
  provider is selected from this result alone.
- `setaccio-lab` includes plain Java shared evidence primitives for versioned
  manifests, non-overwriting run directories and artifact writes,
  Git/framework provenance, relative artifact links, SHA-256 integrity
  metadata, saved-run layout checks, deterministic summary handling, and
  strict offline verification. The common file operations have vision, Tool
  Search, and local-evaluation consumers without merging their schemas. The
  locked Tool Search matrix and sequential vision matrix use the shared v1
  manifest; standalone Tool Search tasks retain legacy-v0 compatibility, while
  vision verification accepts v1 evidence only.
- Phase 5 R5 completed one formal answer-generation matrix on 2026-08-30 from
  clean commit `c724e5a93c89eb5de8a11e9d1774a523f77bda37`, consuming the
  separately verified R3 lexical baseline without rerunning retrieval. The
  operationally selected already-installed `gemma4:e2b` tag resolved to full
  digest `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`
  under Ollama `0.33.2`; the 14 sequential one-attempt rows locked
  `retrieval-grounded-answer-v1`, temperature `0.0`, seed `42`, `256` output
  tokens, `PT2M`, and no pull. Ten rows completed and four retained empty
  responses; two used exact `NO_SUPPORT`, with no timeout, unavailable-model,
  authentication, rate-limit, or provider-failure result. Ignored evidence
  verified and reanalyzed offline. This is limited to invocation, abstention,
  and bracketed-reference observations; it is not an answer-correctness,
  semantic-support, relevance, quality, ranking, or model-selection claim. Do
  not rerun, repair, replace, or publish its raw output. R4 formal embedding
  execution completed once on 2026-09-02 with the `qwen3-embedding:0.6b` tag,
  which the owner pulled that day and whose literal `embedding` capability was
  confirmed by read-only inspection before any evidence directory was
  allocated. Its evidence is immutable; a further run requires a new
  scope-start request, a fresh capability and digest check, and a new dated
  directory.
- Phase 5 R6 completed one formal relevancy-evaluation matrix on 2026-08-30
  from clean commit `f704d989429a10769ce334276dc79de5bd7cd308`, consuming the
  verified R5 evidence without rerunning retrieval or answer generation. The
  operationally selected already-installed `granite4.1:3b` tag resolved to full
  digest `6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb`
  under Ollama `0.33.2`; it differs from the R5 answer artifact but that does
  not establish independence. The run locked `retrieval-relevancy-evaluator-v1`,
  temperature `0.0`, seed `42`, `64` output tokens, `PT2M`, and no pull. It
  retained 14 rows: eight eligible evaluator calls completed, two
  missing-context rows and four unavailable-answer rows were not attempted, and
  no unavailable-model, timeout, provider-failure, empty-response, or
  malformed-verdict outcome occurred. Ignored evidence verified and reanalyzed
  offline. This is limited to evaluator invocation and not-attempted-row
  observations; human support remains `NOT_REVIEWED` and answer correctness
  `NOT_ASSESSED`. Do not rerun, repair, replace, or publish raw evidence, and
  do not treat an evaluator result as ground truth, semantic correctness,
  quality, ranking, or selection.
- The default Ollama model is `gemma4:e2b`.
- The T0.1 documentation packet and provider-free T1.1-T1.8 implementation for
  the tracked small-model tool-calling compatibility plan are complete. The
  dedicated `toolCompatibility` and `toolCompatibilityTest` source sets lock
  the untreated LFM2.5 protocol at the standard advisor, eight ordered
  canonical cases, two seeded repetitions, `512` output tokens per provider
  turn, one `PT2M` whole-row deadline, one logical attempt with ordered
  provider-turn/per-call evidence, no pull, 16 sequential rows, and the exact
  suite-owned semantic call/argument oracle. The opt-in matrix, verify, and
  reanalyze tasks remain outside the default lifecycle.
- A clean Phase 1 baseline run completed on 2026-08-20 from commit `62181fb`
  using the installed model
  `hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0` with full
  digest
  `2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed`.
  All 16 planned logical row attempts were executed; none timed out and none
  was retried or replaced. No logical row attempt completed successfully;
  every first provider turn was classified `PROVIDER_FAILURE`, with no observed
  tool calls, final responses, usage, output-limit state, or visible reasoning markers. The
  ignored evidence verified and reanalyzed offline. This is a bounded
  provider-turn compatibility result, not a quality, reliability, or model
  ranking claim.
- Phase 2 prompt intervention completed its authorized paired execution and
  deterministic closeout on 2026-08-21 from clean commit `80bc122`. The two
  ignored 16-row conditions verify, reanalyze, and compare successfully, but
  every row reached the same first `PROVIDER_FAILURE` boundary. This supports
  no prompt-effect, quality, reliability, or ranking claim. On 2026-08-23 the
  owner completed T2.5 with the decision `inconclusive`, bound to the preserved
  runs and comparison. The cohort prompt policy is therefore untreated
  operation with the limitation recorded; no prompt-effect claim is
  authorized. The owner then approved the exact T3.1 cohort: five peers and a
  separately labelled `qwen3.8:27b-mlx` reference, all locked to full local
  digests under Ollama `0.32.15`. One clean-baseline `toolCompatibilityCohort`
  run completed on 2026-08-24 from commit `e897edf`, retaining all 96 planned
  rows under the untreated policy. Its ignored evidence verified and reanalyzed
  offline, and the owner authorized a bounded T3.4 interpretation recorded in
  `docs/logs/2026-08-24-phase3-tool-compatibility-cohort.md`. That record is
  per-model and multidimensional, preserves incomplete and mixed-runtime
  observations, and makes no rank, selection, general-capability, or
  semantic-correctness claim. Provider-free T3.5 implementation and one
  deterministic offline comparison completed on 2026-08-25 against that same
  verified run. The isolated `toolCompatibilityCohortCompare` task verifies
  the saved cohort, pairs every peer with the separately labelled reference by
  locked case/repetition identity, and writes only to standard output. The
  comparison found reference-only pass counts of `16`, `2`, `4`, `2`, and `2`
  in peer order, with no peer-only or neither-pass rows. This is not a ranking,
  ground-truth, model-selection, semantic-correctness, or backend-normalized
  performance result. The owner then authorized T3.6. The isolated
  provider-free `toolCompatibilityCohortFrontier` task verified the same run,
  required the complete locked schedule, compared only all-pass models by
  recorded installed-artifact byte size, and wrote its report only to standard
  output. Exactly one artifact qualified: the separately labelled
  `qwen3.8:27b-mlx` reference passed `16/16` rows at a recorded artifact size of
  `18174721847` bytes. This supports only the narrow smallest-among-qualifying-
  tested-artifacts statement under the exact protocol, not a general
  smallest-capable, ranking, or selection claim. Every new run remains
  separately unauthorized; do not rerun, replace, repair, pull, or customize
  any cohort model.
- Formal run evidence is durable and private. Every suite writes new evidence
  only under `setaccio-lab/local/evidence/<suite>/<run-id>/`, which is ignored
  but is not a Gradle output directory, so `clean` cannot delete it. One shared
  `EvidenceSuiteRoot` contract owns the root, direct-child, traversal, and
  symlink policy for all twelve suite roots; suites keep only their own
  date/run-id rules. Readers still accept a legacy `build/<suite>/<run-id>`
  path so evidence saved before 2026-09-03 can be verified, reanalyzed,
  compared, and consumed, and that acceptance is read-only. Ordinary
  interactive endpoint output is unaffected and stays under
  `build/lab-results/`. See the durable evidence root section of
  `docs/ENVIRONMENT.md`.
- Both local chat boundaries record provider responses as separate dimensions
  through one shared `ChatResponseCapture`: assistant content, any reasoning
  field, reasoning presence, finish reason, evaluated output tokens, the
  explicitly requested `ChatReasoningPolicy`, and how the adapter handled it.
  Content and reasoning are never merged. `OllamaReasoningOptions` maps the
  provider-neutral policy onto Spring AI's `ThinkOption`, which stays inside the
  Ollama adapter. The opt-in `thinkingDiagnostic` suite is a new diagnostic
  protocol with its own schema under
  `local/evidence/thinking-diagnostic/`; it reuses the tracked fact-check
  fixture catalog and prompt but is not a rerun, repair, replacement, or
  reanalysis of Phase 4 evidence and never writes into that suite. Every other
  suite deliberately keeps sending `PROVIDER_DEFAULT`, so its protocol identity,
  manifest settings, row schema, and retained evidence are unchanged; that
  inherited default is a recorded limitation in `docs/DEFERRED-WORK.md`. Do not
  add a constant to `ChatGenerationOption`: `ChatProviderOptionSupport` requires
  every constant to be classified, so a new one would make retained chat and
  answer raw JSON undeserializable.
- One controlled reasoning diagnostic completed on 2026-09-03 from clean commit
  `4e766b7a6345ba1a8af9ee1e354c2ba027e1573a` under Ollama `0.33.3`, retaining all
  30 rows with no failure, timeout, retry, or omission. With reasoning explicitly
  enabled at `64` output tokens, `gemma4:e2b` at digest `7fbdbf8f5e45` returned
  empty content with a populated reasoning field, the full `64` evaluated tokens,
  and finish reason `length` in five of six rows; the paired explicitly disabled
  arm answered in two tokens in all six; at `256` tokens reasoning fit and every
  row produced content; the non-thinking control produced content in all six.
  This is a mechanism consistent with and explanatory of the Phase 4
  output-budget association, which stands exactly as recorded. Two limits are
  part of the result: the retained runs sent no policy rather than an enabled
  one and this diagnostic has no unset-policy arm, and only the fact-check
  boundary was exercised live. No quality, factuality, reliability, ranking, or
  model-selection claim follows, no closeout is withdrawn, and no rerun of
  retained evidence is authorized.
- Tracked documentation splits the front door from the detail: `README.md`
  leads with findings and the evidence model, `docs/CAPABILITIES.md` carries
  the slice-by-slice surface description, and `docs/evidence/` holds published
  copies of deterministic summaries and manifests under the Publication
  Boundary. `docs/evidence/` is a tracked, partial publication copy of
  permitted summaries and manifests only: it is never a task input and is not
  the source for offline verification. A publication copy omits its raw
  artifact and therefore does not pass the suite's offline verifier; that is
  intended.
- `setaccio-testcontainers` remains an optional skeleton. Slice A6 deferred a
  fact-check container path because provisioning would not answer the observed
  verdict-yield question; any later container work must remain a separate,
  explicitly justified opt-in slice.

## Versioning Policy

Follow [Semantic Versioning](https://semver.org/) for project versions.
Follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) for changelog structure.

- Increment `MAJOR` for incompatible public API changes.
- Increment `MINOR` for backward-compatible public functionality.
- Increment `PATCH` for backward-compatible fixes.
- Use SemVer pre-release identifiers, such as `-alpha.N`, `-beta.N`, or `-rc.N`, before stable releases.
- Keep Gradle project versions, changelog entries, tags, and release notes consistent.
- Keep unreleased changes under `## [Unreleased]` with standard Keep a Changelog categories such as `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, and `Security`.

## Important Spring AI Notes

The Spring AI 2.0 line, currently pinned at `2.0.1`, replaced the older
RC1/M8/M4 planning targets.

Relevant upgrade concerns for this repo:

- Keep future Spring AI upgrades focused and separate from mechanical repo split work.
- Watch direct `ChatModel.call(Prompt)` usage with per-request options. `OllamaChatModel.buildRequestPrompt` substitutes the model's default options only when the prompt carries none; a non-null partial options object is used verbatim and every configured default is dropped. This is identical in Spring AI `2.0.0` and `2.0.1`, so it is a standing hazard rather than an upgrade regression. Prefer `ChatClient`, which merges the runtime options onto `chatModel.getOptions()`, or materialize a complete options object first. The vision boundary does the latter through `ollamaChatModel.getOptions().mutate()`, which keeps its direct-call protocol instead of gaining `ChatClient`'s auto-registered tool-calling advisor.
- Spring AI `2.0.1` made tool-call limits configurable, defaulting to 40 calls per tool and 150 total with `ToolCallLimitBehavior.THROW`; exceeding either limit aborts the invocation instead of truncating it. `ToolCallLimitPolicy` pins those values for both tool paths so a later framework default cannot change the protocol silently. They are deliberately not written into saved evidence: Tool Search matrix verification compares the exact manifest settings key set, so adding a key would invalidate every retained manifest.
- Spring AI's `OllamaChatModel` maps Ollama's `message.thinking` into the assistant message's properties under the key `thinking`, and `message.content` into the assistant text; `getOutput().getText()` therefore returns content only. Generation metadata carries the finish reason and a duplicate `thinking` entry, but only when both `prompt_eval_count` and `eval_count` are present; otherwise it is `ChatGenerationMetadata.NULL`. `Usage.getCompletionTokens()` is Ollama's `eval_count`, which counts reasoning tokens too. `OllamaChatOptions.thinkOption` controls the request's `think` field, and Spring AI documents that a thinking-capable model auto-enables thinking when it is unset, so sending nothing is not the same as disabling. Verified identical in `2.0.0` sources and `2.0.1` bytecode; recorded in `docs/logs/2026-09-03-thinking-field-inspection.md`.
- The long-term harness should cover Spring AI's major provider surface: Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama.
- The long-term harness should cover Spring AI model types: chat completion, embedding, text to image, audio transcription, text to speech, and moderation.
- Provider-backed tests must be opt-in and must not run by default in CI.
- Keep provider environment variable requirements documented in `docs/ENVIRONMENT.md`.
- For Ollama chat config, follow Spring AI's `spring.ai.ollama.base-url`, `spring.ai.model.chat`, `spring.ai.ollama.chat.model`, and `spring.ai.ollama.init.pull-model-strategy` properties. `OLLAMA_API_BASE` is only a repo-supported environment alias.
- For Anthropic chat config, follow Spring AI's `spring.ai.anthropic.api-key`, `spring.ai.anthropic.base-url`, and `spring.ai.anthropic.chat.options.*` properties. Re-check exact Spring AI 2.0 defaults before changing models or option names.
- Anthropic live tests should eventually cover sync chat, streaming, multimodal image/PDF input, tool choice/tool calling, and extended thinking where the selected Claude model supports it.
- For Google GenAI chat config, follow Spring AI's `spring.ai.google.genai.api-key`, Vertex AI properties, and `spring.ai.google.genai.chat.*` properties. `GEMINI_API_KEY` is only a repo-supported alias for local Gemini Developer API setup.
- Do not use `GOOGLE_CLIENT_ID` or `GOOGLE_CLIENT_SECRET` for Spring AI Google GenAI chat tests; those are OAuth client credentials, not GenAI API-key credentials.
- Google GenAI tests should account for Gemini Developer API versus Vertex AI mode, multimodal input, response MIME type, Google Search grounding, server-side tool metadata, safety settings, cached content, thought signatures, and model-specific thinking option compatibility.
- The deterministic evaluation benchmark already uses Spring AI's `Evaluator`
  and `EvaluationRequest`. The Slice 7 planning gate checked the Spring AI
  `2.0.0` `RelevancyEvaluator` and `FactCheckingEvaluator` contracts, and they
  were re-checked against `2.0.1`: the public contracts and the embedded
  evaluation prompt texts are unchanged. Re-check them again if framework
  versions change before implementation.
- For container-backed tests, track Spring AI's `spring-ai-spring-boot-testcontainers` support and service connections, but keep Docker/Testcontainers opt-in.
- Keep Testcontainers dependencies isolated in `setaccio-testcontainers`; do not add them to `setaccio-lab`.
- Direct Spring AI 2.0 tool-calling and regex Tool Search comparison are implemented. Keep new advisor/index work bounded, expectation-aware, and offline-tested before expanding it.
- MCP should remain a later phase, after direct Spring AI tool tests are reliable.

## Public-Safe Copy Guidance

Do not copy previous private docs verbatim into this repo.

Safe to synthesize:

- Public purpose of the lab.
- Public test strategy.
- Public benchmark plan.
- Public-facing changelog entries.

Do not copy:

- Private product plans.
- Private deployment notes.
- Private daily logs.
- Private issue/PR history.
- Private roadmap details unrelated to the lab.

From prior lab work, these concepts are useful but should be copied/adapted carefully:

- `BenchmarkResult` and simple result row models.
- A local-only vision benchmark endpoint shape.
- A Spring Boot context smoke test.
- A prompt file for Setaccio-style image classification, if phrased generically.
- JSON result writing under `build/lab-results/`, when benchmark execution is wired.

Avoid copying prematurely:

- Suite loader and YAML suite machinery.
- Leaderboard/report endpoint.
- Calculator/weather demo tools.
- MCP code.
- Any private Setaccio server utility classes.

## Near-Term Implementation Plan

Completed:

- Keep `setaccio-core` Spring-free and buildable.
- Keep `setaccio-lab` as the Spring/Spring AI host.
- Wire the first local-only vision benchmark service with uploaded images, per-request model names, Ollama calls, core hashing, structured rows, and JSON output under `build/lab-results/`.
- Wire local chat, tool-calling, regex Tool Search comparison, and deterministic evaluation benchmark endpoints.
- Add deterministic public-safe tool fixtures and expectation-aware cases with normalized trace observations.
- Make Tool Search comparisons paired and sequential, alternate advisor order across repetitions, and record deterministic generation settings.
- Document local Ollama setup and provider environment variables.
- Add offline tests for the current core, vision, chat, tool, Tool Search, and deterministic evaluator behavior.
- Add the shared benchmark evidence lifecycle foundation and offline integrity tests.
- Apply the shared evidence lifecycle to the locked Tool Search matrix and add
  standalone offline saved-run verification and deterministic summary
  regeneration with legacy-v0 compatibility.
- Add the reproducible vision invocation contract with a tracked prompt and
  digest, explicit Ollama options, usage metadata, deterministic section
  checks, classified errors, and backward-compatible multipart handling.
- Add the ignored local vision corpus layout and public-safe case metadata
  template without tracking personal source images.
- Add the dedicated sequential vision matrix, strict corpus reader,
  suite-specific evidence writer, offline analyzer, and saved-run
  verify/reanalyze tasks.
- Populate and approve a bounded ignored corpus, smoke-check the selected
  installed model cohort, lock the no-limit token policy, and complete one
  clean-baseline controlled local vision matrix with offline verification.
- Complete the local AI-judged evaluation and Testcontainers compatibility
  planning gate without adding a live judge, container runtime task, or new
  dependency.
- Complete the controlled local Tool Search refresh with offline verification
  and a bounded conclusion that does not select another index or provider.
- Close the unavailable Prompt v1/v2 review prerequisite through a documented
  evidence-loss waiver without converting agent-assisted findings into a human
  decision, changing the Prompt v1 default, or recreating evidence under the
  original run names.
- Add the versioned local fact-check prompt, balanced six-fixture catalog, and
  exact-digest actual-human confirmation record with deterministic offline
  contract tests and no live model behavior.
- Add the dedicated fact-check recording judge boundary with explicit Ollama
  options, loopback/no-pull/one-attempt policy, raw-response and usage capture,
  strict verdict normalization, classified failures, and provider-free mocked
  tests.
- Add the suite-specific offline fact-check evidence lifecycle with a locked
  twelve-row schedule, BLAKE3 document/claim identities, shared v1 manifest,
  deterministic summary, strict offline verification/reanalysis, and no live
  model behavior.
- Add the explicit host-Ollama fact-check runner with pre-allocation contract,
  option, loopback, installed-model/digest, and output checks; locked
  sequential one-attempt execution; provider-free tests; and no default
  lifecycle attachment.
- Complete one clean-baseline controlled local fact-check run with twelve
  preserved attempts, immutable judge/contract/code identities, offline
  verification and byte-identical reanalysis, and aggregate-only public-safe
  closeout.
- Complete the bounded Slice A6 interpretation without treating two
  repetitions as statistical reliability, claiming an order effect or general
  factuality, ranking judges, or rerunning/replacing A5 rows; record the narrow
  follow-up hypothesis and defer Testcontainers for this cycle.
- Complete one bounded Anthropic chat portability proof with a pinned hosted
  model ID, explicit option handling, a current official-price estimate and
  user-authorized USD ceiling, six sequential one-attempt calls, ignored
  evidence, and standalone offline verification/reanalysis without changing
  the interactive endpoint or making a quality/performance comparison.

Pending and separately deferred:

- Phase 1 of
  [SmallModelToolCallingCompatibilityPlan.md](docs/SmallModelToolCallingCompatibilityPlan.md)
  is closed through one bounded clean baseline, offline verification and
  reanalysis, and the dated public-safe interpretation. Phase 2's paired
  execution, deterministic comparison, and owner-only T2.5 decision are
  complete. The owner selected `inconclusive` on 2026-08-23, which selects
  untreated cohort operation with an explicit limitation and authorizes no
  prompt-effect claim. The owner subsequently approved the exact T3.1 cohort,
  one clean-baseline T3.3 execution, and bounded T3.4 offline interpretation.
  The 96-row ignored run from commit `e897edf` verifies and reanalyzes offline;
  its public-safe per-model interpretation is recorded in the dated cohort log.
  Provider-free T3.5 and one deterministic reference comparison of that same
  run completed on 2026-08-25 without provider access or evidence mutation.
  Provider-free T3.6 then evaluated the same verified run once and found the
  frontier measurable only in the narrow deployed-protocol sense: the
  separately labelled `qwen3.8:27b-mlx` reference was the sole `16/16`
  qualifier. This does not authorize another Phase 3 invocation, rerun,
  replacement row, model pull, or evidence-based model selection. The
  completed Phase 4 and bounded Phase 5 closeouts preserve their ignored
  evidence and do not themselves start successor runs. The standing local
  Ollama authorization above covers local calls for explicitly requested and
  protocol-allowed work. Default tests and CI remain provider-free; formal
  evidence remains immutable and protocol-bound. Pulls,
  downloads, removals, renames, silent substitutions, remote providers,
  credentials, spending, Docker, publication of ignored output beyond what the
  Publication Boundary in `docs/DEFERRED-WORK.md` permits, pushes, releases,
  and tags remain separately gated.
- Any further Anthropic call, another provider/model type, or endpoint migration
  requires a new explicit scope and authorization. The completed Phase 3 proof
  does not grant standing remote-call or spending authority; keep the existing
  chat endpoint unchanged unless parity tests justify migration.

The tracked [deferred-work index](docs/DEFERRED-WORK.md) is the canonical
public-safe list of deferred scope, start gates, and non-authorization
boundaries. Keep it aligned with this section, the environment guide, test
plan, changelog, and dated log when the status of a deferred item changes.

- If Prompt v2 is reconsidered later, create a separately authorized paired
  controlled protocol with new preserved evidence and actual human review.
- Phase 4 is closed: the initial F1–F4 work and the separately planned five-arm
  breakpoint follow-up are retained as immutable evidence. Do not rerun,
  repair, replace, alter, or publish an arm. Any later output-budget experiment
  requires an explicit scope-start request and a fresh clean-baseline protocol;
  its local Ollama calls are covered by the standing authorization above and it
  is not a continuation of the completed Phase 4 work.
- Phase 5 is closed as a bounded protocol closeout. R3, R5, and R6 retained
  evidence verifies offline; R5 and R6 are the completed local-model
  executions. R4 completed one explicitly requested formal run on
  2026-09-02 using the `qwen3-embedding:0.6b` tag, which the owner pulled that
  day and which read-only inspection confirmed advertises Ollama's literal
  `embedding` capability, under the locked clean-baseline, no-pull,
  one-attempt, non-overwriting-evidence contract. Its evidence is immutable and
  any further run needs a new scope-start request. This closeout does not
  authorize a rerun, but the standing authorization above
  covers local inspection, selection, and invocation once that work is
  explicitly requested.
- Keep any later container-backed work isolated in `setaccio-testcontainers`
  and justify it with a provisioning or service-connection question distinct
  from the completed host-Ollama fact-check cycle.
- Add tests before expanding into additional model types, providers, tools, or MCP.

## Test Direction

### Core Tests

Maintain tests that prove:

- `setaccio-core` has no Spring runtime dependencies.
- Both BLAKE3 implementations produce the same hash for the same input.
- Empty input, string input, byte arrays, and streams are covered.
- Null inputs throw clear exceptions.
- Hash verification works for matching and non-matching hashes.

Recommended future guard:

- Add a Gradle dependency check or test that fails if `org.springframework` appears on `setaccio-core` runtime classpath.

### Lab Smoke Tests

Maintain tests that prove:

- The Spring Boot app context starts under a `test` profile.
- Tests never call live Ollama or Anthropic by default.
- Model pulling is disabled in tests.
- The local-only controller is not accidentally exposed outside the intended profile.

### Vision Benchmark Tests

- Mock `OllamaChatModel` through the reusable vision invocation boundary so
  service tests do not require a live model.
- Verify prompt ID/version/digest and explicit model, temperature, seed, and
  optional token-limit settings.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include model settings, prompt metadata, detected MIME
  type, input name/hash, latency, token metadata, output text, structural
  checks, success flag, and classified error details.
- Verify failed model calls produce failed rows rather than crashing the whole benchmark run.
- Verify result JSON writing uses ignored build output directories.

### Optional Live Tests

Live model and provider tests must be opt-in.

Rules:

- Never run live Ollama or remote-provider tests by default in CI.
- Never auto-pull large models in tests.
- Require an explicit Gradle property or profile for live runs.
- Require explicit provider and model names.
- Require explicit credentials through local environment variables or ignored local config for remote providers.
- Keep the required environment variables in `docs/ENVIRONMENT.md` current when adding providers or model types.
- Store live outputs only under ignored build directories.
- Keep AI-judged evaluator tests and Testcontainers-backed tests opt-in.

### Local Fact-Check Evaluation Tests

- Keep `localEvaluationTest`, `localEvaluationVerify`, and
  `localEvaluationReanalyze` provider-free.
- Cover every required runner option and preflight failure before output
  allocation, including contract drift, non-loopback endpoints, model identity,
  and reused output.
- Prove the executor makes exactly twelve calls in locked sequential order,
  uses one attempt per row, and retains classified failed rows without
  selective retries or replacement.
- Keep `localEvaluation` outside `test`, `check`, `build`, application startup,
  and CI.

### Later Test Phases

Text benchmark phase:

- Structured output validity.
- Prompt regression tests.
- JSON parse reliability.

Provider/model-type phase:

- Chat completion output quality and option handling.
- Embedding dimensionality, determinism expectations, and similarity checks.
- Text-to-image request metadata and generated artifact handling.
- Audio transcription fixture handling and transcript comparison.
- Text-to-speech generated artifact handling.
- Moderation category and score mapping.

Evaluation/Testcontainers phase:

- Phase 4 is closed. Preserve its immutable output-budget evidence without
  rerun, repair, replacement, alteration, or publication. Any later
  output-budget experiment needs separate authorization and a fresh
  clean-baseline protocol; it is not a continuation of the completed study.
- The bounded Phase 5 closeout leaves R3, R5, and R6 retained evidence
  unchanged. R4 completed one separately requested formal run on
  2026-09-02 using the pulled `qwen3-embedding:0.6b` tag, whose literal
  `embedding` capability was proven by read-only inspection under the locked
  clean-baseline, no-pull, one-attempt contract. This closeout does not
  authorize an R4 rerun; the standing local Ollama authorization covers
  inspection, selection, and invocation of already-installed loopback models
  when that work is requested. Model pulls remain separately gated.
- The completed R5 run consumed verified retrieval evidence without rerunning
  retrieval, and R6 consumed that verified R5 evidence without rerunning answer
  generation. Both preserve retrieved context/ranks and keep retrieval
  expectation, evaluator result, human support judgment, and answer correctness
  separate. Default tests remain fake-provider tested; an AI evaluator is not
  ground truth.
- Testcontainers is deferred for the completed fact-check cycle. A later
  optional typed Ollama service-connection/model-provisioning slice must be
  independently justified, isolated in `setaccio-testcontainers`, and never
  enter the normal build lifecycle.

Tool-calling phase:

- Maintain explicit required/forbidden tool, output-term, and tool-response-term assertions for public-safe cases.
- Preserve normalized Tool Search query/discovery observations together with raw selected calls and executed responses.
- Keep comparison repetitions paired and sequential; record effective seeds and execution order so latency and reliability results remain interpretable.
- For the prepared compatibility matrix, preserve ordered provider turns and
  per-call linkage; apply `512` tokens per provider turn and `PT2M` to the whole
  logical row attempt, and never let timed-out work overlap a later row.
- Keep raw JSON validity, declared-schema validity, exact call sequence,
  semantic argument agreement, callback lifecycle, and final-output assertions
  separate. The suite-owned oracle adds only missing call/argument expectations;
  it must not duplicate canonical prompts, existing expectations, or schemas.
- Add more distractor tools only through bounded deterministic fixture slices.
- Use controlled opt-in local model matrices to decide whether another index or provider path is justified.

MCP phase:

- Direct Spring AI tool call versus MCP tool call comparison.
- Local-only transport tests.
- Security and argument-validation behavior.

## Build Commands

```bash
./gradlew :setaccio-core:test
./gradlew :setaccio-lab:test
./gradlew :setaccio-lab:chatMatrixTest
./gradlew :setaccio-lab:localEvaluationTest
./gradlew :setaccio-lab:visionMatrixTest
./gradlew :setaccio-testcontainers:test
./gradlew :setaccio-core:build
./gradlew :setaccio-lab:build
./gradlew :setaccio-testcontainers:build
./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build
./gradlew allDeps
```

Run the lab locally:

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

The lab app uses port `8082`.

## Git Workflow

Follow the Standing Work Loop above for every change: verify, document, log,
commit. Keep unrelated user changes unstaged and uncommitted. Report the
modified files and the verification run with each commit.

Standing closeout instruction for workflow-guidance corrections:

- When the user asks to correct, simplify, or make a repository workflow guide
  copy/paste-ready in a way similar to the vision human-review instructions,
  treat that request as authorization to complete and commit the bounded
  correction.
- Complete the whole change before committing: align any related task error or
  operator-facing behavior, active instructions, environment documentation,
  changelog, dated log, and risk-matched verification that are affected. Do not
  leave part of the same correction unstaged or undocumented.
- Use one focused commit when implementation, tests, and documentation form one
  inseparable change. Split commits into logical chunks when independently
  useful changes, such as repository-policy guidance and functional workflow
  behavior, can be reviewed or reverted separately.
- This standing instruction does not authorize a push. Push only when the user
  explicitly requests it.

Before committing in a future session:

- Run the relevant Gradle build.
- Check `git status --short`.
- Confirm no private docs are staged, and no generated output beyond what the
  Publication Boundary in `docs/DEFERRED-WORK.md` permits (deterministic
  summaries and manifests under `docs/evidence/`).
- Confirm no `.DS_Store`, `.gradle`, or `build` outputs are tracked.
