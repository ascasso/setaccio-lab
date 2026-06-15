# Test Plan

## Near Term

- Keep `setaccio-core` Spring-free with a dependency check that fails if Spring Framework or Spring Boot appears on the core runtime classpath.
- Preserve direct unit coverage for both BLAKE3 implementations.
- Add known BLAKE3 test vectors for empty input, strings, byte arrays, and streams.
- Keep `setaccio-lab` context smoke tests on the `test` profile with no live Ollama or Anthropic calls.
- Add controller validation tests for missing files, missing model names, and malformed model lists.
- Keep Spring AI evaluation and Testcontainers APIs under review before adding harness abstractions; the current Spring AI 2.0 docs pages for these areas may lag the RC1 API.

## Vision Benchmark Phase

- Maintain service-level tests with a mocked `OllamaChatModel`.
- Verify per-request model selection is passed through Spring AI options.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include model, input name, input hash, latency, output text, and failure details.
- Verify JSON result writing under ignored `build/lab-results/` output.

## Optional Integration Tests

- Keep live Ollama and remote-provider tests opt-in behind a dedicated Gradle property or profile.
- Require explicit provider and model names for live runs so CI never pulls models or calls providers implicitly.
- Require provider credentials through local environment variables or ignored local config.
- Keep provider and model-type environment variables documented in `docs/ENVIRONMENT.md`.
- Record live-run outputs under ignored build directories only.

## Evaluation Testing

- Use Spring AI's `Evaluator` contract as the model for AI-judged evaluation rows where practical.
- Track each evaluation input as user text, optional context/data, model response, evaluator provider/model, pass/fail result, and raw evaluator explanation.
- Use `RelevancyEvaluator` for RAG/context relevance checks when retrieval benchmarks are added.
- Use `FactCheckingEvaluator` for claim-versus-context checks when factuality benchmarks are added.
- Keep evaluator models configurable and separate from the model being tested; the judge model may be different from the generation model.
- Keep deterministic fixture-based assertions for default tests. AI-judged evaluator tests must be opt-in unless backed by mocks or recorded fixtures.
- If custom evaluator prompts are added, keep prompt templates public-safe and test the required placeholders.

## Testcontainers

- Keep Docker/Testcontainers dependencies isolated in `setaccio-testcontainers`; `setaccio-lab` must not require them.
- Consider Spring AI's `spring-ai-spring-boot-testcontainers` module when adding container-backed integration tests.
- Prefer Spring Boot service connections where they simplify wiring local model services or vector stores.
- Use `OllamaContainer` only for explicit integration tests; do not make Docker or model pulls required for normal builds.
- Relevant future service connections include Ollama, local/vector stores such as Chroma, Milvus, Qdrant, Typesense, Weaviate, and infrastructure such as OpenSearch or LocalStack when those test surfaces are added.
- Keep Testcontainers tests behind a dedicated Gradle task, profile, or property so local unit tests and CI remain fast and offline by default.

## Later Phases

- Add structured-output reliability tests for text prompts.
- Add provider comparison tests for Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama as integrations are added.
- Add Anthropic-specific chat tests for default options, per-request option overrides, streaming, multimodal image/PDF inputs, tool choice/tool calling, and extended thinking constraints.
- Add Google GenAI-specific chat tests for Gemini Developer API key mode, Vertex AI mode, multimodal prompts, response MIME type, Google Search grounding, server-side tool invocation metadata, safety settings, cached content, thought signatures, and thinking option compatibility.
- Add model-type tests for chat completion, embedding, text to image, audio transcription, text to speech, and moderation.
- Add detailed local Ollama setup docs before adding required Ollama live-test workflows.
- Add a dedicated `setaccio-testcontainers` integration-test task before adding Docker-backed tests.
- Add tool-calling tests only after the selected Spring AI tool-calling API is stable for this project.
- Add MCP tests after direct Spring AI tool-calling tests are reliable.
