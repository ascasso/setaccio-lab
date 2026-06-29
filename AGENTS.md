# AGENTS.md

This file is the repo-local guide for Codex, Claude Code, and other AI agents working in this repository. Follow it unless the user gives a direct conflicting instruction or a higher-priority system rule applies. When in doubt, preserve the public/private boundary, do less, and ask.

Do not stage, commit, or push unless explicitly asked.

## Agent Hard Stops

- Never copy private Setaccio product code, docs, deployment details, issue history, roadmap text, API modules, database code, or UI code into this repo.
- Never add Spring, Spring Boot, Spring AI, or Spring annotations to `setaccio-core`.
- Never make default tests call live Ollama, Anthropic, or other remote providers.
- Never add credentials, tokens, API keys, or private endpoint details to tracked files.
- Never add Docker or Testcontainers dependencies to `setaccio-lab`; keep them in `setaccio-testcontainers`.
- Never make `setaccio-lab` depend on `setaccio-testcontainers`.
- Never stage, commit, or push without explicit user instruction.

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

## Current State Snapshot (as of 2026-06-18)

This repo was bootstrapped from the Setaccio monorepo but has been intentionally reduced:

- Root Gradle build with Java 25.
- `setaccio-core` is a plain Java BLAKE3 utility library.
- `setaccio-lab` is a Spring Boot / Spring AI app using Spring AI `2.0.0`.
- The local vision benchmark endpoint is wired at `POST /api/lab/vision`; it accepts uploaded images and model names, uses local Ollama through Spring AI, hashes inputs through `setaccio-core`, returns structured rows, and writes JSON under `build/lab-results/`.
- The default Ollama model is `gemma4:e2b`.
- `setaccio-testcontainers` remains an optional skeleton for future container-backed integration tests.

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
- For evaluation tests, track Spring AI's `Evaluator`, `EvaluationRequest`, `RelevancyEvaluator`, and `FactCheckingEvaluator`, but re-check the exact Spring AI 2.0 API before implementation.
- For container-backed tests, track Spring AI's `spring-ai-spring-boot-testcontainers` support and service connections, but keep Docker/Testcontainers opt-in.
- Keep Testcontainers dependencies isolated in `setaccio-testcontainers`; do not add them to `setaccio-lab`.
- Tool-calling tests should wait until the chosen Spring AI 2.0 tool API is confirmed.
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
- Chat benchmark service.
- Tool benchmark service.
- Calculator/weather demo tools.
- MCP code.
- Any private Setaccio server utility classes.

## Near-Term Implementation Plan

Completed:

- Keep `setaccio-core` Spring-free and buildable.
- Keep `setaccio-lab` as the Spring/Spring AI host.
- Wire the first local-only vision benchmark service with uploaded images, per-request model names, Ollama calls, core hashing, structured rows, and JSON output under `build/lab-results/`.
- Document local Ollama setup and provider environment variables.
- Add tests for the current core and vision benchmark behavior.

Pending:

- Add public sample prompts and ignored sample image folders.
- Add or refine Spring AI evaluation and Testcontainers planning docs before wiring evaluator or container-backed integration tests.
- Keep container-backed work isolated in `setaccio-testcontainers`.
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

When benchmark execution is added:

- Mock `OllamaChatModel` or use a small adapter boundary so service tests do not require a live model.
- Verify model names are passed through per request.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include model, input name, input hash, latency, output text, success flag, and error details.
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

- Relevancy evaluator tests for context-grounded responses.
- Fact-checking evaluator tests for claim-versus-context behavior.
- Configurable judge/evaluator provider and model selection.
- Optional Spring AI Testcontainers service connections for local model services and vector stores.

Tool-calling phase:

- Expected tool selected.
- Arguments valid.
- Tool result incorporated correctly.
- Tool-call observability checked against the Spring AI 2.0 API.

MCP phase:

- Direct Spring AI tool call versus MCP tool call comparison.
- Local-only transport tests.
- Security and argument-validation behavior.

## Build Commands

```bash
./gradlew :setaccio-core:test
./gradlew :setaccio-lab:test
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

Do not stage, commit, or push unless explicitly asked. Leave changes unstaged by default, then report the modified files, tests run, and what would be committed if requested.

Before committing in a future session:

- Run the relevant Gradle build.
- Check `git status --short`.
- Confirm no private docs or generated outputs are staged.
- Confirm no `.DS_Store`, `.gradle`, or `build` outputs are tracked.
