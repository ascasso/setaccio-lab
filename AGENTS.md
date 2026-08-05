# AGENTS.md

This file is the repo-local guide for Codex, Claude Code, and other AI agents working in this repository. Follow it unless the user gives a direct conflicting instruction or a higher-priority system rule applies. When in doubt, preserve the public/private boundary, do less, and ask.

Commit every completed, in-scope change after appropriate verification. Do not
push unless the user explicitly asks.

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
- Never push without explicit user instruction. Commit every completed,
  in-scope change after appropriate verification.

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

## Current State Snapshot (as of 2026-08-04)

This repo was bootstrapped from the Setaccio monorepo but has been intentionally reduced:

- Root Gradle build with Java 25.
- `setaccio-core` is a plain Java BLAKE3 utility library.
- `setaccio-lab` is a Spring Boot / Spring AI app using Spring AI `2.0.0`.
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
  `build/vision-matrix/` directory.
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
  `build/vision-human-review/`. It organizes paired evidence but does not make
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
  suite-specific schemas remain unchanged. Slice S2 is next. Phase 2 remains
  local, no-pull, and provider-free; it does not authorize Anthropic
  credentials or remote calls, default-lifecycle live execution, or migration
  of the existing chat endpoint without request/response parity tests.
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
- The default Ollama model is `gemma4:e2b`.
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

Spring AI `2.0.0` replaced the older RC1/M8/M4 planning targets.

Relevant upgrade concerns for this repo:

- Keep future Spring AI upgrades focused and separate from mechanical repo split work.
- Watch direct `ChatModel.call(Prompt)` usage with per-request options. Spring AI 2.0 M5+ changed how partial options are merged. Prefer `ChatClient` where practical, or explicitly combine options with model defaults.
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
  `2.0.0` `RelevancyEvaluator` and `FactCheckingEvaluator` contracts; re-check
  them if framework versions change before implementation.
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

Active:

- Continue the authorized Phase 2 chat reuse proof after completed Slice S1.
  Slice S2 adds the minimal provider-neutral chat invocation boundary with only
  an Ollama adapter; Slice S3 adds one dedicated sequential six-row chat matrix
  with offline verification/reanalysis. Keep the existing endpoint unchanged
  unless parity tests justify migration.

Pending:

The tracked [deferred-work index](docs/DEFERRED-WORK.md) is the canonical
public-safe list of deferred scope, start gates, and non-authorization
boundaries. Keep it aligned with this section, the environment guide, test
plan, changelog, and dated log when the status of a deferred item changes.

- If Prompt v2 is reconsidered later, create a separately authorized paired
  controlled protocol with new preserved evidence and actual human review.
- If separately authorized, design a new output-budget compatibility
  experiment that changes only the explicit positive token limit and writes a
  new evidence directory; do not treat it as a retry or correction of A5.
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

- If separately authorized, test the registered output-budget compatibility
  hypothesis as a new run while preserving the immutable judge digest,
  prompt, fixtures, row order, temperature, seeds, one-attempt policy, no-pull
  behavior, failure classification, and offline evidence verification.
- Later retrieval slice: relevancy evaluation only when a real retrieval flow
  supplies preserved context.
- Testcontainers is deferred for the completed fact-check cycle. A later
  optional typed Ollama service-connection/model-provisioning slice must be
  independently justified, isolated in `setaccio-testcontainers`, and never
  enter the normal build lifecycle.

Tool-calling phase:

- Maintain explicit required/forbidden tool, output-term, and tool-response-term assertions for public-safe cases.
- Preserve normalized Tool Search query/discovery observations together with raw selected calls and executed responses.
- Keep comparison repetitions paired and sequential; record effective seeds and execution order so latency and reliability results remain interpretable.
- Add argument-validity checks and more distractor tools only through bounded deterministic fixture slices.
- Use controlled opt-in local model matrices to decide whether another index or provider path is justified.

MCP phase:

- Direct Spring AI tool call versus MCP tool call comparison.
- Local-only transport tests.
- Security and argument-validation behavior.

## Build Commands

```bash
./gradlew :setaccio-core:test
./gradlew :setaccio-lab:test
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

Commit every completed, in-scope change after appropriate verification. Keep
unrelated user changes unstaged and uncommitted. Do not push unless the user
explicitly asks; report the modified files and tests run with each commit.

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
- Confirm no private docs or generated outputs are staged.
- Confirm no `.DS_Store`, `.gradle`, or `build` outputs are tracked.
