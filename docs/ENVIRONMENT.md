# Environment Configuration

This project should keep default builds local-safe. Unit tests and smoke tests must run without provider credentials, without calling remote AI APIs, and without pulling local models.

Live provider checks are opt-in and should use environment variables or ignored local config only. Do not commit `.env`, credentials, generated audio/image outputs, or provider response payloads outside ignored build directories.

## Current Local Variables

These variables are supported by the current `setaccio-lab` application config.

| Variable | Required for default build | Used for | Notes |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | No | Anthropic integration | Empty by default. Required only for explicit live Anthropic runs once those are added. |
| `OLLAMA_BASE_URL` | No | Ollama integration | Preferred Ollama base URL variable. Defaults through `OLLAMA_API_BASE`, then `http://localhost:11434`. |
| `OLLAMA_API_BASE` | No | Ollama integration | Supported fallback alias for local setups that already use this name. |
| `OLLAMA_MODEL` | No | Ollama chat/vision model | Defaults to `granite3.2-vision`. |

The current Spring AI Ollama mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.ollama.base-url` | `${OLLAMA_BASE_URL:${OLLAMA_API_BASE:http://localhost:11434}}` |
| `spring.ai.ollama.chat.model` | `${OLLAMA_MODEL:granite3.2-vision}` |
| `spring.ai.model.chat` | Spring AI defaults to `ollama` when the Ollama chat starter is active. Set explicitly when multiple chat model starters are enabled. |
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
| Anthropic | `ANTHROPIC_API_KEY` | Existing config maps this into `spring.ai.anthropic.api-key`. |
| OpenAI | `OPENAI_API_KEY` | Planned config should map this into `spring.ai.openai.api-key`. |
| Microsoft Azure OpenAI | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` | Some setups may also require deployment/model names and API version. |
| Amazon Bedrock | `AWS_REGION` plus standard AWS credentials such as `AWS_PROFILE` or `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Prefer standard AWS credential resolution where practical. |
| Google Vertex AI | `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION`, `GOOGLE_APPLICATION_CREDENTIALS` | Prefer application-default credentials or a local service-account file outside git. |
| Google Gemini / GenAI | `GOOGLE_API_KEY` | Use only if the selected Spring AI integration supports API-key based Google access. |
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
