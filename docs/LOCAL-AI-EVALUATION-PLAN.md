# Local AI-Judged Evaluation Plan

Status: Slices A1 through A6 completed. Actual-human fixture confirmation was
recorded on 2026-08-02; the dedicated recording judge boundary and offline
evidence lifecycle plus the opt-in host-Ollama runner were added on 2026-08-03.
The controlled local run and bounded interpretation completed on 2026-08-03.
The framework contract was re-checked against Spring AI `2.0.0` and Spring
Boot `4.1.0` during A2 implementation, and again against Spring AI `2.0.1` and
Spring Boot `4.1.1`.

This plan records one completed bounded local fact-checking cycle. Slices A1
through A6 added a tracked offline prompt/fixture/review contract, a mockable
recording judge boundary, offline-verifiable evidence, one explicit
host-Ollama runner, one controlled execution, and a bounded interpretation.
They did not change the deterministic evaluation endpoint, start Docker, pull
a model, attach live execution to a default lifecycle, or add a dependency.

## Current Baseline

- `POST /api/lab/evaluations` runs public fixtures through the Spring AI
  `Evaluator` contract without calling a model or provider.
- `EvaluationBenchmarkRow` already separates evaluator output, score,
  feedback, metadata, invocation success, and errors, but its `passed` field
  currently means the deterministic evaluator's verdict. A live slice must not
  reuse that field as if it meant agreement with a known expected result.
- The A2 boundary builds Spring AI's unchanged `FactCheckingEvaluator` around a
  request-scoped recording `ChatModel`. Its dedicated Ollama factory requires
  an explicit loopback URL and complete generation settings, forces pull
  strategy `never`, applies connect/read timeout, and configures one attempt
  with no hidden Spring AI retry.
- `setaccio-testcontainers` already declares
  `spring-ai-spring-boot-testcontainers` in test scope. It has no
  `OllamaContainer`, Docker task, or model-provisioning path, and normal tests
  do not start a container.
- The resolved Spring AI Testcontainers module supplies Spring Boot service
  connection support and an `OllamaConnectionDetails` factory for a typed
  Testcontainers `OllamaContainer`. The current module classpath does not
  include the separate `testcontainers-ollama` module.

## Implemented Slice A1 Contract

- Prompt `local-fact-check`, version `1`, uses exact `{document}` and `{claim}`
  placeholders and has raw-byte SHA-256
  `e75e0ddd9bef80eecf27e1b668cef954a5eddb5a74b5e4c19db97710c3d39470`.
- Catalog `local-fact-check-fixtures`, version `1`, contains three original
  repository-authored document pairs and exactly three supported plus three
  unsupported claims. Its raw-byte SHA-256 is
  `077d63fe5af596454127babf809075ebc61857cb5e1694c4fae1e58c0d844dac`.
- On 2026-08-02, the project owner confirmed that all six expected verdicts are
  correct. The tracked review record binds that date, the catalog
  identity/digest, and all six fixture IDs;
  its raw-byte SHA-256 is
  `55a5c452dd58a6dddf9d9012cdfb68e50a127226fd49abfaa30597d5e8310161`.
- Offline tests lock prompt/catalog/review identity and reject pending,
  incomplete, or digest-mismatched review records. No live model or provider
  is involved.

## Implemented Slice A2 Boundary

- `LocalFactCheckJudgeSettings` requires an explicit model, temperature, seed,
  positive token limit, positive timeout, and exactly one attempt. It has no
  application or environment-derived defaults.
- `LocalFactCheckJudgeModelFactory` constructs a dedicated Ollama chat model
  from an explicit loopback base URL, propagates the full generation options,
  uses pull strategy `never`, applies the same timeout to connection/read
  handling, and disables Spring AI retries.
- `LocalFactCheckJudgeBoundary` creates a fresh recording model for each
  fixture, passes its `ChatClient.Builder` plus the tracked prompt to Spring
  AI's unmodified `FactCheckingEvaluator`, and captures the response before the
  evaluator reduces it to a boolean.
- The result separates provider invocation success, Spring evaluator boolean,
  normalized judge verdict, human-confirmed expectation agreement, raw output,
  effective response metadata, token usage when available, latency, attempt
  count, and diagnostic category. Only trimmed case-insensitive exact `yes` and
  `no` become verdicts; empty and malformed output remain separate failures.
- Mocked default-lifecycle tests cover both locked repetition seeds, valid
  verdicts, expectation mismatch, empty/malformed output, metadata and usage,
  absent usage, unavailable model, timeout, provider failure, explicit timeout
  propagation, and one-attempt enforcement without contacting Ollama.
- No Spring bean selects or starts this judge. A4 supplies it only through the
  separately invoked opt-in runner.

## Implemented Slice A3 Evidence Lifecycle

- `LocalEvaluationProtocol` locks protocol version `1`, twelve sequential rows,
  temperature `0.0`, seeds `42` and `43`, positive explicit token/timeout
  values, exactly one attempt, and pull strategy `never`. Supported then
  unsupported order within each pair is reversed for repetition two; because
  seed and repetition change too, the report makes no order-effect claim.
- Saved rows retain fixture/pair IDs, BLAKE3 document and claim identities,
  expected verdict, requested and normalized installed judge identity with a
  full digest, raw response, response metadata, usage when available, latency,
  attempt count, Spring evaluator boolean, normalized verdict, expectation
  agreement, and diagnostic category without copying fixture text.
- The evidence writer produces `local-evaluation-results.json`, the shared v1
  `manifest.json` with SHA-256 artifact descriptors plus Git/framework
  provenance, and a deterministic `SUMMARY.md` under a caller-provided fresh
  run directory.
- `localEvaluationVerify` and `localEvaluationReanalyze` are standalone offline
  tasks. They do not start Spring or contact Ollama, and they are not attached
  to `test`, `check`, `build`, application startup, or CI.
- Offline tests reject raw/summary tampering, missing or extra artifacts,
  unsafe paths, manifest/contract/model drift, wrong schedule or row count,
  invalid attempts, unclassified or incoherent outcomes, partial usage, unsafe
  metadata, and summary drift. Reanalysis replaces only a regenerable summary
  after immutable raw evidence and its manifest descriptor pass inspection.
- No live task is attached to the default lifecycle; the A4 runner remains the
  sole explicit judge entry point.

## Implemented Slice A4 Host-Ollama Runner

- `:setaccio-lab:localEvaluation` requires an explicit loopback URL, installed
  judge tag, positive token limit, ISO-8601 timeout up to ten minutes, and a
  new dated child of ignored `local/evidence/evaluation-matrix/`.
- Preflight locks the A1 prompt/catalog/review digests and confirmed fixture
  order, validates every option and output path, and resolves the requested
  installed tag to its normalized name and full immutable Ollama digest before
  allocating output.
- The runner reuses the A2 factory/boundary and A3 protocol/evidence layer. It
  makes exactly twelve sequential one-attempt calls, uses temperature `0.0`
  and seeds `42`/`43`, forces pull strategy `never`, and preserves classified
  failures as rows.
- Provider-free tests cover all option/preflight failures and exact execution
  order without Ollama. Gradle task help documents the local/no-pull boundary;
  `test`, `check`, `build`, startup, and CI remain judge-free.
- Git provenance is captured once before output allocation and execution, then
  reused for the summary and manifest. A dirty worktree is
  labeled `diagnostic/non-final` in `SUMMARY.md`, not treated silently as a
  controlled baseline.

## Completed Slice A5 Controlled Local Run

- The single authorized run used clean commit
  `5d41362cc73e0f95eaf740602ac8a5d47e80a830`, explicit installed judge
  `gemma4:e2b`, full digest
  `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e`,
  token limit `64`, timeout `PT2M`, temperature `0.0`, seeds `42`/`43`, and
  ignored output ID `2026-08-03-gemma4-e2b-a5`.
- The protocol produced all twelve ordered rows and exactly twelve recorded
  attempts. Every provider invocation completed and every row retained prompt,
  completion, and total usage metadata; no model-unavailable, timeout, or
  provider failure occurred.
- Ten rows had empty raw judge output. Two unsupported rows produced valid
  `no` verdicts that matched the human-confirmed expectations. There were no
  valid expectation mismatches or malformed verdicts. The result contained two
  normalized unsupported/`no` verdicts in total, one fixture with consistent
  normalized verdicts, and five fixtures with incomplete two-repetition
  comparison.
- The shared manifest binds the clean Git baseline, Spring Boot `4.1.0`, Spring
  AI `2.0.0`, prompt/catalog/review identities, judge identity, settings, and
  artifact digests. Standalone verification passed; reanalysis reproduced the
  same `SUMMARY.md` SHA-256
  `6739810900203ef4f6aead1dc00e98a55de0b7e1db19fb0967d19e06e9e8f90e`.
- No selective retry, replacement row, second protocol run, model pull, remote
  provider, credential, Docker/Testcontainers runtime, or publication of raw
  ignored evidence occurred.

The upstream API review confirmed:

- Spring AI's [`Evaluator` contract and evaluation request model](https://docs.spring.io/spring-ai/reference/api/testing.html)
  already match the deterministic benchmark's user text, context, and response
  shape.
- `RelevancyEvaluator` judges whether a response is relevant to a query and
  supplied context. Its custom prompt contract requires `query`, `response`,
  and `context` placeholders.
- `FactCheckingEvaluator` judges whether a claim is supported by supplied
  context and accepts a custom evaluation prompt. That is the closer fit for
  the lab's existing public context/response fixtures.
- The pinned implementation normalizes only exact `yes` as a passing verdict
  and returns empty feedback/metadata. It does not expose the raw judge text or
  token usage, and it makes a valid `no` indistinguishable from malformed text
  through `EvaluationResponse` alone. A2 addresses that limitation with a
  narrow request-scoped recording boundary around the dedicated judge model;
  it does not fork or copy Spring AI's evaluator implementation.
- Spring AI `2.0.1` documents an
  [`OllamaContainer` service connection](https://docs.spring.io/spring-ai/reference/api/testcontainers.html),
  while Spring Boot documents that
  [`@ServiceConnection` details override connection properties](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html#testing.testcontainers.service-connections).
- Spring AI's [Ollama options](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
  support explicit model, temperature, seed, and token settings, and the pull
  strategy can remain `never`.

## Completed Slice A6 Bounded Interpretation

A6 analyzed only the preserved A5 evidence. It made no model call, did not
rerun or replace a row, and left the ignored raw evidence unchanged.

- Supported agreement is not measurable from this run: none of the six
  planned supported rows produced a valid normalized verdict; all six were
  empty.
- Two of the six planned unsupported rows produced valid `no` verdicts and
  both agreed with the human-confirmed expectation. The other four were empty,
  so this is two agreements among two evaluable unsupported rows, not a
  general unsupported-claim accuracy rate.
- One of six fixtures had two valid, consistent verdicts. Five repetition
  comparisons were incomplete and there were no observed disagreements. Two
  repetitions do not establish statistical reliability.
- The only valid normalized outputs were two `no` verdicts; there were no
  valid `yes` verdicts. Because ten rows had no verdict, this does not establish
  an always-`no` or other label tendency.
- Ten responses were empty and none were malformed. All ten empty responses
  recorded `64` completion tokens, equal to the explicit output-token limit;
  both valid responses recorded two completion tokens. This is a descriptive
  association, not proof that the limit or model reasoning behavior caused the
  empty output.
- All twelve provider invocations completed in one attempt with complete usage
  metadata. There were no model-unavailable, timeout, provider, or other
  infrastructure failures. Median latency was `1073.5 ms`, with an observed
  range of `442–6545 ms`.

The contract merits one later, separately implemented and pre-registered
output-budget compatibility hypothesis: test whether a larger explicit
positive output-token limit increases exact `yes`/`no` verdict yield for the
same immutable judge digest while keeping the prompt, fixtures, row order,
temperature, seeds, one-attempt policy, and no-pull behavior fixed. That would
be a new experiment with a new evidence directory, not a retry or correction
of A5. A6 did not execute it. The standing local Ollama policy covers calls to
already-installed loopback models throughout explicitly requested repository
work, so the future budget experiment does not need another local-call or
local-run approval once its implementation scope is explicitly started. Its
paired-evidence and clean-baseline safeguards remain unchanged.

### Separately started reasoning-default and boundary diagnostic

The 2026-09-04 successor diagnostic is separate from A5, A6, and the completed
output-budget work. Its version-aware protocol preserves the first reasoning
diagnostic and pre-registers one new 42-row schedule at `64` tokens: the subject
under `PROVIDER_DEFAULT`, `ENABLED`, and `DISABLED` at both the fact-check and
provider-neutral chat boundaries, plus a provider-default non-thinking chat
control. Both boundaries receive the identical rendered prompt. Within-boundary
policy contrast is controlled; matching-policy boundary contrast is controlled
for the prompt and recorded settings, while remaining descriptive for observed
runtime behavior. Chat rows keep fixture identity and expected-verdict
provenance but intentionally carry no evaluator verdict. This does not rerun,
repair, replace, reanalyze, or reinterpret A5 or any Phase 4 evidence and does
not decide whether a closed suite should change its reasoning policy.

Testcontainers disposition for this cycle: **defer**. Host-Ollama execution,
provenance, and offline verification worked; containerization would test model
provisioning and service-connection wiring, not the observed verdict-yield
question. No container code or dependency is added. `RelevancyEvaluator`
also remains deferred until a real retrieval flow preserves retrieved
documents. Release and tag decisions remain deferred.

## Future Evaluation Contract

The completed August closeout and every intentionally deferred follow-up are
indexed in [DEFERRED-WORK.md](DEFERRED-WORK.md). The historical contract detail
below does not itself start a new experiment or authorize a provider,
container, release, or tag. The standing local Ollama policy remains in force
for explicitly requested work using already-installed loopback models.

### Rubric and fixtures

- Track one public prompt such as `local-fact-check-v1` with ID, version, exact
  template bytes, required `document` / `claim` placeholders, and SHA-256
  digest. Supply it through
  `FactCheckingEvaluator.builder(...).evaluationPrompt(...)` rather than
  depending silently on an upstream default prompt.
- Start with three short public contexts. Pair each context with one supported
  and one unsupported claim, for six fixed fixtures total.
- Have a human confirm the six expected verdicts before a live run. The model's
  answer is a judge verdict; agreement with the fixture expectation is the
  benchmark result. Neither one is a general factuality score.
- Keep positive and negative cases balanced and their execution order explicit.
  Alternate the within-pair order across repetitions so a label or ordering
  tendency remains visible.

### Reproducible execution

- Run two strictly sequential repetitions with temperature `0.0`, seeds `42`
  and `43`, and one explicit token limit.
- Record the judge tag, normalized installed name, full digest, Ollama base
  URL category (`local`, never a host name), Spring Boot and Spring AI
  versions, prompt identity, complete generation settings, fixture order, Git
  baseline, latency, token metadata when available, raw judge answer, and
  normalized verdict.
- Preserve raw `yes` / `no` output separately from expectation agreement and
  repetition consistency. Do not coerce any other text into a verdict.
- Write immutable raw JSON, a shared v1 evidence manifest, and deterministic
  Markdown summary under ignored `local/evidence/evaluation-matrix/` output. Add
  standalone offline verification and reanalysis before the first controlled
  live run.
- Treat a dirty worktree as diagnostic/non-final provenance, not as missing
  evidence.

### Bias and interpretation

- Report supported-claim and unsupported-claim agreement separately; do not
  hide label imbalance inside one aggregate percentage.
- Report an always-`yes` or always-`no` tendency, pair-order sensitivity, and
  repetition disagreement as diagnostics.
- Keep judge identity separate from any future generation-model identity.
  Flag self-evaluation explicitly if a later benchmark uses the same full model
  digest for both roles.
- Do not rank judge models in this first slice and do not use one local judge to
  replace human review of prompt quality.

### Failure classification

Hard execution or evidence failures:

- `invalid_input`: missing or invalid task inputs, fixture drift, or unsupported
  settings;
- `judge_model_unavailable`: the explicit tag is not installed or a full digest
  cannot be resolved;
- `provider_failure`: Ollama invocation, timeout, or response transport failure;
- `empty_response`: the judge returned no usable text;
- `malformed_verdict`: normalized output is neither exactly `yes` nor `no`;
- `evidence_failure`: artifact creation, integrity, verification, or reanalysis
  failed.

Model-behavior diagnostics, not infrastructure failures:

- `expectation_mismatch`: a valid verdict disagrees with the human-confirmed
  fixture expectation;
- `repetition_inconsistent`: two valid repetitions disagree;
- `label_skew`: the bounded cohort reveals an always-yes or always-no tendency.

## Acceptance Criteria for the Controlled Live Slice (Completed)

- Default `test`, `check`, `build`, application startup, and CI make no judge
  call and need no running Ollama instance.
- The live task refuses to run without an explicit already-installed judge
  model, full resolved digest, token policy, and new ignored output directory.
- No model is pulled automatically and no remote or paid provider is
  configured or contacted.
- The tracked fact-check prompt and six paired fixtures have locked identity
  tests and human-confirmed expected verdicts.
- Mocked tests cover options propagation, both verdicts, malformed and empty
  output, raw-response/usage capture before evaluator normalization, every hard
  failure category, expectation mismatch, label skew, and repetition
  disagreement.
- The controlled run contains exactly twelve sequential rows: six fixtures by
  two repetitions.
- Raw evidence records judge verdict and expectation agreement as different
  fields, then verifies and reanalyzes offline from the shared manifest.
- Public closeout reports agreement, disagreement, variance, label tendency,
  latency, token availability, and infrastructure failures separately. It does
  not claim a general factuality score or model winner.

## Separate Testcontainers Gate

Do not add container work to the first live evaluation slice. A later
container-specific slice may proceed only if service-connection coverage adds
value beyond the working host-Ollama path.

That later slice must:

- add the typed Testcontainers Ollama dependency only to
  `setaccio-testcontainers`;
- use an explicit task such as
  `:setaccio-testcontainers:ollamaEvaluationTest`, never the normal `test`,
  `check`, root `build`, or CI lifecycle;
- use Spring AI's existing `OllamaConnectionDetails` service connection with a
  typed `OllamaContainer` and verify that it overrides ordinary connection
  properties;
- require explicit model provisioning through a pinned local image or a
  separately acknowledged opt-in download step; never pull a model as a side
  effect of a default build;
- leave `setaccio-lab` free of Docker/Testcontainers dependencies and never
  reverse the existing module dependency direction;
- prove that running `:setaccio-testcontainers:test` and the root build still
  requires no Docker daemon.

Stop and re-review compatibility before implementation if the repository moves
off Spring AI `2.0.1` or Spring Boot `4.1.1`, the evaluator prompt contracts
change, the Ollama service connection no longer resolves, or the proposed
slice requires RAG, a remote judge, automatic model pulls, or container
provisioning to succeed.
