# Environment Configuration

This project should keep default builds local-safe. Unit tests and smoke tests must run without provider credentials, without calling remote AI APIs, and without pulling local models.

Live provider checks are opt-in and should use environment variables or ignored local config only. Do not commit `.env`, credentials, generated audio/image outputs, or provider response payloads outside ignored build directories.

## Local and Planned Variables

These variables are supported by the current `setaccio-lab` application config, used by the documented local workflow, or reserved for the planned provider sections below.

| Variable | Required for default build | Used for | Notes |
| --- | --- | --- | --- |
| `ANTHROPIC_API_KEY` | No | Explicit Anthropic portability runner | Read only by the opt-in O3 runner after local preflight. Keep it in local environment/config; never put it in a command line, tracked file, or evidence. |
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
| `SETACCIO_LAB_TOOL_FIXTURE_INSTANT` | No | Deterministic tool fixtures | Defaults to `2026-01-15T12:00:00Z`. Used by the fixed-time benchmark tools so default tests never depend on the machine clock. |
| `SETACCIO_LAB_TOOL_SEARCH_ENABLED` | No | Spring AI Tool Search Tool | Defaults to `false`. Set to `true` only for an explicit Tool Search comparison request. |
| `SETACCIO_LAB_TOOL_SEARCH_INDEX_TYPE` | No | Spring AI Tool Search Tool | Defaults to `regex`. `regex` is the only executable index type in the current comparison slice. |

The current Spring AI Anthropic mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.anthropic.api-key` | `${ANTHROPIC_API_KEY:}` |
| `spring.ai.anthropic.base-url` | `${ANTHROPIC_BASE_URL:}` |
| `spring.ai.anthropic.chat.options.model` | `${ANTHROPIC_MODEL:claude-haiku-4-5}` |
| `spring.ai.anthropic.chat.options.max-tokens` | `${ANTHROPIC_MAX_TOKENS:4096}` |
| `spring.ai.anthropic.chat.options.temperature` | Planned: explicit test option, not a default requirement. |

Spring AI also supports Anthropic runtime options through `AnthropicChatOptions` and per-request `Prompt` options. Slice O1 selects the pinned hosted model ID `claude-haiku-4-5-20251001`; it is a provider version identifier, not a locally resolvable content digest. The adapter uses only the official API base URL, receives a credential explicitly from local configuration when the O3 runner supplies one, and never logs credentials itself.

For the fixed portability chat contract, `temperature` and `max output tokens` are supported and mapped directly. `timeout` is translated to the SDK client timeout, and the exactly-one-attempt rule is translated to SDK `maxRetries=0`. Anthropic exposes no seed in this contract, so seed is explicitly rejected, never silently ignored, and never synthesized. No common contract option is silently ignored. Response metadata may retain the returned effective model, usage, and only a format-validated opaque response ID; headers, base URLs, and credential data are excluded.

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

## Opt-in Local AI Judge Runner and Evidence

There is no supported judge environment variable or judge endpoint.
`OLLAMA_MODEL` remains the application chat/vision default and never selects
the evaluator model. The only live fact-check entry point is the explicitly
invoked `localEvaluation` Gradle task.

The implemented recording boundary requires a caller to provide an explicit
judge model, temperature, seed, token limit, timeout, and exactly-one-attempt
policy. Its dedicated Ollama factory also requires an explicit loopback base
URL, applies connect/read timeout, keeps pull strategy `never`, and disables
Spring AI retries. It does not read `OLLAMA_MODEL` or create a Spring bean, so
application startup and default tests cannot silently invoke it.

The offline evidence layer now locks the twelve-row protocol, BLAKE3
document/claim identities, prompt/catalog/human-review digests, full judge
identity, generation settings, raw outcomes, response metadata, available
usage, latency, attempts, and classified diagnostics. A saved run directory is
a direct child of ignored `build/evaluation-matrix/` and contains exactly
`local-evaluation-results.json`, shared v1 `manifest.json`, and deterministic
`SUMMARY.md`.

Run the locked matrix only against an already-running loopback Ollama service
and replace the example tag/output name deliberately:

```bash
./gradlew :setaccio-lab:localEvaluation \
  --ollama-base-url=http://localhost:11434 \
  --judge-model=YOUR_INSTALLED_TAG \
  --max-tokens=64 \
  --timeout=PT30S \
  --output-dir=build/evaluation-matrix/YYYY-MM-DD-local
```

| Option | Required | Contract |
| --- | --- | --- |
| `--ollama-base-url` | Yes | Explicit loopback HTTP(S) URL. It is validated but never saved. |
| `--judge-model` | Yes | One already-installed Ollama tag. No environment or application default is used. |
| `--max-tokens` | Yes | Positive integer from `1` through `32768`. |
| `--timeout` | Yes | Positive ISO-8601 duration, such as `PT30S`, no greater than `PT10M`. |
| `--output-dir` | Yes | One nonexistent dated child of `build/evaluation-matrix/`. |

Before creating the output directory, preflight validates the loopback
boundary, locked prompt/catalog/review digests and confirmation, option bounds,
fresh path, installed normalized model name, and full immutable model digest.
The task never pulls a model and executes exactly twelve rows sequentially,
with temperature `0.0`, seeds `42`/`43`, and exactly one attempt per row. It is
not attached to `test`, `check`, `build`, application startup, or CI. A dirty
worktree is preserved in the manifest and labeled `diagnostic/non-final` in
the deterministic summary.

Verify saved evidence without starting Spring or contacting Ollama:

```bash
./gradlew :setaccio-lab:localEvaluationVerify \
  --run-dir=build/evaluation-matrix/YYYY-MM-DD-local
```

Regenerate only `SUMMARY.md` after the immutable raw result and manifest pass
offline inspection:

```bash
./gradlew :setaccio-lab:localEvaluationReanalyze \
  --run-dir=build/evaluation-matrix/YYYY-MM-DD-local
```

The verify/reanalyze tasks have no model or endpoint options and remain fully
offline. One controlled Slice A5 run completed from clean commit `5d41362`
with explicit `gemma4:e2b`, its full installed digest, `64` output tokens,
`PT2M`, and twelve one-attempt rows. Its ignored evidence passed verification
and byte-identical reanalysis. It is not a reusable default or an invitation to
rerun the same protocol. Slice A6 completed the bounded offline interpretation
without changing the evidence. `RelevancyEvaluator` remains later work, and
container provisioning is deferred for this fact-check cycle. See
[the local AI-judged evaluation plan](LOCAL-AI-EVALUATION-PLAN.md) for the
command boundary, results, and closeout decisions.
The complete deferred-work index, including future provider and model-type
boundaries, is [DEFERRED-WORK.md](DEFERRED-WORK.md).

## Local Vision Benchmark

The vision benchmark is available only through the `local` profile and requires
explicit uploaded files and model names. Models must already be installed in
the configured Ollama instance; the application keeps model pulling disabled.

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

The existing multipart request remains valid:

```bash
curl -sS http://localhost:8082/api/lab/vision \
  -F files=@/path/to/image.jpg \
  -F models=gemma4:e2b
```

Optional generation settings may be supplied for reproducible calls:

```bash
curl -sS http://localhost:8082/api/lab/vision \
  -F files=@/path/to/image.jpg \
  -F models=gemma4:e2b \
  -F temperature=0.0 \
  -F seed=42 \
  -F maxTokens=1024
```

| Field | Required | Notes |
| --- | --- | --- |
| `files` | Yes | One or more JPEG, PNG, GIF, or WebP uploads. Request-scoped temporary copies are deleted after the response. |
| `models` | Yes | Comma-separated, already-installed Ollama model tags. |
| `temperature` | No | Value from `0.0` through `2.0`. When omitted, the configured model default remains in effect. |
| `seed` | No | Non-negative Ollama generation seed. When omitted, the configured model default remains in effect. |
| `maxTokens` | No | Optional Ollama `num_predict` limit from `1` through `32768`. |

Every row records the tracked `vision-image-analysis` prompt ID, version `1`,
its calculated SHA-256 digest, detected MIME type, BLAKE3 input hash, requested
generation settings, token usage when Spring AI exposes it, latency, output,
and classified failure information. Seven deterministic checks report whether
the required Markdown sections are present. `success` reports invocation
success independently from `structureComplete`; neither field is a semantic
image-quality judgment.

The endpoint continues to write `*-vision.json` under
`SETACCIO_LAB_OUTPUT_DIR`, which defaults to `build/lab-results/`. Vision output
uses the neutral host value `local`. No new environment variable is required.

### Local vision corpus

The controlled vision matrix uses the fixed ignored directory
`setaccio-lab/local/vision-corpus/`. It is separate from the interactive
endpoint's optional `SETACCIO_LAB_INPUT_DIR`; setting that environment variable
does not populate or select matrix cases.

Create a local catalog from the tracked public-safe template:

```bash
mkdir -p setaccio-lab/local/vision-corpus/images
cp setaccio-lab/src/main/resources/vision-corpus/cases.template.json \
  setaccio-lab/local/vision-corpus/cases.json
```

Replace the template placeholders locally and copy each selected input under
its stable case-ID-based filename. The local catalog and images are ignored as
one directory and must not be force-added. Do not record original filenames or
absolute paths.

The template starts every privacy-review field as false. Review sensitive
visible content before using a case. Before any exact image or derivative is
made public, strip and recheck EXIF/GPS metadata and obtain explicit user
approval for that file. Review state does not itself authorize tracking.

See [the local vision corpus contract](vision-corpus/README.md) for the exact
fields, six target case categories, and public-artifact boundary.

### Opt-in sequential vision matrix

The dedicated vision matrix is not attached to `test`, `check`, `build`, or
CI. Before running it:

1. Replace every placeholder in the ignored `cases.json`.
2. Set `sensitiveContentReviewed` to `true` only after reviewing that exact
   image.
3. Confirm MIME types and BLAKE3 digests match the exact local bytes.
4. Confirm every selected model tag is already installed with `ollama list`.
   The task records the full resolved Ollama digest and rejects duplicate tags
   that resolve to the same model bytes.
5. Decide one token policy for the entire run.

Run the offline suite first:

```bash
./gradlew :setaccio-lab:visionMatrixTest
```

Then invoke the live matrix explicitly, replacing the example model and output
name as needed:

```bash
./gradlew :setaccio-lab:visionMatrix \
  --corpus-dir=local/vision-corpus \
  --models=gemma4:e2b \
  --max-tokens=none \
  --output-dir=build/vision-matrix/2026-07-26-local \
  --prompt-version=2
```

| Option | Required | Contract |
| --- | --- | --- |
| `--corpus-dir` | Yes | Must resolve to the fixed ignored `local/vision-corpus` directory inside the `setaccio-lab` module. |
| `--models` | Yes | Comma-separated, unique, already-installed Ollama tags. No model is selected implicitly. |
| `--max-tokens` | Yes | `none` or one integer from `1` through `32768`, locked for every row. |
| `--output-dir` | Yes | A new direct child of `build/vision-matrix/` whose name contains a `YYYY-MM-DD` date. Existing directories are never reused. |
| `--prompt-version` | Yes | A supported tracked prompt version, currently `1` or `2`; it is recorded in every row and the evidence manifest. |
| `--case-ids` | No | Comma-separated, unique approved case IDs for a controlled subset, preserved in the supplied order. Omit it to run the full approved corpus. |

The protocol is fixed at two repetitions, temperature `0.0`, effective seeds
`42` and `43`, model-major/case-major/repetition order, strictly sequential
execution, and `spring.ai.ollama.init.pull-model-strategy=never`. The runner
validates the catalog, relative case-ID image paths, MIME bytes, BLAKE3 hashes,
and sensitive-content review state before it starts Spring. It then queries
Ollama's installed-model list with pulling disabled, resolves each requested
tag to its normalized installed name and full digest, rejects aliases that
identify the same model bytes, and fails before creating the output directory
when any requested tag is missing or lacks complete identity metadata.

Each successful run directory contains:

- `vision-matrix-results.json`, with safe case IDs and input identities but no
  local paths, original filenames, reference observations, expected concepts,
  unsupported-detail notes, or raw provider exception messages;
- `manifest.json`, using the shared evidence lifecycle and SHA-256 artifact
  integrity;
- `SUMMARY.md`, generated deterministically from the raw result.

Verify a saved run without Spring, the private corpus, or Ollama:

```bash
./gradlew :setaccio-lab:visionMatrixVerify \
  --run-dir=build/vision-matrix/2026-07-25-local
```

Regenerate only `SUMMARY.md` from verified immutable raw evidence:

```bash
./gradlew :setaccio-lab:visionMatrixReanalyze \
  --run-dir=build/vision-matrix/2026-07-25-local
```

Compare two already-verified saved runs without Spring, corpus access, Ollama,
or a remote provider:

```bash
./gradlew :setaccio-lab:visionMatrixCompare \
  --baseline-run-dir=build/vision-matrix/2026-07-25-controlled-four-case \
  --candidate-run-dir=build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case
```

The comparison verifies both inputs before rendering deterministic Markdown to
standard output. It requires the same ordered full model digests, case IDs and
BLAKE3 identities, repetitions/seeds, temperature, token policy, row order,
execution engine, and Spring Boot and Spring AI versions. Prompt identity and
code baseline may differ. The report covers invocation, structural, repetition,
token, latency, and infrastructure deltas only; semantic judgments remain human
review.

The ignored saved-run directories for the documented Prompt v1/v2 pair became
unavailable before actual-human comparative review. On 2026-08-02, the project
owner closed that prerequisite through an evidence-loss waiver without making
an `adopt` / `revise` / `reject` judgment. Prompt v1 remains the operational
interactive default, and Prompt v2 remains experimental and unadopted. Any
future decision based on replacement evidence requires a separately authorized
paired protocol, new run names, preserved evidence, and actual human review.

The historical preparation procedure remains below in case both exact ignored
runs are restored. Confirm that they are present before continuing:

```bash
ls -d \
  setaccio-lab/build/vision-matrix/2026-07-25-controlled-four-case \
  setaccio-lab/build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case
```

If either exact directory is missing, stop. Do not recreate or replace evidence
under these historical names. If both exact directories are restored, run this
command without changing the paths:

```bash
./gradlew :setaccio-lab:visionHumanReviewPrepare \
  --baseline-run-dir=build/vision-matrix/2026-07-25-controlled-four-case \
  --candidate-run-dir=build/vision-matrix/2026-07-26-prompt-v2-controlled-four-case \
  --corpus-dir=local/vision-corpus
```

Do not run the task without these options. The task never guesses from
timestamps or automatically selects evidence. See
`docs/VISION-HUMAN-REVIEW.md` for the canonical policy and rubric, or
`docs/VISION-HUMAN-REVIEW-OPERATOR.md` for the committed top-to-bottom operator
checklist.

The task verifies both runs, applies the same deterministic comparability gate,
and validates that the current private corpus cases still match the saved MIME
and BLAKE3 input identities. It then writes:

```text
setaccio-lab/build/vision-human-review/
└── 2026-07-25-controlled-four-case--vs--2026-07-26-prompt-v2-controlled-four-case/
    └── HUMAN-REVIEW.md
```

The worksheet contains private reference metadata, local image links, and raw
model responses grouped by model and case. Successful repetitions that match
exactly are shown once; differing or failed repetitions are shown separately.
The task does not start Spring, contact Ollama, select evidence automatically,
score semantics, or make the prompt decision. It refuses to overwrite an
existing worksheet so partially completed human notes remain protected.

Offline verification rejects missing, tampered, empty, unexpected, unsafe, or
protocol-drifted artifacts. Reanalysis refuses to change the summary when raw
evidence or manifest settings fail validation.

The complete run directory and generated human-review worksheet remain ignored
and private by default. Raw model outputs may describe sensitive visible
content even though corpus metadata and paths are omitted. Do not publish raw
results without a separate content review; public closeout for private cases
should use only safe case IDs and reviewed aggregate findings.

The summary reports invocation success, structural completion, repetition
readiness and exact-output diagnostics, token availability, median and observed
range for successful latencies, and infrastructure failures in separate
sections. Its expected-observation and unsupported-detail fields remain
`not performed` because the deterministic analyzer never invents semantic
labels. Slice 7 human review is recorded separately in the dated public log
and aggregate documentation; it does not rewrite raw evidence or copy private
reference metadata into the saved run.

## Local Chat Benchmark

The Phase 2 start gate closed on 2026-08-04 with chat selected as the reuse and
later portability surface. Slices S1 through S3 closed on 2026-08-05 after one
controlled local `gemma4:e2b` matrix from clean commit `51025cf` verified and
reanalyzed offline. The locked six rows used `128` output tokens and `PT2M`;
all completed with usage metadata and empty responses. The interactive endpoint
remains unchanged unless later parity tests justify migration. Phase 2 added no
Anthropic credential or remote call.

The simple chat benchmark path is manually runnable only through the `local` profile. It does not add a default test, CI, or startup path that calls Ollama.

Start the app explicitly:

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

Run the local chat benchmark against one or more already-pulled Ollama models:

```bash
curl -sS http://localhost:8082/api/lab/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "models": "gemma4:e2b",
    "advisorMode": "standard",
    "useDefaultPrompts": true
  }'
```

The chat benchmark invokes the model names supplied in the request `models` field. In the example above, Ollama receives requests for `gemma4:e2b`. To compare multiple local models, pass a comma-separated list such as `"gemma4:e2b,llama3.2:3b"`. The app-level `OLLAMA_MODEL` setting defaults to `gemma4:e2b`, but this endpoint still requires an explicit `models` value so benchmark runs cannot silently switch models.

The endpoint writes `*-chat.json` result files under `SETACCIO_LAB_OUTPUT_DIR`, which defaults to `build/lab-results/`.

Optional request fields:

| Field | Required | Notes |
| --- | --- | --- |
| `models` | Yes | Comma-separated Ollama model names used for this run. Models must be pulled manually before the request. |
| `advisorMode` | No | Defaults to `standard`. The chat benchmark supports only `standard`; Tool Search comparison is available only on `/api/lab/tools`. |
| `useDefaultPrompts` | No | Defaults to `true`. Set to `false` only when supplying explicit `prompts`. |
| `prompts` | No | List of `{ "id": "...", "text": "..." }` prompt objects. Required when `useDefaultPrompts` is `false`. |

No new environment variables are required for this path. It reuses `OLLAMA_BASE_URL` / `OLLAMA_API_BASE` and `SETACCIO_LAB_OUTPUT_DIR`.

### Dedicated controlled chat matrix

The dedicated matrix is not an HTTP endpoint and does not start Spring. It
requires five explicit task options, resolves the requested already-installed
model to its full Ollama digest before output allocation, and then runs exactly
three locked prompts twice in sequential order with temperature `0.0`, seeds
`42`/`43`, one attempt, and pull strategy `never`.

Running it contacts local Ollama and requires separate explicit authorization:

```bash
./gradlew :setaccio-lab:chatMatrix \
  --ollama-base-url=http://127.0.0.1:11434 \
  --model=<already-installed-model-tag> \
  --max-tokens=<positive-limit-1-through-32768> \
  --timeout=<positive-ISO-8601-duration-up-to-PT10M> \
  --output-dir=build/chat-matrix/2026-08-04-<run-id>
```

The output directory must be new, dated, and directly under ignored
`build/chat-matrix/`. The task never pulls a model and is not attached to
`test`, `check`, `build`, application startup, or CI. A failed invocation is
retained as its scheduled row without retry or replacement.

Verify or deterministically regenerate a saved summary without starting Spring
or contacting Ollama:

```bash
./gradlew :setaccio-lab:chatMatrixVerify \
  --run-dir=build/chat-matrix/<saved-run>

./gradlew :setaccio-lab:chatMatrixReanalyze \
  --run-dir=build/chat-matrix/<saved-run>
```

`SUMMARY.md` reports only protocol integrity, invocation completion, available
usage, failure categories, and successful latency range. It does not judge
semantic output quality or rank the single model.

### Bounded Anthropic portability matrix

`anthropicChatMatrix` is the only remote-provider task in this slice. It is
outside `test`, `check`, `build`, application startup, and CI. It requires a
separate explicit authorization immediately before execution, a current
official-price worst-case calculation, and a local `ANTHROPIC_API_KEY`; neither
the key nor raw responses are printed. Do not put a key in a Gradle option or
in a tracked/local command transcript.

The task permits one fixed protocol only: model
`claude-haiku-4-5-20251001`, six sequential calls over the tracked three-prompt
catalog, two unseeded repetitions, temperature `0.0`, `128` maximum output
tokens, `PT2M`, and one attempt. Temperature and output tokens are direct
options; timeout and one attempt translate to SDK timeout and `maxRetries=0`;
seed is rejected and never simulated. The verified matching Ollama run is used
only for the offline, raw-output-free portability report.

With the credential already present only in local environment/config, invoke
the task with an explicit budget ceiling and fresh dated output directory:

```bash
./gradlew :setaccio-lab:anthropicChatMatrix \
  --max-tokens=128 \
  --timeout=PT2M \
  --max-cost-usd=<explicit-authorized-usd-not-over-3.00> \
  --output-dir=build/anthropic-chat-matrix/<new-dated-run> \
  --ollama-run-dir=build/chat-matrix/<verified-matching-run>
```

The runner refuses an output directory that already exists and writes its raw
provider result, raw-output-free portability snapshot, manifest, and summary
only under the ignored `build/anthropic-chat-matrix/` root. These operations
remain provider-free and do not read a credential:

```bash
./gradlew :setaccio-lab:anthropicChatMatrixVerify \
  --run-dir=build/anthropic-chat-matrix/<saved-run>

./gradlew :setaccio-lab:anthropicChatMatrixReanalyze \
  --run-dir=build/anthropic-chat-matrix/<saved-run>
```

The one authorized Phase 3 run completed on 2026-08-05 from clean commit
`3810a19` with the fixed six-row protocol. All six calls completed with
non-empty outputs and complete usage metadata. The official-price worst-case
estimate was `$0.005376`; usage-derived cost was `$0.001870` under the task's
`$3` ceiling and the owner's `$5` authorization. The ignored saved evidence
verified and reanalyzed offline. This closes the bounded architecture proof;
it does not authorize another call, quality/performance comparison, additional
provider, or migration of `POST /api/lab/chat`.

## Local Tool-Calling Benchmark

The standard Spring AI tool-calling benchmark path is manually runnable only through the `local` profile. It does not add a default test, CI, or startup path that calls Ollama.

Start the app explicitly:

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

Run the local tool benchmark against one or more already-pulled Ollama models:

```bash
curl -sS http://localhost:8082/api/lab/tools \
  -H 'Content-Type: application/json' \
  -d '{
    "models": "gemma4:e2b",
    "advisorMode": "standard",
    "useDefaultPrompts": true
  }'
```

The endpoint writes `*-tool-calling.json` result files under `SETACCIO_LAB_OUTPUT_DIR`, which defaults to `build/lab-results/`.

Optional request fields:

| Field | Required | Notes |
| --- | --- | --- |
| `models` | Yes | Comma-separated Ollama model names. Models must be pulled manually before the request. |
| `advisorMode` | No | Defaults to `standard`. Use `compare` to run standard and Tool Search modes together. `tool_search` is rejected because standalone Tool Search runs are intentionally not comparable. |
| `useDefaultPrompts` | No | Defaults to `true`. Set to `false` only when supplying explicit `prompts`. |
| `prompts` | No | List of prompt objects. Each may include an `expectation` with required/forbidden executed tools, required output terms, and required tool-response terms. Required when `useDefaultPrompts` is `false`. |
| `requestedTools` | No | List of deterministic tool names to expose. Defaults to all arithmetic, fixed-time, catalog, and controlled-failure fixture tools. |
| `repetitions` | No | Defaults to `1` for standard runs and `2` for comparisons; allowed range is 1-20. Comparison repetitions are paired. |
| `temperature` | No | Explicit Ollama temperature recorded with the result. Defaults to `0.0`; allowed range is 0.0-2.0. |
| `baseSeed` | No | Defaults to `42`. Repetition `n` uses `baseSeed + n - 1`, and each effective seed is recorded on its row. |
| `maxTokens` | No | Optional Ollama generation limit. When omitted, the configured model default remains in effect. |
| `comparisonOrder` | No | `alternate` by default. Also accepts `standard_first` and `tool_search_first`. |

The built-in cases declare their own expectations. If `requestedTools` omits a tool required by a selected case, the request is rejected as an invalid benchmark setup instead of recording that setup error as a model failure. Custom prompts without an `expectation` still record the run-completion and Tool Search completion checks that apply to their advisor mode.

For a Tool Search comparison, enable the feature explicitly before starting the local-profile app:

```bash
SETACCIO_LAB_TOOL_SEARCH_ENABLED=true \
  ./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

Then send an explicit comparison request. The default two repetitions counterbalance advisor order: repetition one runs standard then Tool Search, while repetition two runs Tool Search then standard. Every pair is sequential so the two advisors do not compete for the local model server at the same time.

```bash
curl -sS http://localhost:8082/api/lab/tools \
  -H 'Content-Type: application/json' \
  -d '{
    "models": "gemma4:e2b",
    "advisorMode": "compare",
    "repetitions": 2,
    "temperature": 0.0,
    "baseSeed": 42,
    "comparisonOrder": "alternate",
    "useDefaultPrompts": false,
    "prompts": [{
      "id": "catalog-lookup",
      "text": "Use the available tools to look up fixture-policy-faq and summarize it.",
      "expectation": {
        "requiredExecutedTools": ["lab_lookup_catalog_item"],
        "requiredOutputTerms": ["Policy FAQ"]
      }
    }],
    "requestedTools": ["lab_lookup_catalog_item", "lab_list_catalog_items"]
  }'
```

The endpoint writes one `*-tool-calling-comparison.json` file containing both result sets. Each row retains its raw trace and also reports normalized Tool Search observations, named assertions, and `contractPassed`. This is a per-case execution contract, not an aggregate model or advisor winner score.

No new credentials are required for this path. It reuses `OLLAMA_BASE_URL` / `OLLAMA_API_BASE`, `SETACCIO_LAB_OUTPUT_DIR`, and `SETACCIO_LAB_TOOL_FIXTURE_INSTANT`.

## Small-Model Tool-Compatibility Matrix

The tracked
[Small-Model Tool-Calling Compatibility Plan](SmallModelToolCallingCompatibilityPlan.md)
defines the dedicated Phase 1 matrix. Its T0.1 documentation packet and
provider-free T1.1-T1.8 implementation are complete. The task is opt-in and
remains outside the default lifecycle; saved outputs remain ignored.

The Phase 1 task contract is intentionally explicit and does not inherit
application defaults or environment model selection:

```bash
./gradlew :setaccio-lab:toolCompatibilityMatrix \
  --ollama-base-url=http://localhost:11434 \
  --model=hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0 \
  --max-tokens=512 \
  --timeout=PT2M \
  --output-dir=build/tool-compatibility/YYYY-MM-DD-lfm-baseline
```

That task must reject missing or extra options, non-loopback or structured
endpoints, a missing/incompletely identified model, a model that would need a
pull, and a reused or unsafe output path before allocation. Its protocol locks:

- standard `ToolCallingAdvisor` only, with no Tool Search;
- all eight ordered cases from `ToolBenchmarkCases.defaults()` and every tool
  from `ToolBenchmarkCases.toolNames()`;
- two repetitions with seeds `42` and `43`;
- temperature `0.0`, `512` output tokens on every provider turn, one `PT2M`
  deadline around the complete logical row attempt, and no retry or turn replay;
- ordered per-turn/per-call evidence plus the plan's exact
  `tool-case-oracle` semantic call/argument contract;
- exactly 16 sequential rows and pull strategy `never`;
- one new direct child under ignored `build/tool-compatibility/` containing
  `tool-compatibility-results.json`, `manifest.json`, and `SUMMARY.md`.

The planned provider-free and offline commands are:

```bash
./gradlew :setaccio-lab:toolCompatibilityTest
./gradlew :setaccio-lab:toolCompatibilityVerify \
  --run-dir=build/tool-compatibility/<saved-run>
./gradlew :setaccio-lab:toolCompatibilityReanalyze \
  --run-dir=build/tool-compatibility/<saved-run>
```

Phase 2 uses a separate locked paired-runner interface so the Phase 1 CLI does
not change. The completed 2026-08-21 run used the same clean commit, model
digest, and settings for both conditions and wrote these ignored directories:
`build/tool-compatibility/2026-08-21-lfm-prompt-untreated` and
`build/tool-compatibility/2026-08-21-lfm-prompted`.

```bash
./gradlew :setaccio-lab:toolCompatibilityPromptMatrix \
  --ollama-base-url=http://localhost:11434 \
  --model=hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0 \
  --max-tokens=512 \
  --timeout=PT2M \
  --baseline-output-dir=build/tool-compatibility/YYYY-MM-DD-lfm-baseline \
  --candidate-output-dir=build/tool-compatibility/YYYY-MM-DD-lfm-prompted
```

The task preflights both fresh direct-child output paths before
allocation and executes both 16-row conditions in one 32-attempt interleaved
process. It re-checks the original commit and clean-worktree state before
every row and before finalizing either manifest, aborting both runs as
incomplete on drift. It ran only after Phase 1 closeout, Phase 2 provider-free
implementation, and separate approval of the exact command. Each output was
verified with `toolCompatibilityVerify` and reanalyzed offline before the
comparison:

```bash
./gradlew :setaccio-lab:toolCompatibilityCompare \
  --baseline-run=build/tool-compatibility/YYYY-MM-DD-lfm-baseline \
  --candidate-run=build/tool-compatibility/YYYY-MM-DD-lfm-prompted
```

Phase 1 completed on 2026-08-20 from clean commit `62181fb` with the installed
LFM2.5 model digest
`2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed`. The
16-row run verified and reanalyzed offline. Every row reached one provider
turn classified as `PROVIDER_FAILURE`; no tool calls, final responses, usage,
output-limit state, or visible reasoning markers were observed. This is a
bounded compatibility observation, not a quality or reliability claim.

Both conditions reached the same first `PROVIDER_FAILURE` boundary on all 16
rows. This is a bounded provider-turn observation, not a prompt-effect,
quality, reliability, or ranking result. The T2.5 worksheet is ignored and
requires the owner's human decision before Phase 3 can select a prompt policy
or execute a cohort. Dependency-independent provider-free preflight work and
read-only installed-model inspection were separately authorized on 2026-08-23;
they do not lock the provisional cohort or authorize model invocation. Any
future cohort task must use explicit ordered peer tags and a separately
labelled reference, record one Ollama runtime version and full model digests,
fail before allocation on identity drift, and never read `OLLAMA_MODEL`, pull a
model, join a default lifecycle, or publish ignored raw output.

## Opt-In Tool Search Smoke Automation

The `toolSearchSmoke` Gradle task provides a narrow live diagnostic for the Tool Search wrapper produced by the installed Spring AI version. It starts a non-web Spring context, runs one deterministic paired standard-versus-Tool-Search repetition, and compares each raw Tool Search response with the normalized discovery trace. It recognizes the array, textual-singleton, and object `toolReferences` representations covered by the offline parser; any other shape is malformed. It is not part of `test`, `check`, `build`, or default CI.

The model argument is mandatory and must identify a model that is already installed in the configured local Ollama instance:

```bash
./gradlew toolSearchSmoke \
  --model=llama3.1:8b \
  --case-ids=arithmetic-add,fixed-zone-time,catalog-multi-step
```

Omit `--case-ids` to run all cases from `ToolBenchmarkCases.defaults()`. Semantic IDs are the stable interface. The task also accepts 1-based ordinals for convenient ad hoc subsets:

```bash
./gradlew toolSearchSmoke \
  --model=llama3.1:8b \
  --case-ids=1,3,5,7
```

Unknown, duplicate, blank, and out-of-range selectors are rejected before Spring starts or Ollama is contacted. The current default corpus contains eight cases, so valid ordinals are `1` through `8`.

The task always exposes the complete `ToolBenchmarkCases.toolNames()` fixture set and forces these effective properties:

- `spring.ai.ollama.init.pull-model-strategy=never`
- `spring.ai.chat.client.tool-search-advisor.enabled=true`
- `spring.ai.chat.client.tool-search-advisor.tool-index-type=regex`
- `spring.ai.model.chat=ollama`

It never downloads or pulls a model. A missing model or unreachable Ollama instance is reported as a hard invocation failure. Raw JSON output remains under the ignored `setaccio-lab/build/lab-results/tool-search-smoke/` directory.

The console summary distinguishes:

- no Tool Search call,
- search completed with zero matches,
- non-empty discovery,
- discovery mismatch between raw and normalized traces,
- required tool discovered but not executed,
- required tool executed but its output contract failed.

Only startup or invocation failures, malformed results, missing trace linkages, and discovery mismatches fail the task. The other categories describe model behavior and leave the task successful. Every summary prints this reminder:

> Model behavior categories are for diagnosis only ��� never block merges on them unless a specific hypothesis was stated.

The analyzer and selection logic have a separate offline test task that does not contact Ollama:

```bash
./gradlew :setaccio-lab:toolSearchSmokeTest
```

## Post-Fix Tool Search Matrix Baseline

`toolSearchMatrixBaseline` is a dedicated, explicitly live task for reproducing the July 12 diagnostic matrix after the Issue #20/#21 fixes. It is not attached to `test`, `check`, `build`, or default CI and never pulls models.

The protocol is locked in code and the task rejects internal drift:

- models: `gemma4:e2b`, `granite4.1:3b`, `qwen3.5:0.8b`,
- canonical cases: `arithmetic-add`, `catalog-lookup`, `catalog-multi-step`, `no-applicable-domain-tool`, `deterministic-tool-failure`,
- two repetitions with effective seeds `42` and `43`,
- temperature `0.0`, no explicit maximum-token limit,
- alternate paired sequential standard/Tool Search execution,
- regex Tool Search index and the complete `ToolBenchmarkCases.toolNames()` fixture set,
- exactly 60 result rows.

The runner selects prompts and expectations directly from `ToolBenchmarkCases.defaults()`. In particular, the deterministic failure contract comes from `FailureBenchmarkTools.FAILURE_MARKER` (`fixture-tool-failure`); no request JSON transcribes that expectation.

Before running, confirm that all three exact IDs already appear in `ollama list`. Then supply a new, dated directory under `build/tool-search-matrix/`:

```bash
./gradlew toolSearchMatrixBaseline \
  --output-dir=build/tool-search-matrix/2026-07-13-post-fix-baseline
```

The task refuses an existing directory so a prior baseline cannot be overwritten. It forces `spring.ai.ollama.init.pull-model-strategy=never` and writes:

```text
build/tool-search-matrix/YYYY-MM-DD-post-fix-baseline/
├── <timestamp>-tool-calling-comparison.json
├── manifest.json
└── SUMMARY.md
```

New runs use the shared v1 evidence manifest. It records run identity, Git
commit and dirty state, Spring Boot and Spring AI versions, execution engine,
Issue #20/#21 context, models, cases, canonical prompts/expectations, tool
names, run settings, execution/index metadata, Ollama base URL, no-pull
strategy, expectation fingerprint, and relative descriptors with sizes and
SHA-256 digests for both the raw JSON and `SUMMARY.md`.

Every Tool Search row is checked independently against its raw linked response. Non-empty raw discoveries must exactly match normalized tool names in order; empty raw responses must normalize empty. Missing, duplicate, malformed, orphaned, or mismatched traces invalidate the baseline.

Failed canonical contracts are assigned exactly one primary category, in this precedence order:

1. no search call,
2. zero discovery,
3. incomplete discovery,
4. discovered-not-executed,
5. execution failure,
6. output-contract failure.

A successful abstention with zero discovery is not a failure. The deterministic `fixture-tool-failure` response is expected fixture data rather than an automatic execution failure. Any failed row outside the six categories invalidates analysis instead of being placed in an `other` bucket.

`SUMMARY.md` compares the post-fix pass counts with both the originally recorded and corrected July 12 counts. It always highlights two confounders: the July 12 request used the wrong deterministic marker, and Issue #20 changes normalization of object-shaped Tool Search responses. Issue #21 is recorded as adjacent chat correctness work, not as a direct tool-scoring cause.

### Offline verification and reanalysis

Saved matrix evidence can be verified without starting Spring or contacting a
model provider:

```bash
./gradlew toolSearchMatrixVerify \
  --run-dir=build/tool-search-matrix/2026-07-13-post-fix-baseline
```

Verification checks the manifest contract, locked protocol metadata, raw
SHA-256, expected 60-row structure and trace integrity, deterministic summary,
and absence of missing, extra, empty, modified, path-escaping, or symbolic-link
artifacts.

To regenerate only `SUMMARY.md` from an intact saved raw result:

```bash
./gradlew toolSearchMatrixReanalyze \
  --run-dir=build/tool-search-matrix/2026-07-13-post-fix-baseline
```

Reanalysis verifies the immutable manifest and raw JSON before replacing the
summary atomically, then verifies the complete directory again. It refuses to
regenerate from missing, modified, malformed, or protocol-drifted raw evidence.
Both commands read the shared v1 manifest and the earlier unversioned Tool
Search manifest, which is treated as legacy v0. They are standalone opt-in
tasks and are not attached to `test`, `check`, `build`, or default CI.

## Local Fixture Evaluation Benchmark

The deterministic evaluation path is available only through the `local` profile, but it does not call Ollama, Anthropic, or another provider. It evaluates a small public fixture set through Spring AI's `Evaluator` contract and writes `*-evaluation.json` under `SETACCIO_LAB_OUTPUT_DIR`.

Start the app explicitly:

```bash
./gradlew :setaccio-lab:bootRun --args='--spring.profiles.active=local'
```

Run all default fixtures:

```bash
curl -sS http://localhost:8082/api/lab/evaluations
```

Select particular fixture cases:

```bash
curl -sS http://localhost:8082/api/lab/evaluations \
  -H 'Content-Type: application/json' \
  -d '{"fixtureIds": ["result-output-supported", "offline-test-partial"]}'
```

Each row records the user text, fixture context, response text, evaluator provider/model, deterministic pass/fail verdict, score, feedback, and evaluator metadata. The current evaluator is `fixture` / `term-containment-v1`; it verifies documented required terms and is not an AI quality judgment. No new environment variables or credentials are required.

The separate AI-judged contract remains offline-only in the default lifecycle.
It tracks prompt `local-fact-check` version `1`, a balanced six-fixture claim/document
catalog, and an actual-human confirmation record tied to the exact catalog
digest. Its request-scoped recording boundary captures the explicit judge
options, raw result, response metadata, token usage when available, latency,
attempt count, normalized verdict, expectation agreement, and classified
failures around Spring AI's unchanged `FactCheckingEvaluator`.

Its suite-specific evidence writer, standalone offline verify/reanalyze tasks,
and explicit host-Ollama runner are implemented. The runner has no judge
environment variable, validates a loopback endpoint and immutable installed
model identity before output allocation, forces no-pull sequential execution,
and remains outside the default lifecycle.

The completed A5 run produced twelve one-attempt rows with complete usage and
no infrastructure failure, but ten responses were empty and only two produced
valid matching `no` verdicts. A6 interpreted that immutable evidence without a
provider call or row replacement. All empty responses reached the explicit
`64`-token output limit, which registers only a later, separately authorized
output-budget compatibility hypothesis; it does not establish causation.
Testcontainers is deferred for this cycle because container provisioning would
not answer that verdict-yield question. No additional environment variable,
Docker setup, rerun, release, or tag is required by the A6 closeout.

## Tool Search Advisor

Spring AI's Tool Search Tool support is available on the `setaccio-lab` classpath through `spring-ai-starter-tool-search-advisor`, but it is disabled by default. Keep it off for normal local runs, default tests, and the current vision benchmark path.

The current Spring AI Tool Search Advisor mapping is:

| Spring AI property | Repo environment mapping |
| --- | --- |
| `spring.ai.chat.client.tool-search-advisor.enabled` | `${SETACCIO_LAB_TOOL_SEARCH_ENABLED:false}` |
| `spring.ai.chat.client.tool-search-advisor.tool-index-type` | `${SETACCIO_LAB_TOOL_SEARCH_INDEX_TYPE:regex}` |

Set `SETACCIO_LAB_TOOL_SEARCH_ENABLED=true` only for an explicit `advisorMode: "compare"` request. That request runs paired standard `ToolCallingAdvisor` and `ToolSearchToolCallingAdvisor` executions sequentially against the same models, prompts, deterministic settings, and tool selection. Direct `advisorMode: "tool_search"` requests are rejected so every Tool Search result has its standard baseline. The current public-safe cases cover arithmetic, fixed time, catalog lookup, multi-step use, no-match behavior, abstention, and deterministic callback failure. Default tests remain mocked and offline.

Supported Spring AI index types are:

| Index type | Intended use |
| --- | --- |
| `regex` | The only executable index in this slice. It needs no extra store and matches tool names and descriptions. |
| `lucene` | Planned keyword-oriented comparison option; rejected by the current endpoint. |
| `vector` | Planned semantic discovery option. It requires an explicit public-safe `VectorStore` fixture and is rejected by the current endpoint. |

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
| `SETACCIO_LAB_TESTCONTAINERS_ENABLED` | Reserved for a future explicit Testcontainers runner; no current task consumes it. |

## Provider Variables

Use provider-specific credentials only for explicit live tests. Exact requirements can vary by Spring AI starter and provider account type, so verify against the Spring AI reference when adding each integration.

| Provider | Expected variables | Notes |
| --- | --- | --- |
| Anthropic | `ANTHROPIC_API_KEY`, optional `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL`, `ANTHROPIC_MAX_TOKENS` | Existing config maps these into Spring AI Anthropic API key, base URL, model, and max-token properties. |
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
| Testcontainers-backed integrations | `SETACCIO_LAB_TESTCONTAINERS_ENABLED` (reserved; no current runner) |

Do not require Docker, Testcontainers, or live evaluator models for default
builds. The current Testcontainers decision is `defer`; see
[DEFERRED-WORK.md](DEFERRED-WORK.md) before proposing a container slice.

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
