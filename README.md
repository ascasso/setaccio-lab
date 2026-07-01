# setaccio-lab

A local-first Spring Boot lab for comparing how AI models analyze real files, starting with image benchmarking, hashing, and reproducible result output.

`setaccio-lab` is the public technical showcase for reusable Setaccio AI/file-processing work that can be inspected without the private product repository. It keeps the primitives, prompts, test fixtures, and benchmark outputs close to the code so model behavior can be reviewed, changed, and compared over time.

## What is in this repo

- `setaccio-core`: a small, Spring-free Java library for reusable Setaccio primitives. Today it provides BLAKE3 hashing utilities backed by Apache Commons Codec and Bouncy Castle.
- `setaccio-lab`: a Spring Boot and Spring AI application for local evaluation work. It is intended for model, prompt, provider, model-type, tool-calling, and later MCP experiments.
- `setaccio-testcontainers`: an optional Testcontainers-backed integration harness. It may depend on `setaccio-lab`, but `setaccio-lab` must not depend on it.
- `docs/`: public test and project notes that describe how the lab should grow without depending on private application code.

This repository is Apache-2.0 licensed and intentionally public-safe. Private Setaccio application code, deployment details, product workflows, and closed-source modules do not belong here.

## Current focus

The first lab track is local vision benchmarking:

- accept uploaded image files,
- run one or more local Ollama models through Spring AI with per-request model selection,
- hash inputs with `setaccio-core`,
- return structured result rows,
- write raw benchmark results under ignored build directories.

The local vision endpoint is a guarded Spring Boot endpoint at `POST /api/lab/vision`. It accepts multipart `files` and a comma-separated `models` parameter, returns one result row per file/model pair, and writes raw JSON results under `build/lab-results/` by default. Live model runs remain opt-in so normal builds and CI do not call Ollama or pull models unexpectedly.

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

## Dependency Versions

Gradle dependency and plugin versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

- Update dependency versions there instead of in `build.gradle` files.
- Version catalog aliases are used from the module build scripts for shared libraries and Spring BOMs.
- This replaces the old root `ext` version block and keeps version changes in one place.

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
