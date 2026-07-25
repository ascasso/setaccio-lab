# setaccio-lab

A local-first Spring Boot lab for comparing how AI models analyze real files, starting with image benchmarking, hashing, and reproducible result output.

`setaccio-lab` is the public technical showcase for reusable Setaccio AI/file-processing work that can be inspected without the private product repository. It keeps the primitives, prompts, test fixtures, and benchmark outputs close to the code so model behavior can be reviewed, changed, and compared over time.

## What is in this repo

- `setaccio-core`: a small, Spring-free Java library for reusable Setaccio primitives. Today it provides BLAKE3 hashing utilities backed by Apache Commons Codec and Bouncy Castle.
- `setaccio-lab`: a Spring Boot and Spring AI application for local evaluation work. It is intended for model, prompt, provider, model-type, tool-calling, and later MCP experiments.
- `setaccio-testcontainers`: an optional Testcontainers-backed integration harness. It may depend on `setaccio-lab`, but `setaccio-lab` must not depend on it.
- `docs/`: public test and project notes that describe how the lab should grow without depending on private application code.

This repository is Apache-2.0 licensed and intentionally public-safe. Private Setaccio application code, deployment details, product workflows, and closed-source modules do not belong here.

## Current capabilities

### Shared Evidence Lifecycle Foundation

`setaccio-lab` includes plain Java primitives for reproducible benchmark
artifacts. They allocate unique non-overwriting run directories, write and read
a versioned manifest envelope, capture Git and framework provenance, describe
artifacts with relative paths and SHA-256 integrity metadata, and verify saved
runs offline. Verification rejects missing, modified, empty, duplicate,
undeclared, path-escaping, or symbolic-link artifacts without starting Spring
or contacting a model provider.

The lifecycle deliberately keeps suite result payloads separate and reserves
BLAKE3 for benchmark input identity. The locked Tool Search matrix now writes
this v1 manifest around its unchanged raw comparison JSON and deterministic
Markdown summary. Vision, chat, and evaluation writers have not adopted the
shared manifest yet.

### Local Vision Benchmark

`POST /api/lab/vision` runs under the `local` profile. It:

- accepts uploaded images through multipart `files`,
- runs each image against one or more local Ollama models through Spring AI,
- hashes inputs with the BLAKE3 utilities in `setaccio-core`,
- returns structured rows with model, input hash, latency, output, and error details,
- writes raw JSON results to `build/lab-results/` by default, configurable with `SETACCIO_LAB_OUTPUT_DIR`.

### Local Chat Benchmark

`POST /api/lab/chat` runs under the `local` profile. It:

- runs text prompts across explicit Ollama model lists without tools,
- uses the comma-separated `models` request field for each run; the documented `gemma4:e2b` example is only the repo default Ollama model and can be replaced with any already-pulled local model,
- accepts default public-safe prompts or caller-provided `{ "id": "...", "text": "..." }` prompts,
- captures provider/model metadata, prompt id/text, token usage when Spring AI exposes it, latency, output, and errors,
- writes structured `*-chat.json` results to the same output directory.

### Local Tool-Calling Benchmark

`POST /api/lab/tools` runs under the `local` profile. It:

- runs deterministic, public-safe tool prompts across explicit Ollama models,
- runs either the standard Spring AI `ToolCallingAdvisor` path or an explicit standard-versus-Tool Search comparison,
- exercises first-class fixture cases for arithmetic, deterministic time, catalog lookup, multi-step execution, no-match behavior, tool abstention, and deterministic callback failure,
- attaches explicit expectations for required and forbidden tools, output terms, and tool-response terms to each case,
- captures selected tool calls, executed tool responses, normalized Tool Search queries and discovered tools, named contract assertions, cumulative token usage, latency, and final output,
- applies and records deterministic Ollama temperature, seed, and optional token-limit settings,
- writes structured `*-tool-calling.json` results for standard runs and `*-tool-calling-comparison.json` results for comparison runs.

Tool Search comparison is disabled by default and currently supports the in-memory regex index only. A comparison request runs paired advisor executions sequentially, alternates which advisor runs first across repetitions by default, and retains both result sets without assigning an aggregate winner. Each row reports whether its explicit case contract passed, while preserving every named assertion and raw trace needed to interpret that verdict.

An explicitly opt-in `toolSearchSmoke` Gradle task validates the live Tool Search response wrapper and raw-to-normalized trace linkage against one already-installed Ollama model. It is not connected to `test`, `check`, or `build`, enforces Ollama's `never` pull strategy, and treats model behavior categories as diagnostic output rather than merge gates. See [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md#opt-in-tool-search-smoke-automation) for invocation and case-selection details.

The separate `toolSearchMatrixBaseline` task reproduces the locked July 12 three-model/five-case protocol from canonical Java cases and writes a raw trace, shared v1 evidence manifest, and Markdown comparison under a new dated `build/tool-search-matrix/` directory. It verifies every raw-to-normalized discovery linkage and classifies contract failures into six explicit diagnostic categories. Its report compares both the originally recorded and corrected July 12 counts, with the request-construction correction called out as a confounder.

Saved matrix directories can be checked with `toolSearchMatrixVerify` or have
only their deterministic `SUMMARY.md` regenerated with
`toolSearchMatrixReanalyze`. Both commands are offline, accept current v1
manifests and the earlier unversioned legacy-v0 manifest, and never start Spring
or contact Ollama.

### Local Fixture Evaluation Benchmark

`POST /api/lab/evaluations` runs under the `local` profile. It:

- evaluates public deterministic fixtures through Spring AI's `Evaluator` contract without calling a model or provider,
- records user input, optional context, response text, evaluator provider/model, pass/fail, score, feedback, and evaluator metadata,
- accepts an optional `fixtureIds` list to select the public fixture cases,
- writes structured `*-evaluation.json` results to the same output directory.

This establishes the result-row contract for later AI-judged evaluation. It does not claim to measure model quality; live evaluator models remain a separate opt-in phase.

All benchmarks are local-first and offline-safe by default:

- default builds and tests require no credentials or running Ollama instance,
- live model runs require the `local` profile or explicit configuration,
- generated benchmark outputs stay under ignored `build/` directories.

Result filenames include nanosecond timestamps and short run identifiers so repeated runs cannot overwrite one another when they start at the same instant.

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
```

The same isolated offline suite covers matrix protocol, trace-integrity, and failure-classification behavior. Live matrix execution is an explicit separate task documented in `docs/ENVIRONMENT.md` and is never part of the normal build lifecycle.

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
