# Changelog

All notable changes to `setaccio-lab` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Added plain-Java `EvidenceFiles` operations for non-overwriting saved-artifact
  writes, directory/path validation, artifact size/SHA-256 checks, saved-run
  layout inspection, deterministic summary verification, and atomic summary
  replacement. Vision matrix, Tool Search matrix, and local evaluation now
  share those operations without changing their saved formats or suite-specific
  schemas.
- Added `docs/DEFERRED-WORK.md` as the public-safe canonical index for work
  intentionally outside the completed August cycle. It now records the active
  Phase 2 chat boundary plus the Prompt v2, output-budget, Testcontainers,
  retrieval, release/tag, and Phase 3 Anthropic gates; links the environment
  and test-plan boundaries; and makes clear that roadmap entries do not
  authorize live execution, credentials, Docker, a release, or a tag.
- Closed the first local fact-check cycle with a bounded offline interpretation
  of the immutable A5 evidence. Supported agreement was not measurable; two
  unsupported rows were evaluable and both agreed, one fixture had two valid
  consistent verdicts, and five repetition comparisons were incomplete. All
  ten empty responses reached the explicit `64`-token output limit, registering
  a later separately designed output-budget compatibility hypothesis without
  claiming causation, statistical reliability, general factuality, or judge
  ranking. Testcontainers, release, and tag work remain deferred.
- Completed the first controlled local fact-check run from clean commit
  `5d41362` with explicit `gemma4:e2b` identity, full immutable digest, `64`
  output tokens, `PT2M`, and the locked twelve-row one-attempt schedule. All
  provider invocations completed with usage metadata and no infrastructure
  failure; ten rows had empty output and two produced valid matching `no`
  verdicts. Ignored evidence verified and reanalyzed byte-for-byte offline
  without selective retry, replacement, model pull, or raw-output publication.
- Added the opt-in `localEvaluation` host-Ollama runner with explicit loopback
  URL, installed judge tag, token limit, timeout, and fresh dated output
  inputs. Preflight locks the confirmed prompt/fixture/review contract and
  resolves a full immutable model digest before output allocation; execution
  remains twelve sequential one-attempt, no-pull rows outside every default
  lifecycle. Provider-free tests cover all option, preflight, model identity,
  allocation, call-order, and failed-row behavior, and dirty Git provenance is
  labeled diagnostic/non-final in the deterministic summary.
- Added the offline local fact-check evidence lifecycle: a locked twelve-row
  counterbalanced schedule, BLAKE3 document/claim identities, full judge and
  contract provenance, suite-specific raw JSON, shared v1 manifest, and
  deterministic summary. Standalone `localEvaluationVerify` and
  `localEvaluationReanalyze` tasks validate or regenerate saved evidence
  without starting Spring or contacting Ollama; offline tests cover protocol,
  outcome, integrity, path-safety, public-safety, and summary-drift failures.
- Added the dedicated local fact-check recording judge boundary around Spring
  AI's unchanged `FactCheckingEvaluator`. It requires complete explicit Ollama
  settings, a loopback endpoint, pull strategy `never`, connect/read timeout,
  and exactly one attempt; records raw output, response metadata, available
  usage, latency, and classified failures before evaluator normalization; and
  keeps provider success, judge verdict, Spring's boolean, and expected-label
  agreement separate in provider-free default tests.
- Added the first offline local fact-checking contract: a versioned
  exact-placeholder prompt, a balanced six-fixture repository-authored catalog,
  and an actual-human confirmation record bound to the exact catalog digest.
  Deterministic tests lock all identities and reject pending, incomplete, or
  digest-mismatched review records without invoking a model provider.
- Added a committed vision human-review operator companion with the exact
  preparation, worksheet-entry, decision, and public-closeout sequence while
  retaining `docs/VISION-HUMAN-REVIEW.md` as the canonical policy source.
- Added the planning-only local AI-judged evaluation gate: a bounded future
  host-Ollama fact-checking matrix contract, reproducibility and failure
  criteria, and a separately deferred Testcontainers service-connection path
  that preserves the module boundary and offline default lifecycle.
- Added `visionHumanReviewPrepare`, an offline-only task that verifies and
  compares two saved vision runs, validates their inputs against the ignored
  private corpus, and writes one non-overwriting private Markdown worksheet
  under ignored `build/vision-human-review/` output. The worksheet presents
  paired raw responses and the pre-registered human rubric without automated
  semantic scoring.
- Added `visionMatrixCompare`, an offline-only comparison task for two verified
  saved vision runs. It rejects non-prompt protocol or identity drift and
  reports deterministic invocation, structural, repetition, token, latency,
  and infrastructure deltas without semantic scoring.
- Added explicit tracked prompt-version selection to the opt-in vision matrix;
  every row and manifest retain the selected prompt identity, while offline
  verification and reanalysis select either supported saved prompt version.
- Added optional explicit approved case-ID selection to the opt-in vision
  matrix for controlled subsets, retaining full-corpus execution when omitted.
- Added a shared benchmark evidence lifecycle foundation with versioned manifests, unique non-overwriting run directories, Git and framework provenance, relative artifact descriptors, streaming SHA-256 integrity metadata, and strict offline saved-run verification.
- Migrated the locked Tool Search matrix to the shared v1 evidence manifest and
  added standalone offline verification and deterministic summary reanalysis
  for both v1 and existing unversioned legacy-v0 saved runs.
- Added a reusable direct Spring AI vision invocation contract with a tracked
  versioned prompt and SHA-256 identity, explicit Ollama generation settings,
  token metadata, deterministic required-section checks, classified failures,
  and a backward-compatible multipart endpoint.
- Added a versioned public-safe local vision corpus template with six stable
  non-sensitive case IDs, explicit privacy-review fields, fixed ignored local
  layout guidance, and offline contract coverage without tracking personal
  images or filled metadata.
- Added an opt-in sequential vision matrix with strict fixed-corpus validation,
  explicit installed-model and token-policy inputs, locked temperature and
  seeds, no-pull Ollama execution, immutable resolved model digests,
  suite-specific raw results, shared v1 evidence manifests, deterministic
  summaries, and standalone offline verification and reanalysis.
- Added the Slice 7 closeout for the first controlled four-case vision matrix:
  human semantic and unsupported-detail observations are documented separately
  from automated structural results, with repetition, token, latency, and
  infrastructure findings and no aggregate model winner.
- Wired the local vision benchmark endpoint through a public Spring AI/Ollama service that hashes inputs with `setaccio-core`, returns one row per file/model pair, and writes raw JSON results under `build/lab-results/`.
- Added public-safe lab server support for upload temp-file handling, MIME detection, Caffeine-backed result caches, and benchmark output configuration.
- Added environment variable documentation for `SETACCIO_LAB_INPUT_DIR` and `SETACCIO_LAB_OUTPUT_DIR` to support local image comparison workflows.
- Added disabled-by-default Spring AI Tool Search Advisor dependency and planning docs for future tool-calling benchmarks.
- Added a Gradle verification guard that fails if `setaccio-core` runtime dependencies include Spring Framework, Spring Boot, or Spring AI artifacts.
- Added deterministic Spring AI tool fixtures for arithmetic, fixed time, and small public-safe catalog lookups to seed future tool-calling benchmarks.
- Added an opt-in local Ollama chat benchmark endpoint that runs text prompts across explicit model lists without tools, records provider/model/prompt metadata and token usage where available, and writes `*-chat.json` results under `build/lab-results/`.
- Added an opt-in local Ollama tool-calling benchmark endpoint that runs deterministic tool prompts across explicit model lists, records standard Spring AI tool-calling observations where exposed, and writes JSON results under `build/lab-results/`.
- Added an opt-in Tool Search comparison mode for the local tool benchmark. It runs standard and regex Tool Search advisor modes sequentially against the same request fixtures and persists one structured comparison result without an aggregate winner score.
- Added an explicitly opt-in `toolSearchSmoke` Gradle diagnostic that requires an already-installed Ollama model, forces the no-pull strategy, validates live Tool Search wrapper/trace integrity, and keeps model behavior categories non-blocking.
- Added a locked post-fix Tool Search matrix task that reproduces the July 12 protocol from canonical Java cases, verifies raw/normalized discovery parity, classifies failures exhaustively, and writes dated raw, manifest, and Markdown comparison artifacts with the request-construction confounder made explicit.
- Added expectation-aware public tool benchmark cases for single-step, multi-step, no-match, abstention, and deterministic callback-failure behavior, with named assertions for required/forbidden tool execution and required output/response terms.
- Added normalized Tool Search observations that retain each search query, completion state, and discovered tool names alongside raw calls and responses.
- Added repeatable tool benchmark settings for repetitions, temperature, base seed, optional token limit, and comparison order, with effective seeds and pair order recorded per row.
- Added a local-only deterministic fixture evaluation endpoint using Spring AI's `Evaluator` contract, with structured pass/fail rows and JSON output under `build/lab-results/` but no live provider call.
- Improved tool-calling benchmark token accounting to accumulate Spring AI usage metadata across advisor loop iterations, and moved tool injection to the portable `ChatClient` request API.
- Initial public repository skeleton with `setaccio-core` as a plain Java library and `setaccio-lab` as a Spring Boot / Spring AI application.
- Added `setaccio-testcontainers` as an optional skeleton module for future Docker/Testcontainers-backed integration tests.

### Changed

- Closed the Phase 2 start gate after the project owner selected the existing
  chat benchmark as the reuse and later portability surface. Slice S1 is now
  active on a dedicated feature branch under local, no-pull, provider-free
  constraints; Anthropic credentials/calls, default-lifecycle live execution,
  automatic pulls, and untested endpoint migration remain unauthorized.
- Updated repository agent workflow guidance to commit completed in-scope work
  after appropriate verification, while keeping pushes explicitly user
  authorized and unrelated user changes out of commits.

- Closed the unavailable Prompt v1/v2 comparative human-review prerequisite
  through a documented evidence-loss waiver after the ignored saved-run
  directories could not be restored. No human adopt/revise/reject judgment is
  claimed: Prompt v1 remains the operational default, Prompt v2 remains
  experimental and unadopted, and any future decision requires new paired
  controlled evidence plus actual human review.
- Added `gradle/libs.versions.toml` for shared Gradle dependency versions and updated the module build scripts to use version catalog aliases.
- Moved repository declaration into Gradle dependency resolution management and made module test dependencies explicit.
- Let Spring Boot/Spring AI dependency management provide the `jackson-datatype-jsr310` version, moved `bcprov-jdk18on` to the Gradle version catalog, restricted the optional Testcontainers harness dependency on `setaccio-lab` to test scope, and enabled Gradle repository-mode enforcement.
- Upgraded Spring Boot to `4.1.0` across the Gradle build.
- Aligned `commons-codec` to `1.22.0` and `slf4j-api` to `2.0.18` across the modules that use them.
- Upgraded the Gradle wrapper from `9.6.0` to `9.6.1`.
- Changed the default local Ollama chat/vision model to `gemma4:e2b`.
- Upgraded `setaccio-lab` from Spring AI `2.0.0-RC1` to `2.0.0`.
- Clarified that `setaccio-lab` is also the public showcase/reference surface for reusable AI/file-processing work, while private Setaccio product code stays out of the repository.
- Upgraded `setaccio-lab` from Spring AI `2.0.0-M4` to the Spring AI 2.0 line.
- Expanded public documentation to describe the intended Spring AI provider and model-type test harness scope.
- Added environment variable documentation for current and planned live provider/model tests.
- Added `OLLAMA_API_BASE` as a supported fallback alias for local Ollama configuration.
- Clarified that `SETACCIO_LAB_INPUT_DIR` is optional and has no default path when unset.
- Added Spring AI evaluation testing and Testcontainers planning notes.
- Added AssertJ as the shared test assertion library.
- Documented Spring AI Anthropic chat configuration and future Anthropic-specific test surfaces.
- Documented Spring AI Google GenAI credential mapping and future Google-specific test surfaces.
- Expanded Google GenAI notes for grounding, server-side tool metadata, cached content, thought signatures, and thinking option compatibility.
- Changed Tool Search comparison execution to paired sequential runs that alternate advisor order across repetitions by default.
- Made benchmark result filenames collision-safe with nanosecond timestamps, unique run identifiers, and non-overwriting file creation.

### Fixed

- Added an exception rule `!gradle/wrapper/gradle-wrapper.jar` to `.gitignore` and restored `gradle-wrapper.jar` so Gradle wrapper execution (`./gradlew`) remains tracked and functional across branch switches.
- Reject vision-matrix comparisons and human-review worksheet preparation when
  saved runs have different Spring Boot or Spring AI versions, preventing
  framework-induced behavior from being attributed to prompt changes.
- Clarified the required `visionHumanReviewPrepare` options with a short
  workflow containing the exact current baseline, candidate, command, and
  worksheet paths, and made a bare task invocation report all required options
  plus the review-guide pointer in one error. Consolidated field-entry formats,
  comparison vocabulary, and final-decision guidance into that canonical guide.
- Parse Spring AI `ToolSearchResponse.toolReferences` entries when recording discovered tool names, so valid Tool Search runs are not marked as discovery failures.
- Record null or result-less Ollama chat responses as failed benchmark rows instead of successful rows without model output.
