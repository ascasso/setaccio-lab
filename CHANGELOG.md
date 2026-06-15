# Changelog

All notable changes to `setaccio-lab` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Wired the local vision benchmark endpoint through a public Spring AI/Ollama service that hashes inputs with `setaccio-core`, returns one row per file/model pair, and writes raw JSON results under `build/lab-results/`.
- Added public-safe lab server support for upload temp-file handling, MIME detection, Caffeine-backed result caches, and benchmark output configuration.
- Initial public repository skeleton with `setaccio-core` as a plain Java library and `setaccio-lab` as a Spring Boot / Spring AI application.
- Added `setaccio-testcontainers` as an optional skeleton module for future Docker/Testcontainers-backed integration tests.

### Changed

- Clarified that `setaccio-lab` is also the public showcase/reference surface for reusable AI/file-processing work, while private Setaccio product code stays out of the repository.
- Upgraded `setaccio-lab` from Spring AI `2.0.0-M4` to `2.0.0-RC1`.
- Expanded public documentation to describe the intended Spring AI provider and model-type test harness scope.
- Added environment variable documentation for current and planned live provider/model tests.
- Added `OLLAMA_API_BASE` as a supported fallback alias for local Ollama configuration.
- Added Spring AI evaluation testing and Testcontainers planning notes.
- Added AssertJ as the shared test assertion library.
- Documented Spring AI Anthropic chat configuration and future Anthropic-specific test surfaces.
- Documented Spring AI Google GenAI credential mapping and future Google-specific test surfaces.
- Expanded Google GenAI notes for grounding, server-side tool metadata, cached content, thought signatures, and thinking option compatibility.
