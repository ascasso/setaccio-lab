# Changelog

All notable changes to `setaccio-lab` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Migrated the formal evidence lifecycle to a durable private root. Every
  suite now allocates new run evidence only under
  `setaccio-lab/local/evidence/<suite>/<run-id>/`, which is ignored but is not
  a Gradle output directory, so `./gradlew clean` cannot remove it. A new
  shared `EvidenceSuiteRoot` contract owns the root, direct-child, path
  traversal, and symbolic-link policy for all twelve suite roots
  (`chat-matrix`, `anthropic-chat-matrix`, `evaluation-matrix`,
  `retrieval-evaluation`, `retrieval-embedding`, `retrieval-answer`,
  `retrieval-relevancy`, `tool-compatibility`,
  `tool-compatibility-human-review`, `tool-search-matrix`, `vision-matrix`,
  `vision-human-review`), replacing twenty-plus independent reimplementations;
  each suite keeps only its own date and run-id rule. Every writer, verifier,
  reanalyzer, comparison, cohort frontier, human-review preparer, paired-arm,
  multi-arm, and downstream source-evidence consumer now resolves through it.
  CLI option names and explicit-path behavior are unchanged. Readers still
  accept a legacy `build/<suite>/<run-id>` path so evidence saved before this
  change can be verified, reanalyzed, compared, and consumed; that acceptance
  is read-only and authorizes no rewrite, repair, reanalysis into, or move of
  old evidence. Direct-child, date/run-id, traversal, symlink, non-overwrite,
  and fresh-allocation safeguards are preserved, and the Tool Search reader,
  previously the loosest, is now held to the same direct-child rule. Ordinary
  interactive endpoint output under `build/lab-results/`, compilation output,
  and unrelated generated files are untouched. Before the path change the
  surviving Phase 5 R4 run was copied byte-for-byte from
  `setaccio-lab/build/retrieval-embedding/2026-09-02-r4-qwen3-embedding-0-6b/`
  into the durable root; both manifest-listed artifacts verified against the
  copied manifest, all three files compared identical, and the recomputed
  source hashes were unchanged. No run, rerun, reanalysis, verifier
  invocation, model call, or evidence mutation occurred. Recorded in
  `docs/logs/2026-09-03-durable-evidence-retention.md`.

- Recorded read-only `ollama show` capability observations for the four
  already-installed artifacts that appear in retained closeouts, in
  `docs/logs/2026-09-02-model-capability-observations.md`. Every empty-response
  run used one artifact, `gemma4:e2b` at digest `7fbdbf8f5e45`, which advertises
  `thinking`; the R6 relevancy matrix ran at the same `64`-token budget against
  `granite4.1:3b`, which does not, and recorded no empty response. The Phase 1
  and Phase 2 artifact advertises `completion` only, without `tools`, while both
  phases exercised the standard `ToolCallingAdvisor`. These are associations
  from manifest metadata read under a later Ollama runtime than some of those
  runs used. They are hypotheses, not results; no experiment was started, no
  model was invoked or modified, and no closeout is retracted or reinterpreted.

- Published the first tracked evidence example under `docs/evidence/`. It holds
  byte-identical copies of the Phase 5 R4 run's deterministic `SUMMARY.md` and
  `manifest.json`, with the raw result artifact deliberately omitted. This moves
  the documentation publication boundary to allow tracked summaries and
  manifests while raw output, vectors, and model responses remain ignored. A
  publication copy is partial by construction and does not pass
  `retrievalEmbeddingVerify`; `docs/evidence/README.md` records why and what a
  reader can check instead. No evidence was rerun, repaired, replaced,
  reanalyzed, or mutated.

- Completed the explicitly requested Phase 5 R4 embedding-retrieval run on
  2026-09-02 from clean commit `4c13b4a` using the prioritized
  `qwen3-embedding:0.6b` tag, which the project owner pulled that day before
  authorizing the run. Read-only inspection confirmed the tag's literal
  `embedding` capability and full digest before any evidence directory was
  allocated. One batch of twelve corpus documents and fourteen confirmed
  queries produced 1024-dimension unit-L2 vectors and deterministic top-K `5`
  cosine rankings; the ignored evidence passed generation-time integrity
  analysis and offline verification. This sets no retrieval-support threshold
  and establishes no embedding quality, semantic relevance, or model ranking.

### Fixed

- Treated Spring AI's `EmptyUsage` marker as unavailable in the vision
  invocation boundary instead of recording synthetic zero-token usage.
  `ChatResponseMetadata` defaults its usage field to `EmptyUsage`, so the
  previous null-only check never applied. The chat, tool, and evaluator paths
  were already adapted.
- Stopped the vision boundary from discarding configured Ollama defaults.
  `OllamaChatModel.buildRequestPrompt` substitutes model defaults only when a
  prompt carries no options, so the previous partial options object silently
  replaced every configured default. The boundary now materializes a complete
  options object from the model defaults and applies the requested model and
  any explicit temperature, seed, or token setting over it. This was a standing
  defect, identical in Spring AI `2.0.0` and `2.0.1`, not an upgrade
  regression, and the direct-call vision protocol is unchanged.
- Rejected Phase 5 R6 evaluator responses whose retained nonblank provider model
  differs from the locked effective evaluator model, preventing model-identity
  drift from being attributed to the approved digest. R5 and R6 deterministic
  summaries now also preserve exact fractional timeout values instead of
  truncating them to whole seconds.
- Hardened Phase 5 R6 offline evidence validation so successful evaluator
  outcomes must reproduce their normalized verdict and diagnostic from the
  retained raw response, while failed invocations must retain a classified
  provider failure. This prevents inconsistent evaluator observations from
  being accepted as verified evidence.
- Hardened Phase 1 tool-compatibility evidence validation so every saved row
  must match the result's locked untreated system-prompt identity. Prompt-matrix
  evidence remains condition-specific and continues to validate each row
  against its declared prompt condition.

### Changed

- Replaced the three separate restatements of the commit rule in `AGENTS.md`
  with one `Standing Work Loop` section covering verify, document, log, commit,
  and naming pushing as separately gated. The documentation and logging
  requirement was previously a convention visible only in commit history, not a
  stated rule.
- Reconciled three `AGENTS.md` statements that contradicted the new Publication
  Boundary, including a pre-commit checklist item forbidding exactly what the
  boundary authorizes.
- Corrected a stale `AGENTS.md` statement that R4 embedding execution remained
  deferred, which contradicted two other statements in the same file recording
  the completed 2026-09-02 run. Updated the state snapshot date and recorded the
  tracked documentation split.
- Added an `Evidence Retention Status` section to `docs/DEFERRED-WORK.md`
  recording that "verifies offline" statements describe their closeout rather
  than the present, that the Phase 5 R4 run is the only formal evidence
  currently present on the maintainer's host, and that integrity safeguards
  protect a saved run from alteration but not from deletion. Additive only: no
  closeout was rewritten and no result withdrawn.

- Corrected the README `Findings` section. The empty-response observation is
  now stated as cross-surface but single-model, naming the shared `gemma4:e2b`
  digest, rather than implying several models; the previous wording overstated
  its generality. Added the R6 contrast row, added the omitted 30/30 standard
  versus 12/30 regex Tool Search result, split the empty-response and
  first-turn-`PROVIDER_FAILURE` open questions apart as separate phenomena, and
  noted the untested candidate mechanism beside the Phase 4 budget finding.

- Restructured `README.md` from 391 to 214 lines so it leads with results
  instead of a slice-by-slice capability narration. A new `Findings` section
  states the Phase 4 output-budget yield curve and the T3.6 single all-pass
  qualifier with their original closeout qualifications carried over unchanged,
  and records the cross-phase empty-response and first-turn `PROVIDER_FAILURE`
  observation explicitly as an open question rather than a finding. A new
  `How evidence works` section promotes the evidence lifecycle and links the
  published example. The previous capability narration moved to
  `docs/CAPABILITIES.md` with only heading-level and relative-link adjustments.

- Pinned the tool-call limits applied by both tool paths through
  `ToolCallLimitPolicy` rather than inheriting Spring AI `2.0.1` defaults. The
  pinned values match the current defaults of 40 calls per tool and 150 total
  with `ToolCallLimitBehavior.THROW`, so behaviour is unchanged, but a later
  framework default can no longer alter the protocol silently. Exceeding either
  limit aborts an invocation instead of truncating it. The limits are recorded
  in tracked documentation, not in saved evidence, because Tool Search matrix
  verification compares an exact manifest settings key set.
- Clarified the standing local Ollama authorization: all explicitly requested
  repository work may inspect, select, and invoke already-installed models on
  a loopback endpoint without per-call, per-command, per-model, per-session,
  or per-run approval. This does not start an unrequested scope or weaken the
  provider-free default lifecycle, formal evidence safeguards, or separate
  boundaries for pulls, remote providers, credentials, Docker, publication,
  pushes, releases, and tags.
- Recorded the dedicated `qwen3-embedding` family as the top planned candidate
  for the deferred R4 slice, beginning with versioned tag
  `qwen3-embedding:0.6b`. This is a planning priority only: R4 still requires
  read-only installed-model eligibility proof, including a complete digest and
  literal Ollama `embedding` capability; no pull, substitution, or formal run
  is authorized by this change.
- Upgraded Spring Boot to `4.1.1` and Spring AI to `2.0.1`.
- Updated direct Commons Codec, JUnit, and Bouncy Castle dependencies to
  `1.22.1`, `6.1.3`, and `1.85.2`; retained the current stable AssertJ,
  SLF4J, Caffeine, and Gradle dependency-management versions.
- Closed the authorized Phase 0–5 small-model tool-calling protocol in
  documentation. Retained R3, R5, and R6 evidence verified offline; R5 and R6
  remain the completed local-model executions. R4 formal embedding execution is
  deferred because retained eligibility evidence did not establish an
  already-installed model advertising Ollama's literal `embedding` capability.
  This is not an embedding-quality, answer-correctness, human-support,
  semantic-relevance, evaluator-ground-truth, model-ranking, selection,
  release, tag, push, or branch-promotion decision.
- Completed one formal Phase 5 R6 retrieval-relevancy matrix from clean commit
  `f704d989429a10769ce334276dc79de5bd7cd308` against the verified R5 answer
  evidence. The operationally selected already-installed `granite4.1:3b`
  evaluator, a different deployed artifact from R5's answer model, retained
  all 14 rows with `64` output tokens, `PT2M`, and no pull; ignored evidence
  verified and reanalyzed offline. Eight eligible evaluator calls completed,
  while two missing-context and four unavailable-answer rows were not
  attempted; no unavailable-model, timeout, provider-failure, empty-response,
  or malformed-verdict outcome occurred. The separate-artifact relationship
  does not prove evaluator independence. This is not evaluator ground truth,
  human support, answer correctness, semantic correctness, quality, ranking,
  or model selection; raw evaluator output remains ignored.
- Completed one formal Phase 5 R5 retrieval-answer matrix from clean commit
  `c724e5a93c89eb5de8a11e9d1774a523f77bda37` against the verified R3 lexical
  baseline. The operationally selected already-installed `gemma4:e2b` model
  ran all 14 locked sequential one-attempt rows with `256` output tokens,
  `PT2M`, and no pull; ignored evidence verified and reanalyzed offline. Ten
  invocation outcomes completed, four were empty responses, and two used exact
  `NO_SUPPORT`, with no model-unavailable, timeout, authentication, rate-limit,
  or provider-failure outcome. This is not an answer-correctness,
  semantic-support, relevance, quality, ranking, or model-selection result;
  raw answers remain ignored.
- Recorded standing authorization for liberal use of already-installed
  loopback Ollama models whenever useful during requested Phase 4 and Phase 5
  work. Local implementation diagnostics and formal calls no longer require
  per-call, per-command, per-model, per-session, or per-run approval. Default
  tests and CI remain provider-free; formal evidence keeps its clean-baseline,
  exact-identity, attempt, immutability, and no-selective-retry rules. Model
  pulls, remote providers, credentials, Docker, pushes, releases, and tags
  remain separately gated.
- Updated the Gradle Wrapper from 9.6.1 to 9.7.1.
- Completed one owner-authorized Phase 3 small-model tool-compatibility cohort
  run from clean commit `e897edf`, retaining all 96 locked sequential rows in
  ignored evidence. The saved run verifies and reanalyzes offline, and its
  bounded T3.4 record remains per-model and multidimensional without a rank,
  selection, general-capability, semantic-correctness, or backend-normalized
  performance claim. The separately authorized provider-free T3.5 comparison
  and T3.6 capability-frontier analysis are now complete. The frontier was
  measurable because the separately labelled `qwen3.8:27b-mlx` reference was
  the only tested installed artifact to pass all 16 locked rows and had a
  recorded installed-artifact size. This is not a general smallest-capable,
  ranking, or selection claim; reruns, model pulls, and replacements remain
  separately unauthorized.
- Closed the bounded Phase 1 small-model tool-compatibility baseline. The
  clean 16-row untreated LFM2.5 run completed with offline verification and
  deterministic reanalysis; all first provider turns were classified as
  `PROVIDER_FAILURE`, with no observed tool calls or final responses. This is
  a provider-turn compatibility observation only, not a quality, reliability,
  production, or model-ranking claim. The paired Phase 2 run and owner-only
  human interpretation are now complete. The `inconclusive` decision selects
  untreated cohort operation with an explicit limitation and authorizes no
  prompt-effect claim.

### Added

- Added the opt-in Phase 5 R6 retrieval-relevancy matrix. It consumes a
  verified clean R5 run without re-running retrieval or answer generation;
  gives Spring AI `RelevancyEvaluator` only the retained retrieved documents;
  locks a tracked evaluator prompt, already-installed loopback Ollama
  evaluator/full digest, and explicit one-attempt/no-pull settings; and
  supplies offline verification/reanalysis. It retains deterministic retrieval
  expectation, evaluator observation, self-evaluation, human support judgment,
  and answer correctness as separate fields. Default tests use fake chat
  models. Evaluator output is not ground truth.
- Added the opt-in Phase 5 R5 retrieval-answer matrix. It consumes a verified
  clean R3 run without re-running retrieval; locks the tracked grounded-answer
  prompt, an already-installed loopback Ollama model/full digest, and explicit
  one-attempt/no-pull settings before allocating ignored evidence; preserves
  exact retrieved documents/ranks beside each raw answer; and supplies offline
  verification/reanalysis. Default tests use fake chat invocations. Reference
  syntax and explicit abstention are observations only; assertion support,
  answer correctness, relevance, and model quality remain unassessed.
- Added Phase 5 retrieval fixtures and the provider-free R1–R3 lexical
  retrieval-evidence lifecycle, plus the opt-in R4 local Ollama embedding
  boundary. R4 requires a clean Git baseline, a loopback-only endpoint, an
  already-installed embedding-capable model with a locked full digest, one
  batch, no pull, one attempt, and non-overwriting ignored evidence with
  offline verify/reanalyze support. No formal embedding run was created.
- Added the isolated provider-free `toolCompatibilityCohortFrontier` task for
  T3.6. It strictly verifies one saved cohort, requires every planned row,
  selects a frontier only from all-pass installed artifacts with an
  unambiguous recorded byte-size minimum, reports non-measurability otherwise,
  writes only to standard output, and leaves saved evidence unchanged.
- Added the isolated provider-free `toolCompatibilityCohortCompare` task for
  T3.5. It strictly verifies one saved cohort, compares each ordered peer with
  the separately labelled reference by locked case/repetition identity, emits
  descriptive pass overlap, diagnostics, output-limit observations, latency,
  and total-token deltas only to standard output, and leaves saved evidence
  unchanged. One authorized comparison of the preserved 2026-08-24 cohort
  completed without contacting Ollama and without producing a rank, reference
  ground-truth, model-selection, or backend-normalized performance claim.
- Began the separately authorized dependency-independent Phase 3 small-model
  cohort preparation. Provider-free code now resolves explicit ordered peers
  plus one separately labelled reference from fake/read-only inventories,
  requires full digests and one Ollama runtime version, preserves optional
  model/runtime metadata as explicitly available or unavailable, rejects
  remote, missing, mutable-alias, duplicate-tag, and duplicate-byte identities,
  and fails before output allocation. The four T2.5 decision values now map
  deterministically to one cohort-wide prompt policy with strict evidence
  binding; `revise` blocks execution and `inconclusive` selects untreated
  operation with a limitation. The provider-free T3.3 core adds one immutable
  model-major schedule, explicit supported/unsupported seed semantics, a fresh
  standard-advisor boundary per fake model, complete row/failure retention,
  strict shared-v1 cohort evidence, and dedicated offline verify/reanalyze
  tasks. The provider-free T3.4 layer expands the deterministic cohort report
  into ordered per-model compatibility, discipline, arguments, multi-step,
  failure-recovery, output, and efficiency sections; records incomplete usage
  and metadata explicitly; limits response-format and recovery observations to
  named lexical markers; reports tokens per passing row only with complete
  passing-row usage; and carries a mixed-artifact deployed-system caveat with
  no total rank. Read-only inspection found all provisional tags installed
  under Ollama `0.32.15`. The owner subsequently selected `inconclusive` for
  T2.5, activating untreated operation plus the recorded limitation. At this
  provider-free implementation checkpoint, no cohort lock, live cohort task,
  model invocation, pull, or evidence run had occurred.

- Completed the authorized Phase 2 small-model prompt comparison from clean
  commit `80bc122`. One locked interleaved 32-attempt run produced two
  independently verified and reanalyzed 16-row conditions, and the strict
  deterministic comparison passed. Both conditions reached the same first
  `PROVIDER_FAILURE` boundary on all rows, so this is not evidence of a prompt
  effect, quality, reliability, or model ranking. On 2026-08-23 the owner
  completed the ignored T2.5 worksheet with `inconclusive`; the bound policy is
  untreated operation with the limitation recorded. Exact cohort approval and
  any live execution remain separately gated.

- Completed the T0.1 small-model tool-compatibility documentation packet. The
  locked 16-row Phase 1 protocol and its authorization boundaries are now
  aligned across the plan, repository guide, deferred-work index, test plan,
  environment guide, and dated log. This is documentation only: T1.1
  provider-free implementation and every live matrix remain separately
  unauthorized.
- Refreshed the proposed Phase 3 small-model cohort from the project owner's
  reported installed-model list. `qwen3.8:27b-mlx` is now the provisional
  higher-capability reference candidate, the absent `dolphin-phi:latest` peer
  was removed without replacement or pull, mutable `:latest` reference aliases
  are disallowed when a versioned tag is available, and cohort evidence must
  record artifact/runtime format, thinking-mode metadata where exposed, and the
  Ollama version. Comparisons of MLX and non-MLX latency and token use are
  explicitly deployment-specific rather than weight-only observations. The
  untreated LFM2.5 model remains locked for Phase 1; this documentation change
  adds no model inspection, implementation, or live execution.
- Corrected the proposed Phase 2 tool-compatibility comparator contract: paired
  row `globalPairSequence` and `conditionExecutionPosition` values are expected
  to differ across baseline and candidate, but are accepted only when each
  matches the identical shared paired-execution schedule. This remains
  documentation only; no compatibility code or live execution was added.
- Strengthened the proposed small-model tool-calling compatibility protocol
  after focused review. Phase 1 now records ordered provider turns and per-call
  lifecycle evidence inside each single logical row attempt, applies the
  `512`-token cap per turn and the `PT2M` deadline to the whole row, defers the
  final row schema until observability is proved, and adds an exact versioned
  semantic call/argument oracle so wrong values cannot pass through final-output
  substrings alone. Phase 2 now has one dedicated paired runner contract that
  produces both conditions in the locked interleaved order without changing the
  Phase 1 CLI. This is documentation only; no source set, task, model inspection,
  live call, credential access, Docker use, or push occurred.
- Hardened the proposed small-model tool-calling compatibility plan for
  slice-by-slice Codex execution. Phase 1 now locks the exact 16-row untreated
  LFM2.5 protocol, canonical case/tool identities, empty-system-prompt digest,
  bounded raw-argument schema validation, suite/task/source-set names, and
  evidence layout. A Codex runbook supplies repository paths, dependencies,
  deliverables, checks, stop conditions, and Terra/Luna/Sol routing for every
  formal slice while preserving separate authorization for implementation,
  live Ollama calls, later phases, pushes, releases, and tags. This is a
  documentation-only change; no compatibility code, task, model inspection, or
  provider call was added.
- Revised the proposed [Small-Model Tool-Calling Compatibility
  Plan](docs/SmallModelToolCallingCompatibilityPlan.md) to address three PR
  review findings: corrected the Phase 0 filename reference to the actual
  tracked path, required raw tool-call argument JSON to be preserved and
  validated against the declared schema before callback binding so ordinary
  Spring AI DTO coercion cannot be mistaken for schema-valid model output, and
  locked the Phase 4 fresh 64- and 256-token arms to the same clean Git commit
  with an explicit stop-and-restart rule on drift. This is a documentation-only
  change; no implementation, live model call, or Docker use occurred.
- Completed the fixed Phase 3 Anthropic portability proof against
  `claude-haiku-4-5-20251001` from clean commit `3810a19`. Six sequential,
  one-attempt calls completed with non-empty outputs and full usage metadata;
  usage-derived cost was `$0.001870` against a `$0.005376` worst-case estimate
  and a `$3` task ceiling within the owner's `$5` authorization. Ignored
  evidence verifies and reanalyzes offline. The report establishes architecture
  compatibility only: Anthropic seed remains unsupported, hosted identity is
  not a local digest, and no answer-quality, performance, or ranking claim is
  made.
- Added the provider-neutral Slice O2 portability projection and the opt-in
  Slice O3 Anthropic matrix implementation. It keeps hosted versioned model
  IDs distinct from local digests, records direct, translated, and rejected
  common-option handling, writes raw provider output only to an ignored
  non-overwriting directory, and generates a raw-output-free portability
  report. The separate remote task locks six sequential one-attempt calls,
  `128` output tokens, `PT2M`, no seed simulation, and an explicit USD ceiling;
  provider-free tests cover cost, input/path bounds, failed-row retention, and
  offline verify/reanalyze. The implementation change itself made no remote
  request.
- Added the provider-free Slice O1 Anthropic adapter behind the existing chat
  invocation boundary. It selects `claude-haiku-4-5-20251001` as a pinned
  hosted model ID, maps temperature/output-token/timeout/one-attempt settings,
  records seed as unsupported, captures only safe response metadata, and has
  mocked coverage for usage, failures, metadata, and no-fallback behavior. It
  added no live runner, credential lookup, or remote call at O1 closeout.
- Added the provider-free implementation of the dedicated Ollama chat matrix:
  a tracked versioned three-prompt catalog with exact catalog/per-prompt
  SHA-256 identities, one six-row sequential protocol, explicit installed model
  digest and generation settings, S2-bound invocation, shared-v1 evidence,
  deterministic summary, and standalone offline verify/reanalyze tasks. Tests
  cover exact parity with the unchanged interactive defaults, pre-allocation
  rejection, six-call order, failed-row retention, protocol drift, tampering,
  and summary regeneration.
- Added a minimal provider-neutral chat invocation contract for provider and
  requested/effective model identity, prompts, common generation settings,
  explicit provider option support, raw response, available usage, latency,
  one-attempt recording, and classified failures. Its Ollama-only adapter keeps
  the full digest and seed semantics provider-specific, applies explicit model
  options, and shares loopback/no-pull/one-attempt model construction with the
  local fact-check judge. Provider-free tests cover success, empty response,
  unavailable model, timeout, provider failure, and safety-policy behavior;
  the existing interactive chat endpoint remains unchanged.
- Added plain-Java `EvidenceFiles` operations for non-overwriting saved-artifact
  writes, directory/path validation, artifact size/SHA-256 checks, saved-run
  layout inspection, deterministic summary verification, and atomic summary
  replacement. Vision matrix, Tool Search matrix, and local evaluation now
  share those operations without changing their saved formats or suite-specific
  schemas.
- Added `docs/DEFERRED-WORK.md` as the public-safe canonical index for work
  intentionally outside the completed August cycle. It now records the active
  Phase 2 chat closure plus the Prompt v2, output-budget, Testcontainers,
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

- Completed the Phase 2 local Ollama reuse proof from clean commit `51025cf`
  with explicit `gemma4:e2b` identity and full digest, `128` output tokens,
  `PT2M`, and the locked six-row sequential one-attempt schedule. All six
  invocations completed with usage metadata but returned empty responses;
  there was no unavailable-model, timeout, or provider failure. Ignored
  evidence verified and reanalyzed offline, and the preserved Phase 1 evidence
  still verifies. This closes the invocation/evidence reuse proof without a
  chat-quality, reliability, or model-ranking claim; the existing interactive
  endpoint remains unchanged and Phase 3 stays separately authorized.
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
