# Test Plan

## Near Term

- Keep `setaccio-core` Spring-free with a dependency check that fails if Spring Framework or Spring Boot appears on the core runtime classpath.
- Preserve direct unit coverage for both BLAKE3 implementations.
- Add known BLAKE3 test vectors for empty input, strings, byte arrays, and streams.
- Keep `setaccio-lab` context smoke tests on the `test` profile with no live Ollama or Anthropic calls.
- Add controller validation tests for missing files, missing model names, and malformed model lists.
- Maintain the implemented deterministic Spring AI evaluator contract, and keep AI-judged evaluation and Testcontainers APIs under review before adding either live abstraction.

## Shared Evidence Lifecycle

- Keep the shared evidence primitives plain Java and independent of Spring application startup, model providers, and suite-specific result row types.
- Require a positive versioned manifest envelope with suite and run identity, generation time, Git commit and dirty state, Spring Boot and Spring AI versions, execution engine, run settings, and relative artifact descriptors.
- Allocate unique run directories atomically and refuse named-directory or manifest overwrites.
- Use BLAKE3 for benchmark input identity and streaming SHA-256 for generated artifact integrity.
- Reject absolute, parent-traversing, cross-platform absolute, or symbolic-link artifact paths.
- Verify saved runs entirely offline and report missing, modified, empty, duplicate, undeclared, or unsafe artifacts clearly.
- Keep manifest JSON free of hostnames, absolute paths, credentials, and raw private environment details.
- Until a benchmark suite adopts the lifecycle, do not describe existing Tool Search, vision, chat, or evaluation output as using the shared manifest.

## Vision Benchmark Phase

- Maintain service-level tests with a mocked `OllamaChatModel`.
- Verify per-request model selection is passed through Spring AI options.
- Verify uploaded files are copied to temporary files and cleaned up.
- Verify result rows include model, input name, input hash, latency, output text, and failure details.
- Verify JSON result writing under ignored `build/lab-results/` output.

## Chat Benchmark Phase

- Maintain the dedicated local-only chat benchmark surface at `POST /api/lab/chat`.
- Keep service-level tests backed by a mocked `OllamaChatModel`; do not add live Ollama calls to default tests.
- Verify per-request model selection is passed through Spring AI chat options for every prompt/model pair.
- Verify default prompts and caller-provided prompt lists through controller tests.
- Verify result rows capture provider/model, prompt id/text, advisor mode, latency, output, failure details, and token-usage metadata when Spring AI exposes it.
- Verify JSON result writing under ignored `build/lab-results/` output as `*-chat.json`.

## Optional Integration Tests

- Keep live Ollama and remote-provider tests opt-in behind a dedicated Gradle property or profile.
- Require explicit provider and model names for live runs so CI never pulls models or calls providers implicitly.
- Require provider credentials through local environment variables or ignored local config.
- Keep provider and model-type environment variables documented in `docs/ENVIRONMENT.md`.
- Record live-run outputs under ignored build directories only.

## Evaluation Testing

- Maintain the dedicated local-only `POST /api/lab/evaluations` fixture benchmark with no live model or provider call.
- Use Spring AI's `Evaluator` contract for deterministic fixture rows before adding AI-judged evaluation.
- Track each evaluation input as user text, optional context/data, model response, evaluator provider/model, pass/fail result, score, raw evaluator explanation, and evaluator metadata.
- Keep public fixtures deterministic and make the evaluator implementation and required terms explicit in result metadata.
- Use `RelevancyEvaluator` for RAG/context relevance checks when retrieval benchmarks are added.
- Use `FactCheckingEvaluator` for claim-versus-context checks when factuality benchmarks are added.
- Keep evaluator models configurable and separate from the model being tested; the judge model may be different from the generation model.
- Keep deterministic fixture-based assertions for default tests. AI-judged evaluator tests must be opt-in unless backed by mocks or recorded fixtures.
- If custom evaluator prompts are added, keep prompt templates public-safe and test the required placeholders.

## Tool Calling and Tool Search

- Keep Spring AI Tool Search Tool support disabled for default tests and normal local runs.
- Maintain the dedicated local-only tool-calling benchmark surface at `POST /api/lab/tools`.
- Use mocked `OllamaChatModel` service tests for automated coverage; do not add live Ollama integration tests to the default Gradle lifecycle.
- Maintain mocked comparison coverage that runs paired standard `ToolCallingAdvisor` and `ToolSearchToolCallingAdvisor` executions sequentially with the same models, prompts, selected tools, and deterministic generation settings.
- Keep Tool Search comparison behind `SETACCIO_LAB_TOOL_SEARCH_ENABLED=true`, and reject standalone `tool_search` requests so each Tool Search result includes its standard baseline.
- Maintain local deterministic tools and expectation-aware cases for arithmetic, fixed date/time, catalog lookup, multi-step behavior, no-match behavior, tool abstention, and controlled callback failure. Do not call live network services from default tests.
- Verify Spring AI `ToolCallback` metadata for the deterministic fixtures so future advisor comparisons can use stable tool names and input schemas.
- Keep `regex` as the only executable index until separate, deterministic comparison coverage is added for another index. Keep `lucene` and `vector` rejected; `vector` also needs a public-safe `VectorStore` fixture.
- Verify result rows capture selected advisor mode, repetition, pair order, effective seed, requested tools, executed tools, tool errors, normalized Tool Search queries/discoveries, named contract assertions, model/provider, prompt, output, latency, and any token-usage metadata exposed by Spring AI.
- Keep comparison order counterbalanced across repetitions by default, and retain a test proving the standard-first/tool-search-first alternation.
- Verify repeated writes with the same suite and start instant produce distinct result files rather than overwriting an earlier run.
- Keep live model runs behind the `local` profile, explicit API calls, or the dedicated opt-in `toolSearchSmoke` task. Do not attach that task to `test`, `check`, `build`, or default CI.
- Require `toolSearchSmoke` to receive an explicit already-installed Ollama model, force `spring.ai.ollama.init.pull-model-strategy=never`, and select cases directly from `ToolBenchmarkCases.defaults()` with the complete `ToolBenchmarkCases.toolNames()` fixture set.
- Keep `toolSearchSmokeTest` offline. Cover semantic and ordinal case selection, live-wrapper parsing fixtures, raw-to-normalized discovery comparison, malformed results, trace-linkage failures, and every console summary bucket without starting Ollama.
- Fail the live smoke task only for startup/invocation failures, malformed results, missing trace linkages, or raw-versus-normalized discovery mismatches. Treat missing searches, zero matches, required-but-unexecuted tools, and output-contract failures as diagnostic model behavior unless an explicit hypothesis says otherwise.
- Keep the post-fix three-model baseline in the separate `toolSearchMatrixBaseline` task with the July 12 models, five canonical case IDs, two repetitions, seeds 42/43, temperature 0.0, no token ceiling, alternate order, paired sequential execution, and complete fixture tool list locked in code.
- Require a new dated `build/tool-search-matrix/` output directory and retain one raw comparison JSON, `manifest.json`, and `SUMMARY.md`; refuse overwrites and verify the raw artifact SHA-256.
- Verify every linked non-empty raw Tool Search discovery exactly matches normalized tool names. Treat malformed/linkage/mismatch conditions and unclassified failed contracts as matrix-integrity failures.
- Classify failed canonical contracts exhaustively as no search call, zero discovery, incomplete discovery, discovered-not-executed, execution failure, or output-contract failure. Preserve precedence tests and a successful zero-discovery abstention test.
- Compare post-fix counts to both recorded and corrected July 12 counts, and require the summary to name corrected request construction and Issue #20 discovery normalization as confounders. Do not present Issue #21's chat fix as a tool pass-rate cause.

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
- Add MCP tests after direct Spring AI tool-calling tests are reliable.
