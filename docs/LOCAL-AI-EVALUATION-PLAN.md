# Local AI-Judged Evaluation Plan

Status: Slices A1 and A2 implemented. Actual-human fixture confirmation was
recorded on 2026-08-02, and the dedicated recording judge boundary was added on
2026-08-03. Evidence, an opt-in runner, and a controlled live run remain
pending. The framework contract was re-checked against Spring AI `2.0.0` and
Spring Boot `4.1.0` during A2 implementation.

This plan defines one bounded local fact-checking cycle. Slices A1 and A2 add a
tracked offline prompt/fixture/review contract and a mockable recording judge
boundary. They do not add a live runner, change the deterministic evaluation
endpoint, start Docker, pull a model, or add a dependency.

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
- No Spring bean selects or starts this judge. A3 must add offline evidence
  lifecycle support, and A4 must add the separately authorized opt-in runner.

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
- Spring AI `2.0.0` documents an
  [`OllamaContainer` service connection](https://docs.spring.io/spring-ai/reference/api/testcontainers.html),
  while Spring Boot documents that
  [`@ServiceConnection` details override connection properties](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html#testing.testcontainers.service-connections).
- Spring AI's [Ollama options](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
  support explicit model, temperature, seed, and token settings, and the pull
  strategy can remain `never`.

## Recommendation

Implement a host-Ollama fact-checking matrix first. Do not combine that slice
with RAG, `RelevancyEvaluator`, or Testcontainers.

The remaining live-run slices should:

1. Add one explicitly invoked `:setaccio-lab:localEvaluation` task that calls
   an already-running local Ollama service.
2. Require `--judge-model`, `--max-tokens`, and a new dated `--output-dir`.
   Do not inherit `OLLAMA_MODEL`, choose a default judge, treat a mutable tag as
   the model identity, or fall back to the application chat model silently.
3. Resolve the requested installed model to its normalized name and full
   immutable Ollama digest before allocating output. Keep
   `spring.ai.ollama.init.pull-model-strategy=never`.
4. Use the implemented dedicated judge factory and recording boundary rather
   than partially overriding the application's generation model. Do not add a
   second normalization path in the runner or evidence layer.
5. Run only `FactCheckingEvaluator` against a small tracked, public-safe,
   balanced claim/context fixture cohort.

`RelevancyEvaluator` should remain deferred until the lab has a real retrieval
flow and saved retrieved documents. Treating ordinary fixture context as RAG
evidence would create the appearance of retrieval evaluation without testing
retrieval.

Containerizing Ollama would test environment provisioning and service
connection wiring, not the fact-checking hypothesis. It should therefore be a
separate later slice, justified only after the host-Ollama contract is useful.

The controlled run must remain cost-free and local in the same sense as the
other lab matrices: no provider credential, paid API, public network request,
or automatic model download; an already-installed model served by local
Ollama; and ignored local evidence only. The runner should require a loopback
Ollama endpoint for this first slice and record only the neutral category
`local`, never the URL or host name.

## Future Evaluation Contract

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
  Markdown summary under ignored `build/evaluation-matrix/` output. Add
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

## Acceptance Criteria for the Future Live Slice

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
off Spring AI `2.0.0` or Spring Boot `4.1.0`, the evaluator prompt contracts
change, the Ollama service connection no longer resolves, or the proposed
slice requires RAG, a remote judge, automatic model pulls, or container
provisioning to succeed.
