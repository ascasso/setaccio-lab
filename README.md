# setaccio-lab

`setaccio-lab` is an open source Java workspace for experimenting with AI-assisted file analysis, benchmark design, prompt behavior, model types, provider behavior, and tool integration.

The goal is to make repeatable AI evaluation work easier to inspect. Instead of hiding model behavior behind a product UI, this repo keeps the primitives, prompts, test fixtures, and benchmark outputs close to the code so they can be reviewed, changed, and compared over time.

## What is in this repo

- `setaccio-core`: a small, Spring-free Java library for reusable Setaccio primitives. Today it provides BLAKE3 hashing utilities backed by Apache Commons Codec and Bouncy Castle.
- `setaccio-lab`: a Spring Boot and Spring AI application for local evaluation work. It is intended for model, prompt, provider, model-type, tool-calling, and later MCP experiments.
- `setaccio-testcontainers`: an optional Testcontainers-backed integration harness. It may depend on `setaccio-lab`, but `setaccio-lab` must not depend on it.
- `docs/`: public test and project notes that describe how the lab should grow without depending on private application code.

This repository is Apache-2.0 licensed and intentionally public-safe. Private Setaccio application code, deployment details, product workflows, and closed-source modules do not belong here.

## Current focus

The first lab track is local vision benchmarking:

- accept uploaded image files,
- run one or more local Ollama models through Spring AI,
- hash inputs with `setaccio-core`,
- return structured result rows,
- write raw benchmark results under ignored build directories.

The local vision endpoint exists as a guarded Spring Boot endpoint, and benchmark execution is being wired in small, testable steps. Live model runs will remain opt-in so normal builds and CI do not call Ollama or pull models unexpectedly.

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

## Build

```bash
./gradlew :setaccio-core:build
./gradlew :setaccio-lab:build
./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build
```

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
