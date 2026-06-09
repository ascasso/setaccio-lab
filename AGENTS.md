# AGENTS.md

## Repository Purpose

`setaccio-lab` is the public, Apache-2.0 side of the Setaccio split.

This repository is intended to contain:

- `setaccio-core`: a minimal plain Java library for reusable Setaccio primitives.
- `setaccio-lab`: a Spring Boot / Spring AI evaluation app for model, prompt, tool-calling, and later MCP experiments.

The private Setaccio application code remains outside this repository. Do not copy private product docs, private roadmap text, private deployment details, private API modules, database code, UI code, or private server code into this repo.

## Current Split Boundary

Public:

- `setaccio-core`
- `setaccio-lab`
- Public-facing README, changelog, test plan, examples, and benchmark fixtures that are safe to publish.

Private:

- Closed-source Setaccio application modules.
- Private product docs, deployment docs, logs, workflows, and implementation plans.

The future private repo is expected to depend on this public repo first through a Gradle composite build, then later through published artifacts if/when `setaccio-lab` or `setaccio-core` are published.

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
- HTTP endpoints for local evaluation.
- Public benchmark fixtures and prompts.
- Result files written under ignored build directories.

Do not turn `setaccio-lab` into the private Setaccio product. It should remain a focused evaluation harness.

## Current State

This repo was bootstrapped from the Setaccio monorepo but has been intentionally reduced:

- Root Gradle build with Java 25.
- `setaccio-core` copied and cleaned into a plain Java library.
- `setaccio-lab` created as a minimal Spring Boot / Spring AI app.
- Spring AI version is currently `2.0.0-RC1` in `setaccio-lab`.
- Spring AI `2.0.0-RC1` was verified available in Maven Central on 2026-06-08.
- No git commits were made during the initial scaffold work.

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

Spring AI `2.0.0-RC1` replaced the older M8/M4 planning target.

Relevant upgrade concerns for this repo:

- Keep future Spring AI upgrades focused and separate from mechanical repo split work.
- Watch direct `ChatModel.call(Prompt)` usage with per-request options. Spring AI 2.0 M5+ changed how partial options are merged. Prefer `ChatClient` where practical, or explicitly combine options with model defaults.
- Tool-calling tests should wait until the chosen Spring AI RC1 tool API is confirmed.
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

1. Keep `setaccio-core` Spring-free and buildable.
2. Keep `setaccio-lab` as the Spring/Spring AI host.
3. Wire the existing local-only vision benchmark endpoint through the first real benchmark service:
   - Accept uploaded images.
   - Accept one or more model names.
   - Use local Ollama through Spring AI.
   - Use per-request model selection.
   - Hash inputs through `setaccio-core`.
   - Return structured benchmark rows.
   - Persist raw result JSON under `build/lab-results/`.
4. Add public sample prompts and ignored sample image folders.
5. Add tests before expanding into chat, tools, or MCP.

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

Live model tests must be opt-in.

Rules:

- Never run live Ollama tests by default in CI.
- Never auto-pull large models in tests.
- Require an explicit Gradle property or profile for live runs.
- Require explicit model names.
- Store live outputs only under ignored build directories.

### Later Test Phases

Text benchmark phase:

- Structured output validity.
- Prompt regression tests.
- JSON parse reliability.

Tool-calling phase:

- Expected tool selected.
- Arguments valid.
- Tool result incorporated correctly.
- Tool-call observability checked against the Spring AI RC1 API.

MCP phase:

- Direct Spring AI tool call versus MCP tool call comparison.
- Local-only transport tests.
- Security and argument-validation behavior.

## Build Commands

```bash
./gradlew :setaccio-core:build
./gradlew :setaccio-lab:build
./gradlew :setaccio-core:build :setaccio-lab:build
./gradlew allDeps
```

Run the lab locally:

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

The lab app uses port `8082`.

## Git Workflow

Do not commit unless explicitly asked.

Before committing in a future session:

- Run the relevant Gradle build.
- Check `git status --short`.
- Confirm no private docs or generated outputs are staged.
- Confirm no `.DS_Store`, `.gradle`, or `build` outputs are tracked.
