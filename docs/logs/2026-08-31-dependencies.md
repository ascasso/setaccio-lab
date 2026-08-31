# Dependency update

On 2026-08-31, the dependency catalog and Spring Boot plugin were updated.

## Changes

- Spring Boot `4.1.0` -> `4.1.1`.
- Spring AI BOM and tool-search starter `2.0.0` -> `2.0.1`.
- JUnit `6.0.3` -> `6.1.3`.
- Commons Codec `1.22.0` -> `1.22.1`.
- Bouncy Castle `1.84` -> `1.85.2`.
- Retained AssertJ `3.27.7`, SLF4J `2.0.18`, Caffeine `3.2.4`, and the
  dependency-management plugin `1.1.7`.

Spring AI 2.0.1 upgrade notes were checked against the repository source; the
documented Redis, Mistral, MCP, and tool API changes do not affect this code.
Provider-free test fixtures were updated for the 2.0.1 non-null response
metadata contract and the named `toolSearchTool` `query` input schema.
Chat and Tool Search usage capture also treats Spring AI's `EmptyUsage` marker
as unavailable rather than recording synthetic zero-token values.

## Verification

- `./gradlew :setaccio-lab:dependencies --configuration runtimeClasspath --no-daemon`
  resolved Spring Boot `4.1.1`, Spring AI `2.0.1`, Commons Codec `1.22.1`,
  Bouncy Castle `1.85.2`, and Caffeine `3.2.4`.
- `./gradlew clean build --no-daemon` passed all three modules, including the
  `setaccio-core` Spring-free classpath guard and provider-free tests.
- `git diff --check` passed.

No provider, Docker, or model execution was used. No push was performed.
