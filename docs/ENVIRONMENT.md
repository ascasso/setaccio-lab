# Environment Configuration

This project should keep default builds local-safe. Unit tests and smoke tests must run without provider credentials, without calling remote AI APIs, and without pulling local models.

Live provider checks are opt-in and should use environment variables or ignored local config only. Do not commit `.env`, credentials, generated audio/image outputs, or provider response payloads outside ignored build directories.

## Current Local Variables

These variables are supported by the current `setaccio-lab` application config or are used by the documented local workflow.

| Variable | Required for default build | Used for | Notes |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | No | Anthropic integration | Empty by default. Required only for explicit live Anthropic runs once those are added. |
| `ANTHROPIC_BASE_URL` | No | Anthropic integration | Optional override for the Anthropic API base URL. |
| `ANTHROPIC_MODEL` | No | Anthropic chat model | Optional default model for live Anthropic chat runs. |
| `ANTHROPIC_MAX_TOKENS` | No | Anthropic chat options | Optional maximum token override for live Anthropic chat runs. |
| `GOOGLE_API_KEY` | No | Google GenAI integration | Preferred API key variable for Gemini Developer API mode. |
| `GEMINI_API_KEY` | No | Google GenAI integration | Supported local alias for Gemini Developer API keys. |
| `GOOGLE_GENAI_MODEL` | No | Google GenAI chat model | Optional default model for future live Google GenAI chat runs. |
| `GOOGLE_GENAI_LOCATION` | No | Google GenAI Vertex AI mode | Optional location for future Vertex AI mode. Use `global` for Gemini 3 Pro Preview where required. |
| `OLLAMA_BASE_URL` | No | Ollama integration | Preferred Ollama base URL variable. Defaults through `OLLAMA_API_BASE`, then `http://localhost:11434`. |
| `OLLAMA_API_BASE` | No | Ollama integration | Supported fallback alias for local setups that already use this name. |
| `OLLAMA_MODEL` | No | Ollama chat/vision model | Defaults to `gemma4:e2b`. |
| `SETACCIO_LAB_INPUT_DIR` | No | Local image workspace | Optional local directory for comparison images, such as `/Users/username/Pictures/lab`. If unset, there is no default input directory and the app continues to use uploaded files and other explicit inputs. |
| `SETACCIO_LAB_OUTPUT_DIR` | No | Benchmark result output | Defaults to `build/lab-results/`; keep outputs under ignored build directories. |

The current Spring AI Anthropic mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY:}` |
| `spring.ai.anthropic.base-url` | Planned: `${ANTHROPIC_BASE_URL:}` when a base URL override is needed. |
| `spring.ai.anthropic.chat.options.model` | Planned: `${ANTHROPIC_MODEL:}` for explicit live Anthropic chat runs. |
| `spring.ai.anthropic.chat.options.max-tokens` | Planned: `${ANTHROPIC_MAX_TOKENS:}` for explicit live Anthropic chat runs. |
| `spring.ai.anthropic.chat.options.temperature` | Planned: explicit test option, not a default requirement. |

Spring AI also supports Anthropic runtime options through `AnthropicChatOptions` and per-request `Prompt` options. Future tests should cover default options versus request-specific overrides.

Anthropic-specific future test surfaces include:

- synchronous chat responses,
- streaming chat responses,
- multimodal image input,
- PDF document input,
- tool choice and tool-calling behavior,
- extended thinking settings where supported by the selected Claude model.

Extended thinking tests must verify compatible model selection, temperature requirements, and token-budget constraints before running live.

The planned Spring AI Google GenAI mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.google.genai.api-key` | Planned: `${GOOGLE_API_KEY:${GEMINI_API_KEY:}}` for Gemini Developer API mode. |
| `spring.ai.google.genai.project-id` | Planned only for Vertex AI mode. |
| `spring.ai.google.genai.location` | Planned only for Vertex AI mode. Map from `GOOGLE_GENAI_LOCATION` or `GOOGLE_CLOUD_LOCATION`. |
| `spring.ai.google.genai.credentials-uri` | Planned only for Vertex AI mode. |
| `spring.ai.google.genai.chat.model` | Planned: `${GOOGLE_GENAI_MODEL:gemini-2.0-flash}` for explicit live Google GenAI chat runs. |
| `spring.ai.google.genai.chat.response-mime-type` | Planned explicit test option for text versus JSON responses. |
| `spring.ai.google.genai.chat.google-search-retrieval` | Planned explicit test option for Google Search grounding. |
| `spring.ai.google.genai.chat.include-server-side-tool-invocations` | Planned explicit test option for observing server-side tool metadata. Gemini Developer API only, not Vertex AI. |
| `spring.ai.google.genai.chat.thinking-budget` | Planned explicit test option. Mutually exclusive with `thinking-level`. |
| `spring.ai.google.genai.chat.thinking-level` | Planned explicit test option. Mutually exclusive with `thinking-budget`. |
| `spring.ai.google.genai.chat.include-thoughts` | Planned explicit test option for thought signatures and function-calling validation. |
| `spring.ai.google.genai.chat.safety-settings` | Planned explicit test option for safety filter behavior. |
| `spring.ai.google.genai.chat.cached-content-name` | Planned explicit test option for cached content reuse. |
| `spring.ai.google.genai.chat.use-cached-content` | Planned explicit test option for cached content reuse. |

`GEMINI_API_KEY` is a project-supported alias for local developer environments. The Spring AI property itself is `spring.ai.google.genai.api-key`.

`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are OAuth client credentials and are not used for Spring AI Google GenAI chat configuration.

Google GenAI-specific future test surfaces include:

- Gemini Developer API key mode,
- Vertex AI mode with Google Cloud project, location, and credentials,
- multimodal prompts,
- Google Search grounding,
- server-side tool invocation metadata,
- safety settings,
- response MIME type selection for text versus JSON,
- thinking budget/level options where supported by the selected Gemini model,
- thought signatures with function calling,
- cached content creation and reuse for large contexts.

Google GenAI thinking tests must enforce model-specific compatibility:

- `thinking-level` and `thinking-budget` are mutually exclusive.
- Gemini 3 Pro uses `thinking-level`; Gemini 3 Pro Preview requires the global endpoint.
- Gemini 2.5 models use `thinking-budget`.
- Gemini 2.0 Flash does not support thinking options.
- `include-thoughts` affects function-calling behavior and can increase token usage.

Server-side tool invocation tests should verify metadata rather than local tool execution. The metadata should include invocation type, id, tool type, arguments, and response data when `include-server-side-tool-invocations` is enabled.

The current Spring AI Ollama mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.ollama.base-url` | `${OLLAMA_BASE_URL:${OLLAMA_API_BASE:http://localhost:11434}}` |
| `spring.ai.ollama.chat.model` | `${OLLAMA_MODEL:gemma4:e2b}` |
| `spring.ai.model.chat` | `ollama`, set explicitly because multiple chat model starters are present. |
| `spring.ai.ollama.init.pull-model-strategy` | Should stay `never` for tests unless a deliberate opt-in workflow is added. |

`OLLAMA_API_BASE` is a project-supported alias for local developer environments. The Spring AI property itself is `spring.ai.ollama.base-url`.

## Planned Live-Test Switches

Before any provider-backed test is added, introduce explicit switches so CI and normal local builds stay offline.

| Variable | Purpose |
| --- | --- |
| `SETACCIO_LAB_LIVE_AI_ENABLED` | Must be `true` before any live provider/model test runs. |
| `SETACCIO_LAB_PROVIDER` | Provider under test, such as `anthropic`, `openai`, `azure-openai`, `bedrock`, `vertex-ai`, `google-genai`, or `ollama`. |
| `SETACCIO_LAB_MODEL_TYPES` | Comma-separated model types to test, such as `chat`, `embedding`, `image`, `transcription`, `speech`, or `moderation`. |
| `SETACCIO_LAB_MODELS` | Comma-separated model names for the selected provider and model type. |
| `SETACCIO_LAB_OUTPUT_DIR` | Optional output directory. Defaults should stay under `build/lab-results/`. |
| `SETACCIO_LAB_EVALUATOR_PROVIDER` | Optional judge/evaluator provider for AI-judged tests. |
| `SETACCIO_LAB_EVALUATOR_MODELS` | Optional comma-separated judge/evaluator model names. |
| `SETACCIO_LAB_TESTCONTAINERS_ENABLED` | Must be `true` before any Testcontainers-backed AI integration test runs. |

## Provider Variables

Use provider-specific credentials only for explicit live tests. Exact requirements can vary by Spring AI starter and provider account type, so verify against the Spring AI reference when adding each integration.

| Provider | Expected variables | Notes |
| --- | --- | --- |
| Anthropic | `ANTHROPIC_API_KEY`, optional `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL`, `ANTHROPIC_MAX_TOKENS` | Existing config maps the API key into `spring.ai.anthropic.api-key`. Other variables are planned for explicit live Anthropic runs. |
| OpenAI | `OPENAI_API_KEY` | Planned config should map this into `spring.ai.openai.api-key`. |
| Microsoft Azure OpenAI | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` | Some setups may also require deployment/model names and API version. |
| Amazon Bedrock | `AWS_REGION` plus standard AWS credentials such as `AWS_PROFILE` or `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Prefer standard AWS credential resolution where practical. |
| Google Vertex AI | `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION` or `GOOGLE_GENAI_LOCATION`, `GOOGLE_APPLICATION_CREDENTIALS` | Prefer application-default credentials or a local service-account file outside git. |
| Google Gemini / GenAI | `GOOGLE_API_KEY` or `GEMINI_API_KEY`, optional `GOOGLE_GENAI_MODEL` | Use API-key based Gemini Developer API mode. OAuth client credentials are not relevant for Spring AI Google GenAI chat. |
| Ollama | `OLLAMA_BASE_URL` or `OLLAMA_API_BASE`, `OLLAMA_MODEL` | Local only; no remote credential required. Prefer `OLLAMA_BASE_URL` for new setup, but `OLLAMA_API_BASE` is supported. |

## Model-Type Variables

When model-type-specific tests are added, keep names explicit so a live run cannot silently switch models.

| Model type | Suggested model variable |
| --- | --- |
| Chat completion | `SETACCIO_LAB_CHAT_MODELS` |
| Embedding | `SETACCIO_LAB_EMBEDDING_MODELS` |
| Text to image | `SETACCIO_LAB_IMAGE_MODELS` |
| Audio transcription | `SETACCIO_LAB_TRANSCRIPTION_MODELS` |
| Text to speech | `SETACCIO_LAB_SPEECH_MODELS` |
| Moderation | `SETACCIO_LAB_MODERATION_MODELS` |

## Evaluation and Testcontainers

Spring AI includes model-evaluation support and Testcontainers service-connection support. Keep both opt-in for this project.

| Area | Variables |
| --- | --- |
| AI-judged evaluation | `SETACCIO_LAB_EVALUATOR_PROVIDER`, `SETACCIO_LAB_EVALUATOR_MODELS` |
| Testcontainers-backed integrations | `SETACCIO_LAB_TESTCONTAINERS_ENABLED` |

Do not require Docker, Testcontainers, or live evaluator models for default builds.

## Ollama Setup

The detailed local Ollama setup guide still needs to be written. It should cover:

- installing Ollama,
- starting the local Ollama service,
- confirming the API server is reachable at `http://localhost:11434` or `http://127.0.0.1:11434`,
- selecting small test models,
- pulling models manually before live tests,
- setting `OLLAMA_BASE_URL` or using the supported `OLLAMA_API_BASE` alias,
- setting explicit model variables,
- setting `spring.ai.model.chat=ollama` explicitly if more than one chat provider starter is active,
- keeping `spring.ai.ollama.init.pull-model-strategy=never` for default tests,
- keeping live Ollama tests opt-in.

Do not add tests that auto-pull large models.
