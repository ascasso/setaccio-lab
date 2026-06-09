# Test Plan

## Near Term

- Keep `setaccio-core` Spring-free with a dependency check that fails if Spring Framework or Spring Boot appears on the core runtime classpath.
- Preserve direct unit coverage for both BLAKE3 implementations.
- Add known BLAKE3 test vectors for empty input, strings, byte arrays, and streams.
- Keep `setaccio-lab` context smoke tests on the `test` profile with no live Ollama or Anthropic calls.
- Add controller validation tests for missing files, missing model names, and malformed model lists.

## Vision Benchmark Phase

- Add service-level tests with a mocked `OllamaChatModel`.
- Verify per-request model selection is passed through Spring AI options.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include model, input name, input hash, latency, output text, and failure details.
- Verify JSON result writing once result persistence is introduced.

## Optional Integration Tests

- Keep live Ollama tests opt-in behind a dedicated Gradle property or profile.
- Require explicit model names for live runs so CI never pulls models implicitly.
- Record live-run outputs under ignored build directories only.

## Later Phases

- Add structured-output reliability tests for text prompts.
- Add tool-calling tests only after the selected Spring AI tool-calling API is stable for this project.
- Add MCP tests after direct Spring AI tool-calling tests are reliable.
