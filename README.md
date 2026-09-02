# setaccio-lab

A local-first Spring Boot lab for comparing how AI models analyze real files, starting with image benchmarking, hashing, and reproducible result output.

`setaccio-lab` is the public technical showcase for reusable Setaccio AI/file-processing work that can be inspected without the private product repository. It keeps the primitives, prompts, test fixtures, and benchmark outputs close to the code so model behavior can be reviewed, changed, and compared over time.

## What is in this repo

- `setaccio-core`: a small, Spring-free Java library for reusable Setaccio primitives. Today it provides BLAKE3 hashing utilities backed by Apache Commons Codec and Bouncy Castle.
- `setaccio-lab`: a Spring Boot and Spring AI application for local evaluation work. It is intended for model, prompt, provider, model-type, tool-calling, and later MCP experiments.
- `setaccio-testcontainers`: an optional Testcontainers-backed integration harness. It may depend on `setaccio-lab`, but `setaccio-lab` must not depend on it.
- `docs/`: public test and project notes that describe how the lab should grow without depending on private application code. Start with [`CAPABILITIES.md`](docs/CAPABILITIES.md) for the detailed surface description, [`evidence/`](docs/evidence/) for a published example of run output, and [`DEFERRED-WORK.md`](docs/DEFERRED-WORK.md) for scope boundaries.

This repository is Apache-2.0 licensed and intentionally public-safe. Private Setaccio application code, deployment details, product workflows, and closed-source modules do not belong here.

## Findings

Every result below is bounded by the closeout that produced it. The qualifying
language is part of the record, not hedging: these are small, controlled,
single-run observations, and none of them ranks or selects a model.

### Output budget and fact-check verdict yield

In the Phase 4 five-arm breakpoint study, valid-verdict yield was flat at 2/12
for the tested 64–128 token budgets, and higher at 192 tokens (6/12) and 256
tokens (12/12).

This is a protocol-specific association, not evidence of a causal threshold, a
generally optimal budget, or model reliability. Completion-token counts remain
only an output-limit proxy.

### Only one tested artifact cleared the tool-calling protocol

Across the 96-row Phase 3 cohort, the T3.6 frontier analysis found exactly one
all-pass qualifier: the separately labelled `qwen3.8:27b-mlx` reference passed
16/16, at a recorded installed-artifact size of `18174721847` bytes. The paired
T3.5 comparison found reference-only pass counts of 16, 2, 4, 2, and 2 in peer
order, with no peer-only and no neither-pass rows.

The narrow frontier is measurable only among these tested installed artifacts
under this exact protocol. It is not a rank, correctness oracle, selection,
general-capability, semantic-correctness, backend-normalized performance, or
general smallest-capable result.

### Open question: empty responses and first-turn failures

The most reproducible observation in the project is one it has not yet
explained. Across otherwise unrelated surfaces, locally hosted models completed
their provider invocation with no classified failure, and returned no content:

| Run | Empty responses |
| --- | --- |
| Phase 2 chat matrix (`gemma4:e2b`, 128 tokens) | 6 of 6 |
| Fact-check A5 (`gemma4:e2b`, 64 tokens) | 10 of 12 |
| Phase 5 R5 answer matrix (`gemma4:e2b`, 256 tokens) | 4 of 14 |

Separately, Phase 1's 16 tool-compatibility rows and Phase 2's 32 interleaved
attempts stopped at the same first `PROVIDER_FAILURE` turn, in both the
untreated and prompted conditions. No tool call, final response, usage,
output-limit, or visible-reasoning observation was retained, and the safe
evidence does not identify the underlying provider-failure cause.

This is an open question, not a finding. It has no closeout, no controlled
protocol, and no interpretation of its own. It is recorded here because it
confounds several results above and is the most obvious candidate for the next
controlled study.

## How evidence works

The design goal is that a disappointing result should still be a reproducible
one. Every formal run writes an immutable, offline-checkable evidence
directory, and the checking path never needs a model.

- **Non-overwriting run directories.** Each run allocates a new dated directory
  and refuses to write into an existing one. No run can silently replace
  another.
- **Versioned manifest envelope.** `manifest.json` records the suite, run ID,
  clean-or-dirty Git baseline commit, Spring Boot and Spring AI versions, the
  execution engine, and the complete locked protocol settings.
- **Artifact integrity.** Every artifact is declared with a relative path, byte
  size, and SHA-256. Verification rejects missing, modified, empty, duplicate,
  undeclared, path-escaping, and symbolic-link artifacts.
- **Immutable model identity.** Requested and effective model tags are recorded
  alongside the full Ollama digest, re-resolved after execution and required to
  be unchanged before evidence is written. Mutable aliases are rejected.
- **Input identity.** Benchmark inputs are identified by BLAKE3 digest rather
  than by copying private content into evidence.
- **Deterministic reanalysis.** `SUMMARY.md` is regenerated from the raw
  artifact alone and must reproduce byte-for-byte.
- **Offline verification.** Every suite has standalone `*Verify` and
  `*Reanalyze` tasks that do not start Spring, read a private corpus, or
  contact a provider.

Analysis keeps separate things separate. Provider invocation success,
structural completion, semantic correctness, token availability, latency, and
infrastructure failure are distinct dimensions. Format compliance is never
treated as proof that a model understood its input, and an AI evaluator is
never treated as ground truth.

A published example of this output is tracked in
[`docs/evidence/`](docs/evidence/), which explains what a reader can check and
why a publication copy deliberately will not pass the verification task.

## Current capabilities

| Surface | Interactive endpoint | Controlled matrix task |
| --- | --- | --- |
| Vision analysis | `POST /api/lab/vision` | `visionMatrix` + `Verify` / `Reanalyze` / `Compare` |
| Chat | `POST /api/lab/chat` | `chatMatrix` + `Verify` / `Reanalyze` |
| Tool calling and Tool Search | `POST /api/lab/tools` | `toolSearchMatrixBaseline`, `toolCompatibility*` |
| Deterministic fixture evaluation | `POST /api/lab/evaluations` | — |
| AI-judged fact checking | — | `localEvaluation`, `localEvaluationBudget` |
| Retrieval (lexical, embedding, answer, relevancy) | — | `retrievalEvaluation`, `retrievalEmbedding`, `retrievalAnswerMatrix`, `retrievalRelevancyMatrix` |

Interactive endpoints run under the `local` profile. Every matrix task is
opt-in, sequential, and outside `test`, `check`, `build`, application startup,
and CI. All of them force Ollama's pull strategy to `never`.

All benchmarks are local-first and offline-safe by default:

- default builds and tests require no credentials or running Ollama instance,
- live model runs require the `local` profile or explicit configuration,
- generated benchmark outputs stay under ignored `build/` directories.

Result filenames include nanosecond timestamps and short run identifiers so repeated runs cannot overwrite one another when they start at the same instant.

Full slice-by-slice detail for every contract, prompt version, corpus, and
closeout is in [`docs/CAPABILITIES.md`](docs/CAPABILITIES.md). Deferred scope,
start gates, and non-authorization boundaries are indexed in
[`docs/DEFERRED-WORK.md`](docs/DEFERRED-WORK.md).

## Evaluation scope

The harness should grow with Spring AI's supported provider and model-type surface. The intent is to make it possible to test comparable prompts, inputs, outputs, options, and error behavior across major providers such as Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama.

Planned model-type coverage includes:

- chat completion,
- embedding,
- text to image,
- audio transcription,
- text to speech,
- moderation.

Provider-backed tests must stay opt-in and explicit. Default builds should use unit tests, mocks, fixtures, and local-safe configuration rather than calling remote providers or local models unexpectedly.

## Why it matters

AI evaluation gets hard when prompts, model choices, inputs, and outputs are scattered across ad hoc scripts. This project keeps those pieces in one Java workspace with ordinary tests, explicit versioning, and reproducible build commands.

Useful contribution areas include:

- benchmark result models and JSON output,
- prompt fixtures that are safe to publish,
- local-only controller and service tests,
- Spring AI model option handling,
- provider and model-type adapters,
- model comparison workflows,
- later tool-calling and MCP evaluation patterns.

## Requirements

- Java 25
- Gradle wrapper from this repo
- Optional: local Ollama for live lab runs

Provider credentials and live-test switches are documented in [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md). Default builds do not require AI provider credentials.
For local image comparison work, set `SETACCIO_LAB_INPUT_DIR` to your working image folder and `SETACCIO_LAB_OUTPUT_DIR` to the benchmark result directory. If `SETACCIO_LAB_INPUT_DIR` is unset, the lab does not substitute a default path.

## Build

```bash
./gradlew :setaccio-core:build
./gradlew :setaccio-lab:build
./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build
```

Offline tests for the isolated Tool Search smoke analyzer are also available explicitly:

```bash
./gradlew :setaccio-lab:toolSearchSmokeTest
./gradlew :setaccio-lab:visionMatrixTest
```

The isolated offline suites cover matrix protocols, evidence integrity,
deterministic reanalysis, and failure classification. Live matrix execution is
an explicit separate task documented in `docs/ENVIRONMENT.md` and is never part
of the normal build lifecycle.

## Build Versions

Most Gradle dependency versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

- Update cataloged dependency versions there instead of in module `build.gradle` files.
- Version catalog aliases are used from the module build scripts for shared libraries and Spring BOMs.
- Root plugin versions are still declared in the root `build.gradle`.
- A few implementation-specific dependencies may remain directly versioned in their module until they are moved into the catalog.

## Run the lab locally

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

The lab app uses port `8082`.

## Project standards

Project versions follow [Semantic Versioning](https://semver.org/).
Changelog entries follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## License

Apache License 2.0. See [LICENSE](LICENSE).
