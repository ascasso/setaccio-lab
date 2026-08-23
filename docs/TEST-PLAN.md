# Test Plan

The tracked [deferred-work index](DEFERRED-WORK.md) records the completed Phase
2 chat reuse proof and the remaining start gates for future providers,
retrieval, Testcontainers, model types, and MCP work. The Phase 2 closeout did
not place a live model call in default tests or CI.

## Near Term

- Keep `setaccio-core` Spring-free with a dependency check that fails if Spring Framework or Spring Boot appears on the core runtime classpath.
- Preserve direct unit coverage for both BLAKE3 implementations.
- Add known BLAKE3 test vectors for empty input, strings, byte arrays, and streams.
- Keep `setaccio-lab` context smoke tests on the `test` profile with no live Ollama or Anthropic calls.
- Add controller validation tests for missing files, missing model names, and malformed model lists.
- Maintain the implemented deterministic Spring AI evaluator contract. Follow
  the accepted local fact-checking and Testcontainers planning gate before
  adding either live path.

## Shared Evidence Lifecycle

- Keep the shared evidence primitives plain Java and independent of Spring application startup, model providers, and suite-specific result row types.
- Require a positive versioned manifest envelope with suite and run identity, generation time, Git commit and dirty state, Spring Boot and Spring AI versions, execution engine, run settings, and relative artifact descriptors.
- Allocate unique run directories atomically and refuse named-directory or manifest overwrites.
- Use BLAKE3 for benchmark input identity and streaming SHA-256 for generated artifact integrity.
- Reject absolute, parent-traversing, cross-platform absolute, or symbolic-link artifact paths.
- Verify saved runs entirely offline and report missing, modified, empty, duplicate, undeclared, or unsafe artifacts clearly.
- Keep manifest JSON free of hostnames, absolute paths, credentials, and raw private environment details.
- Keep shared saved-evidence file operations covered independently for
  non-overwriting writes, safe directory/path handling, artifact size/SHA-256
  checks, allowed-layout inspection, deterministic text verification, and
  atomic replacement.
- Reuse those file operations from vision, Tool Search, and local evaluation,
  but retain suite-specific result rows, analyzers, summaries, failure
  taxonomies, and identity/outcome schemas until compatible semantics are
  demonstrated by real consumers.
- Keep the locked Tool Search matrix on the shared v1 manifest while retaining
  legacy-v0 reads for existing unversioned matrix directories.
- Do not describe vision, chat, or evaluation output as using the shared
  manifest until each writer is migrated and tested separately.

## Vision Benchmark Phase

- Keep the tracked `vision-image-analysis` prompt named and versioned; require an
  explicit digest-test update when its bytes change.
- Maintain the reusable direct Spring AI invocation boundary with a mocked
  `OllamaChatModel`; default tests must not call Ollama.
- Verify per-request model, temperature, seed, and optional token-limit settings
  are passed through Spring AI options.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include prompt identity/digest, detected MIME type, model
  settings, input name, BLAKE3 hash, latency, token usage when available,
  output text, structural checks, invocation success, and classified failure
  details.
- Keep invocation success separate from deterministic required-section
  completion; structural checks do not measure semantic image understanding.
- Preserve the current multipart endpoint when optional generation settings are
  omitted, and use the neutral result host value `local`.
- Verify unavailable models, invalid inputs, empty responses, and provider
  exceptions produce classified failure rows rather than aborting a benchmark.
- Verify JSON result writing under ignored `build/lab-results/` output.
- Keep the tracked vision corpus template at version 1 with stable,
  non-sensitive case IDs and relative case-ID-based image filenames.
- Verify the template contains MIME type, BLAKE3 identity, human reference
  observation, expected concepts, unsupported details, deliberate limitations,
  and explicit privacy-review fields, all defaulting to unapproved.
- Keep personal images and filled case metadata under the explicitly ignored
  `setaccio-lab/local/vision-corpus/` directory; never use original filenames
  or absolute paths in the catalog or public evidence.
- Require sensitive-content and EXIF/GPS review plus explicit user approval
  before tracking any image or derivative.
- Keep the dedicated matrix reader specific to this exact corpus contract
  rather than introducing generic YAML/JSON suite discovery.
- Maintain the implemented dedicated corpus reader with strict unknown-field,
  case-ID, relative-path, MIME-byte, BLAKE3, non-empty-file, duplicate, symlink,
  and sensitive-content-review validation.
- Keep the opt-in matrix protocol fixed at sequential
  model/case/repetition execution, two repetitions, temperature `0.0`, seeds
  `42` and `43`, one explicit token policy, one explicit tracked prompt
  version, and Ollama pull strategy `never`.
- Require explicit model tags, the fixed ignored corpus directory, and one new
  dated output directory. Check the installed Ollama model list with pulling
  disabled, record each normalized name and full immutable digest, reject
  duplicate aliases for the same model bytes, and fail before output allocation
  when a requested tag is missing or its identity is incomplete; never attach
  `visionMatrix` to `test`, `check`, `build`, or CI.
- Keep raw vision evidence suite-specific and free of local paths, original
  filenames, reference observations, expected concepts, and unsupported-detail
  notes.
- Write raw JSON, a shared v1 evidence manifest, and deterministic
  `SUMMARY.md`; verify and reanalyze saved runs without Spring, corpus access,
  Ollama, or another provider.
- Keep invocation success, structural completion, human expected-observation
  review, human unsupported-detail review, repetition diagnostics, token
  availability, successful-invocation latency, and infrastructure failures as
  separate dimensions.
- With two repetitions, report median and observed latency range rather than
  percentiles. Keep structural completion and exact-output matching explicitly
  separate from semantic image understanding.
- After offline verification, record human expected-concept and
  unsupported-detail judgments separately per model/case, label them as human
  review rather than automated scores, include repetition/token/latency and
  infrastructure observations, and do not declare an aggregate winner.
  Pre-register the review criteria before reading candidate raw responses; use
  [`docs/VISION-HUMAN-REVIEW.md`](VISION-HUMAN-REVIEW.md) for the current
  public-safe rubric and
  [`docs/VISION-HUMAN-REVIEW-OPERATOR.md`](VISION-HUMAN-REVIEW-OPERATOR.md) for
  the committed execution checklist.
- Reject tampered or missing raw evidence, manifest protocol drift, unexpected
  artifacts, and summaries that differ from deterministic offline analysis.
- Verify that every row and manifest retain the explicitly selected prompt ID,
  version, and digest, and that offline verification/reanalysis selects either
  supported saved prompt version without starting Spring or a provider.
- Compare two verified saved runs offline only when their ordered model
  identities/digests, input identities, repetitions/seeds, temperature, token
  policy, row order, execution engine, Spring Boot and Spring AI versions, and
  all other non-prompt settings match. Test valid comparisons plus
  input/model/settings/framework mismatch and tampered evidence rejection; keep
  semantic judgments out of the deterministic report.
- Prepare human review only after that offline comparison gate passes. Validate
  the private corpus against saved MIME/BLAKE3 identities, group both prompt
  versions by model/case, collapse exact successful repetitions, retain
  differing repetitions, write only under ignored `build/vision-human-review/`,
  refuse overwrite, and leave every semantic field blank for a human.

## Chat Benchmark Phase

- Treat chat as the completed Phase 2 reuse surface and the later portability
  surface. Keep its default lifecycle local, no-pull, provider-free, and
  offline.
- Keep the implemented Anthropic O1 adapter provider-free by default. Mocked
  tests must cover option mapping, explicit unsupported seed behavior, present
  and absent usage, effective-model/safe-response-ID metadata, empty response,
  timeout, authentication, rate-limit, provider failure, one attempt, and no
  fallback. The separate O3 task may read `ANTHROPIC_API_KEY` only after its
  explicit user authorization, USD ceiling, saved-Ollama-evidence, and fresh
  output-directory preflight all pass.
- Keep the O2 portability projection raw-output-free: preserve the provider's
  identifier semantics (local digest versus hosted versioned ID), common option
  handling, usage, outcome, and one-attempt metadata, but never treat a hosted
  ID as a local digest or copy raw answers into a portability report. Record
  temperature and output tokens as direct support; timeout and retry policy as
  translated; seed as rejected and unsimulated; and no common option as silently
  ignored.
- Keep `anthropicChatMatrix` outside `test`, `check`, `build`, application
  startup, and CI. Its sole protocol is six sequential calls, three locked
  prompts times two unseeded repetitions, `claude-haiku-4-5-20251001`, `0.0`
  temperature, `128` output tokens, `PT2M`, and exactly one attempt. Require a
  current official-price worst-case calculation, an explicit USD ceiling no
  greater than the current authorization, a matching verified Ollama saved run,
  and a fresh ignored output directory before any remote call. Provider-free
  tests must cover cost/path/option preflight, row retention without retries,
  raw-output separation, and standalone offline verify/reanalyze.
- Keep the internal chat invocation contract provider-neutral for provider and
  requested/effective model identity, prompts, common generation settings,
  explicit supported/unsupported option metadata, and invocation outcomes.
  Keep Ollama's full digest and seed semantics on the Ollama identity/adapter
  instead of presenting them as universal provider capabilities.
- Verify the Ollama adapter with mocked `ChatModel` behavior: explicit model,
  temperature, seed, and token options; raw response and optional usage;
  latency and one attempt; and empty-response, unavailable-model, timeout, and
  provider-failure classification. Verify its model factory remains loopback,
  pull strategy `never`, and one attempt without contacting Ollama.
- Maintain the dedicated local-only chat benchmark surface at `POST /api/lab/chat`.
- Keep service-level tests backed by a mocked `OllamaChatModel`; do not add live Ollama calls to default tests.
- Verify per-request model selection is passed through Spring AI chat options for every prompt/model pair.
- Verify default prompts and caller-provided prompt lists through controller tests.
- Verify result rows capture provider/model, prompt id/text, advisor mode, latency, output, failure details, and token-usage metadata when Spring AI exposes it.
- Verify JSON result writing under ignored `build/lab-results/` output as `*-chat.json`.
- Keep the three current default prompts locked in the tracked v1 matrix
  catalog with stable IDs, exact bytes, deterministic order, catalog SHA-256,
  per-prompt SHA-256, and explicit parity with the unchanged interactive
  defaults.
- Test the dedicated `chatMatrix` path separately from the interactive service: one
  explicit installed model identity/digest, two sequential repetitions,
  temperature `0.0`, seeds `42`/`43`, explicit token limit and timeout, one
  attempt, no pull, six rows, non-overwriting ignored output, shared manifest,
  and offline verify/reanalyze behavior.
- Keep `chatMatrix` outside `test`, `check`, `build`, application startup, and
  CI. Keep `chatMatrixTest`, `chatMatrixVerify`, and `chatMatrixReanalyze`
  provider-free; verification and reanalysis must not start Spring, read the
  interactive endpoint, inspect provider state, or make a provider call.
- Reject contract/catalog/model/schedule/settings drift, missing or replacement
  rows, selective retries, tampered raw artifacts, summary drift, undeclared
  files, unsafe paths, and incomplete usage metadata. Deterministic summaries
  must not judge semantic response quality or rank the single model.
- Keep existing endpoint request/response behavior unchanged unless dedicated
  parity tests justify migration to the new boundary.

## Optional Integration Tests

- Keep live Ollama and remote-provider tests opt-in behind a dedicated Gradle property or profile.
- Require explicit provider and model names for live runs so CI never pulls models or calls providers implicitly.
- Require provider credentials through local environment variables or ignored local config.
- Keep provider and model-type environment variables documented in `docs/ENVIRONMENT.md`.
- Record live-run outputs under ignored build directories only.

## Evaluation Testing

- Maintain the dedicated local-only `POST /api/lab/evaluations` fixture benchmark with no live model or provider call.
- Use Spring AI's `Evaluator` contract for deterministic fixture rows before adding AI-judged evaluation.
- Track each evaluation input as user text, optional context/data, model response, evaluator provider/model, pass/fail result, score, raw evaluator explanation, and evaluator metadata.
- Keep public fixtures deterministic and make the evaluator implementation and required terms explicit in result metadata.
- Lock the dedicated fact-check prompt ID, version, raw-byte SHA-256, and exact
  single `{document}` / `{claim}` placeholders.
- Lock the fact-check catalog ID, version, raw-byte SHA-256, ordered stable IDs,
  three pair structure, and three-supported/three-unsupported balance. Require
  non-blank repository-authored document and claim text.
- Require the tracked actual-human confirmation record to match the exact
  catalog ID, version, SHA-256, confirmation date, and all six fixture IDs in
  catalog order. Reject pending, incomplete, or digest-mismatched records in
  offline tests before a future runner can consume them.
- Maintain the implemented `FactCheckingEvaluator` recording boundary against
  the balanced public claim/context fixture cohort and an explicitly selected
  host-Ollama judge. Keep live execution separately authorized. Follow
  [`docs/LOCAL-AI-EVALUATION-PLAN.md`](LOCAL-AI-EVALUATION-PLAN.md).
- Defer `RelevancyEvaluator` until a real retrieval flow can supply and preserve
  retrieved documents; ordinary fixture context is not a RAG benchmark.
- Keep judge and fixture-expectation results separate. A valid `yes` / `no`
  verdict is not automatically an expectation match or a general factuality
  score.
- Because Spring AI `2.0.0`'s fact-checking response does not retain raw judge
  text or usage metadata, capture the dedicated judge model response through a
  narrow request-scoped recording boundary before evaluator normalization.
  Do not duplicate the evaluator implementation to obtain that evidence.
- Require the dedicated boundary to propagate explicit model, temperature,
  seed, token limit, timeout, and exactly-one-attempt policy on every call.
  Keep pull strategy `never`, reject non-loopback endpoints, and never inherit
  the judge from `OLLAMA_MODEL` or another application default.
- Keep mocked coverage for exact `yes` / `no` verdicts, empty and malformed
  output, expectation mismatch, unavailable model, timeout, provider failure,
  response metadata, available/absent token usage, latency, attempt count, both
  repetition seeds, timeout propagation, and hidden-retry prevention.
- Require prompt ID/version/digest, full judge-model digest, complete generation
  settings, two seeded repetitions, balanced supported/unsupported fixtures,
  explicit execution order, and shared-manifest offline verification.
- Keep `localEvaluationTest` provider-free. Require exact counterbalanced row
  order, BLAKE3 document/claim identities, separate evaluator/verdict/agreement
  signals, exhaustive diagnostic coherence, usage/latency/attempt validation,
  and public-safe metadata/error persistence.
- Require `localEvaluationVerify` and `localEvaluationReanalyze` to remain
  standalone and offline. Reject raw/summary tampering, missing or extra
  artifacts, unsafe paths, prompt/catalog/review/model drift, row order/count
  drift, invalid attempts, unclassified failures, and deterministic summary
  drift without starting Spring or contacting Ollama.
- Keep the `localEvaluation` runner opt-in and outside `test`, `check`, `build`,
  application startup, and CI. Provider-free tests must reject every missing
  option, unknown/duplicate options, non-loopback or structured endpoints,
  invalid token/timeout bounds, unsafe/reused output paths, absent/unconfirmed
  or digest-drifted contracts, missing models, incomplete digests, and
  mismatched resolved names before output allocation.
- Test the executor with a fake judge session: exactly twelve calls in locked
  sequential order, seeds `42`/`43`, one attempt per row, no replacement call,
  and retention of classified failed attempts. Never start Ollama in these
  tests.
- Keep evaluator models configurable and separate from the model being tested;
  record and flag identical full digests if a later benchmark self-evaluates.
- Keep deterministic fixture-based assertions for default tests. AI-judged evaluator tests must be opt-in unless backed by mocks or recorded fixtures.
- If custom evaluator prompts are added, keep prompt templates public-safe and test the required placeholders.

## Tool Calling and Tool Search

- Keep Spring AI Tool Search Tool support disabled for default tests and normal local runs.
- Maintain the dedicated local-only tool-calling benchmark surface at `POST /api/lab/tools`.
- Use mocked `OllamaChatModel` service tests for automated coverage; do not add live Ollama integration tests to the default Gradle lifecycle.
- Maintain mocked comparison coverage that runs paired standard `ToolCallingAdvisor` and `ToolSearchToolCallingAdvisor` executions sequentially with the same models, prompts, selected tools, and deterministic generation settings.
- Keep Tool Search comparison behind `SETACCIO_LAB_TOOL_SEARCH_ENABLED=true`, and reject standalone `tool_search` requests so each Tool Search result includes its standard baseline.
- Maintain local deterministic tools and expectation-aware cases for arithmetic, fixed date/time, catalog lookup, multi-step behavior, no-match behavior, tool abstention, and controlled callback failure. Do not call live network services from default tests.
- Verify Spring AI `ToolCallback` metadata for the deterministic fixtures so future advisor comparisons can use stable tool names and input schemas.
- Keep `regex` as the only executable index until separate, deterministic comparison coverage is added for another index. Keep `lucene` and `vector` rejected; `vector` also needs a public-safe `VectorStore` fixture.
- Verify result rows capture selected advisor mode, repetition, pair order, effective seed, requested tools, executed tools, tool errors, normalized Tool Search queries/discoveries, named contract assertions, model/provider, prompt, output, latency, and any token-usage metadata exposed by Spring AI.
- Keep comparison order counterbalanced across repetitions by default, and retain a test proving the standard-first/tool-search-first alternation.
- Verify repeated writes with the same suite and start instant produce distinct result files rather than overwriting an earlier run.
- Keep live model runs behind the `local` profile, explicit API calls, or the dedicated opt-in `toolSearchSmoke` task. Do not attach that task to `test`, `check`, `build`, or default CI.
- Require `toolSearchSmoke` to receive an explicit already-installed Ollama model, force `spring.ai.ollama.init.pull-model-strategy=never`, and select cases directly from `ToolBenchmarkCases.defaults()` with the complete `ToolBenchmarkCases.toolNames()` fixture set.
- Keep `toolSearchSmokeTest` offline. Cover semantic and ordinal case selection, live-wrapper parsing fixtures, raw-to-normalized discovery comparison, malformed results, trace-linkage failures, and every console summary bucket without starting Ollama.
- Fail the live smoke task only for startup/invocation failures, malformed results, missing trace linkages, or raw-versus-normalized discovery mismatches. Treat missing searches, zero matches, required-but-unexecuted tools, and output-contract failures as diagnostic model behavior unless an explicit hypothesis says otherwise.
- Keep the post-fix three-model baseline in the separate `toolSearchMatrixBaseline` task with the July 12 models, five canonical case IDs, two repetitions, seeds 42/43, temperature 0.0, no token ceiling, alternate order, paired sequential execution, and complete fixture tool list locked in code.
- Require a new dated `build/tool-search-matrix/` output directory and retain one raw comparison JSON, shared v1 `manifest.json`, and `SUMMARY.md`; refuse overwrites and verify both generated artifacts by size and SHA-256.
- Keep `toolSearchMatrixVerify` and `toolSearchMatrixReanalyze` standalone and
  offline: neither may start Spring, contact a provider, or join the default
  Gradle lifecycle.
- Verify both shared v1 and legacy-v0 saved matrix directories, reject tampered
  or missing raw results and locked-protocol manifest drift, and require
  byte-for-byte deterministic summary regeneration.
- Verify every linked non-empty raw Tool Search discovery exactly matches normalized tool names. Treat malformed/linkage/mismatch conditions and unclassified failed contracts as matrix-integrity failures.
- Classify failed canonical contracts exhaustively as no search call, zero discovery, incomplete discovery, discovered-not-executed, execution failure, or output-contract failure. Preserve precedence tests and a successful zero-discovery abstention test.
- Compare post-fix counts to both recorded and corrected July 12 counts, and require the summary to name corrected request construction and Issue #20 discovery normalization as confounders. Do not present Issue #21's chat fix as a tool pass-rate cause.

## Small-Model Tool Compatibility

- Follow the locked
  [`SmallModelToolCallingCompatibilityPlan.md`](SmallModelToolCallingCompatibilityPlan.md)
  one slice at a time. T0.1 and provider-free T1.1-T1.8 are complete, and the
  Phase 1 live baseline has been verified and reanalyzed offline.
- Phase 1 must use dedicated `toolCompatibility` and
  `toolCompatibilityTest` source sets under `com.setaccio.lab.toolcompat`; do
  not migrate or alter `POST /api/lab/tools`.
- Lock the untreated matrix at one explicit LFM2.5 tag, standard
  `ToolCallingAdvisor`, all eight canonical cases in tracked order, all
  canonical fixture tools, two repetitions, seeds `42`/`43`, temperature
  `0.0`, `512` output tokens per provider turn, one `PT2M` whole-row deadline,
  one logical attempt with ordered recursive provider turns, no pull, and
  exactly 16 sequential rows.
- Bind the canonical cases to the plan's exact versioned semantic call oracle.
  Check exact ordered calls and JSON-typed argument values independently of raw
  JSON validity, declared-schema validity, callback coercion, callback success,
  and final-output assertions; reject oracle drift before output allocation.
- Keep raw argument JSON validity, declared-schema validity, callback binding,
  callback execution, callback success, final response, and case contract as
  separate signals. Validate the current fixture schema subset before callback
  binding; accept and ignore only `description`, `title`, `$schema`, and
  `default` metadata, and fail preflight on any other unsupported schema
  keyword or shape rather than silently approximating validation.
- Keep `toolCompatibilityTest` wholly provider-free. Cover the exact preflight,
  multi-turn ordering/linkage, semantic argument, row-timeout/no-overlap,
  execution, observability, analysis, evidence-integrity, task-isolation, and
  deterministic-reanalysis requirements in the plan before any live command is
  reviewed. Do not lock the final row schema before the standard-advisor
  observability proof succeeds.
- Require `toolCompatibilityMatrix`, `toolCompatibilityPromptMatrix`,
  `toolCompatibilityVerify`, `toolCompatibilityReanalyze`, and
  `toolCompatibilityCompare` to stay outside `test`, `check`, `build`,
  application startup, and CI. The provider-free
  `toolCompatibilityCohortVerify` and `toolCompatibilityCohortReanalyze` tasks
  must remain equally isolated. Verify/reanalyze/compare must not start Spring,
  resolve a model, or contact Ollama.
- The completed Phase 1 baseline is recorded under the ignored
  `setaccio-lab/build/tool-compatibility/2026-08-20-lfm-baseline/` directory.
  Its public-safe interpretation is limited to the observed provider-turn
  failure boundary; no quality, reliability, production, or ranking claim is
  allowed. The authorized Phase 2 paired run is recorded under the two ignored
  `2026-08-21-lfm-prompt-untreated` and `2026-08-21-lfm-prompted` directories;
  both verify and reanalyze offline, and `toolCompatibilityCompare` passes.
  Both conditions reached the same first `PROVIDER_FAILURE` boundary on every
  row, so no prompt-effect or quality conclusion is permitted. The T2.5 human
  decision worksheet remains blank. Separately authorized provider-free Phase
  3 preparation must cover ordered peer/reference resolution, full digest and
  runtime-version checks, duplicate tags/bytes and mutable aliases, local-only
  execution, explicit unavailable metadata, mixed artifact formats, and
  failure before output allocation. Its prompt-policy tests must cover all four
  owner decisions and reject decision-binding drift. No test may select the
  owner's actual decision, contact Ollama, or execute a live cohort model. T3.3
  provider-free tests must cover exact model-major order/count, no cross-model
  advisor state, supported and unsupported seed recording, no thinking
  override, runtime drift, retained provider failures, strict evidence layout,
  tampering, offline verification/reanalysis, and default-lifecycle isolation.
  T3.4 provider-free tests must cover every per-model analysis dimension,
  deterministic model/case order, omitted/unknown/incorrect arguments,
  multi-step continuation/order/premature-final/duplicate observations,
  deterministic failure retention plus bounded lexical recovery markers,
  response-format markers and final-response length, successful-row
  median/range, complete and incomplete token coverage, tokens-per-passing-row
  gating, mixed artifact formats, a golden report digest, and the absence of a
  winner/ranking/leaderboard section.
- Treat incomplete standard-advisor observability as a stop-and-review result.
  Do not introduce a lower-level custom tool execution loop or widen existing
  interactive classes to bypass that gate.
- Before any Phase 2 comparison, require both prompt conditions to originate
  from the same clean Git commit and carry an identical paired-execution
  schedule identity that proves the locked interleaved alternating order. Stop
  and restart both conditions from a new clean commit if either worktree is
  dirty or the commit changes between conditions.
- In the Phase 2 comparator, treat `globalPairSequence` and
  `conditionExecutionPosition` as schedule-derived per-condition values rather
  than direct-equality fields. Accept their expected baseline/candidate
  differences only when each row matches the shared schedule for its condition,
  case, and repetition; reject missing, swapped, equal, or otherwise
  schedule-inconsistent values.
- Implement Phase 2 live execution only as the dedicated
  `toolCompatibilityPromptMatrix` task. One invocation must preflight both fresh
  outputs before allocation, execute all 32 logical attempts in the locked
  interleaved order, and write two independently verifiable 16-row runs with one
  shared schedule identity. Re-check the original commit and clean-worktree
  state before every row and before manifest finalization; drift aborts both
  runs as incomplete. Never substitute two whole-condition commands or change
  the Phase 1 matrix CLI.

## Testcontainers

- Keep Docker/Testcontainers dependencies isolated in `setaccio-testcontainers`; `setaccio-lab` must not require them.
- Keep the existing Spring AI `spring-ai-spring-boot-testcontainers` support in
  test scope. Its Ollama service-connection factory does not itself add the
  typed Testcontainers Ollama module.
- Add a typed `OllamaContainer` dependency only in `setaccio-testcontainers`
  and only when a separately authorized container-specific slice needs it.
- Prefer Spring Boot `@ServiceConnection` wiring and verify that the resulting
  Ollama connection details override ordinary connection properties.
- Use `OllamaContainer` only for explicit integration tests; do not make Docker or model pulls required for normal builds.
- Relevant future service connections include Ollama, local/vector stores such as Chroma, Milvus, Qdrant, Typesense, Weaviate, and infrastructure such as OpenSearch or LocalStack when those test surfaces are added.
- Keep Testcontainers tests behind a dedicated Gradle task, profile, or property so local unit tests and CI remain fast and offline by default.

## Later Phases

- Add structured-output reliability tests for text prompts.
- Add provider comparison tests for Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama as integrations are added.
- Add Anthropic-specific chat tests for default options, per-request option overrides, streaming, multimodal image/PDF inputs, tool choice/tool calling, and extended thinking constraints.
- Add Google GenAI-specific chat tests for Gemini Developer API key mode, Vertex AI mode, multimodal prompts, response MIME type, Google Search grounding, server-side tool invocation metadata, safety settings, cached content, thought signatures, and thinking option compatibility.
- Add model-type tests for chat completion, embedding, text to image, audio transcription, text to speech, and moderation.
- Add detailed local Ollama setup docs before adding required Ollama live-test workflows.
- Add a dedicated `setaccio-testcontainers` integration-test task before adding Docker-backed tests.
- Add MCP tests after direct Spring AI tool-calling tests are reliable.
