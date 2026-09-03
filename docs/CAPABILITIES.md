# Current Capabilities

Detailed, slice-by-slice description of every benchmark surface, contract, and
evidence lifecycle in `setaccio-lab`. This is the long-form companion to the
capabilities summary in the [project README](../README.md).

The closeout language throughout is deliberate. Where a section says a result
does not establish quality, reliability, ranking, or selection, that boundary is
part of the record and should not be paraphrased into a stronger claim.


## Shared Evidence Lifecycle Foundation

`setaccio-lab` includes plain Java primitives for reproducible benchmark
artifacts. They allocate unique non-overwriting run directories, write and read
a versioned manifest envelope, capture Git and framework provenance, describe
artifacts with relative paths and SHA-256 integrity metadata, and verify saved
runs offline. Verification rejects missing, modified, empty, duplicate,
undeclared, path-escaping, or symbolic-link artifacts without starting Spring
or contacting a model provider.

Phase 2 Slice S1 added shared `EvidenceFiles` operations for non-overwriting
artifact writes, saved-run path/layout checks, artifact size/SHA-256 checks,
deterministic summary verification, and atomic offline summary replacement.
Vision matrix, Tool Search matrix, and local evaluation now consume those file
operations while retaining their existing raw-result schemas, analyzers,
summaries, and failure taxonomies.

Phase 2 Slice S2 added a small provider-neutral chat invocation contract for
provider/requested/effective model identity, prompts, common generation
settings, provider option support, and recorded invocation outcomes. Its first
adapter is Ollama-only: Ollama identity retains the full model digest, request
options remain explicit, and the shared model construction forces loopback,
pull strategy `never`, and one attempt. The existing interactive chat endpoint
has not migrated to this boundary.

Phase 2 Slice S3 has a provider-free implementation: the three existing
default chat prompts are locked in one tracked v1 catalog with catalog and
per-prompt SHA-256 identities, and a dedicated `chatMatrix` task executes the
fixed six-row protocol through the S2 boundary. Suite-specific raw JSON,
shared-v1 manifest, deterministic summary, and standalone `chatMatrixVerify`
and `chatMatrixReanalyze` tasks stay under ignored `local/evidence/chat-matrix/`. One
clean-baseline local run from commit `51025cf` used `gemma4:e2b`, its full
digest, `128` output tokens, `PT2M`, and the locked six-row schedule. The
evidence verified and reanalyzed offline; all six rows had complete usage but
empty responses. This completes the contract-reuse proof without a chat-quality
or model-ranking claim.

The lifecycle deliberately keeps suite result payloads separate and reserves
BLAKE3 for benchmark input identity. The locked Tool Search matrix and the
dedicated sequential vision matrix write v1 manifests around their
suite-specific raw JSON and deterministic Markdown summaries. The interactive
vision, chat, and evaluation writers have not adopted the shared manifest yet.

## Local Vision Benchmark

`POST /api/lab/vision` runs under the `local` profile. It:

- accepts uploaded images through multipart `files`,
- runs each image against one or more local Ollama models through Spring AI,
- uses the tracked `vision-image-analysis` prompt version 1 and records its
  SHA-256 identity on every row,
- accepts optional temperature, seed, and token-limit settings without changing
  the existing multipart contract when they are omitted,
- hashes inputs with the BLAKE3 utilities in `setaccio-core`,
- returns structured rows with detected MIME type, model settings, input hash,
  token usage when available, deterministic required-section checks, latency,
  output, and classified error details,
- writes raw JSON results to `build/lab-results/` by default, configurable with `SETACCIO_LAB_OUTPUT_DIR`.

The direct Spring AI vision call lives behind a reusable invocation component,
while the interactive endpoint retains concurrent file/model coordination.
Invocation success and required-section completion are recorded separately;
format compliance is not treated as proof that a model understood an image.
Vision results use the neutral host value `local` rather than exposing the
machine hostname.

## Local Vision Corpus Contract

The tracked
[`cases.template.json`](../setaccio-lab/src/main/resources/vision-corpus/cases.template.json)
defines a versioned, vision-specific metadata shape for the controlled local
corpus. It provides six stable, non-sensitive case IDs covering a single
subject, complex scene, text-heavy image, low-quality image, ambiguous image,
and file-organization image. Each local case records its relative case-ID-based
image name, detected MIME type, BLAKE3 digest, human reference observation,
expected concepts, unsupported details, deliberate limitations, and explicit
privacy-review state.

Personal images and filled case metadata belong only under the explicitly
ignored `setaccio-lab/local/vision-corpus/` directory. The repository contains
no selected source images or private observations, and an image or derivative
requires sensitive-content and EXIF/GPS review plus explicit user approval
before it may be tracked. See
[`docs/vision-corpus/README.md`](vision-corpus/README.md) for the fixed
layout and review procedure.

The opt-in `visionMatrix` task consumes this exact contract. It validates the
catalog and exact input bytes before starting Spring, then executes every
explicit model, case, and repetition strictly sequentially with temperature
`0.0`, effective seeds `42` and `43`, one predeclared token policy, one
explicit tracked prompt version, and Ollama's pull strategy forced to `never`.
For a controlled smoke or diagnostic subset, callers may supply explicit
approved `--case-ids`; omitting that option retains the full approved corpus.
It checks Ollama's installed-model list, resolves each requested tag to its
full immutable Ollama digest, rejects duplicate aliases for the same installed
model, and fails before creating the run directory when a requested tag is
missing or its identity is incomplete. The task writes the selected prompt and
resolved model identities into suite-specific raw JSON, the shared v1 evidence
manifest, and `SUMMARY.md` under a required new dated
`local/evidence/vision-matrix/` directory.

Saved runs can be checked with `visionMatrixVerify` or have only their
deterministic summary regenerated with `visionMatrixReanalyze`. Both paths are
offline: they do not read the private corpus, start Spring, or contact Ollama.
They select the saved supported prompt version from immutable raw evidence.
Two verified saved runs can also be compared with `visionMatrixCompare`. It
requires matching Spring Boot and Spring AI versions, model digests and order,
input identities, settings, row order, and execution engine; it permits only
prompt identity and code baseline to differ. The deterministic Markdown report
is written to standard output and does not assess image semantics or copy
private corpus metadata.
The offline `visionHumanReviewPrepare` task builds on that comparison gate and
the ignored local corpus to produce one private, non-overwriting Markdown
worksheet under `local/evidence/vision-human-review/`. It groups baseline and candidate
responses by model and case, includes both repetitions only when they differ,
and leaves all semantic judgments and the final prompt decision to the human
reviewer.
The analyzer keeps invocation, structural completion, repetition diagnostics,
token availability, successful-invocation latency, and infrastructure failures
separate from semantic review.

A clean-baseline controlled local matrix completed across three models, four
reviewed private cases, and two repetitions: 24 sequential rows at temperature
`0.0`, seeds `42`/`43`, and no explicit token limit. All 24 invocations
succeeded, all 24 outputs contained the required prompt sections, token
metadata was available throughout, and the ignored evidence verified offline.
Human review is now complete as a separately labeled assessment of expected
concepts, unsupported detail, repetition consistency, token metadata, and
latency. The review found reliable core-scene coverage but recurring
unsupported geographic, event, and time specificity, plus overconfident image
quality claims on the intentionally limited case. These observations are
diagnostic rather than an aggregate model ranking; see the
[2026-07-25 Slice 7 log](logs/2026-07-25.md#slice-7-human-analysis-and-public-closeout)
for the bounded findings and next hypothesis.

The later Prompt v1/v2 comparative human-review prerequisite was closed on
2026-08-02 through an explicit evidence-loss waiver after its ignored saved-run
directories became unavailable. No actual-human adopt/revise/reject judgment
is claimed, and earlier Prompt v2 semantic observations remain labeled
agent-assisted. Prompt v1 remains the operational interactive default; Prompt
v2 remains experimental and unadopted. Any future Prompt v2 decision requires
new paired controlled evidence and actual human review rather than recreating
artifacts under the original run names.

## Local Chat Benchmark

`POST /api/lab/chat` runs under the `local` profile. It:

- runs text prompts across explicit Ollama model lists without tools,
- uses the comma-separated `models` request field for each run; the documented `gemma4:e2b` example is only the repo default Ollama model and can be replaced with any already-pulled local model,
- accepts default public-safe prompts or caller-provided `{ "id": "...", "text": "..." }` prompts,
- captures provider/model metadata, prompt id/text, token usage when Spring AI exposes it, latency, output, and errors,
- writes structured `*-chat.json` results to the same output directory.

## Local Tool-Calling Benchmark

`POST /api/lab/tools` runs under the `local` profile. It:

- runs deterministic, public-safe tool prompts across explicit Ollama models,
- runs either the standard Spring AI `ToolCallingAdvisor` path or an explicit standard-versus-Tool Search comparison,
- exercises first-class fixture cases for arithmetic, deterministic time, catalog lookup, multi-step execution, no-match behavior, tool abstention, and deterministic callback failure,
- attaches explicit expectations for required and forbidden tools, output terms, and tool-response terms to each case,
- captures selected tool calls, executed tool responses, normalized Tool Search queries and discovered tools, named contract assertions, cumulative token usage, latency, and final output,
- applies and records deterministic Ollama temperature, seed, and optional token-limit settings,
- writes structured `*-tool-calling.json` results for standard runs and `*-tool-calling-comparison.json` results for comparison runs.

Tool Search comparison is disabled by default and currently supports the in-memory regex index only. A comparison request runs paired advisor executions sequentially, alternates which advisor runs first across repetitions by default, and retains both result sets without assigning an aggregate winner. Each row reports whether its explicit case contract passed, while preserving every named assertion and raw trace needed to interpret that verdict.

An explicitly opt-in `toolSearchSmoke` Gradle task validates the live Tool Search response wrapper and raw-to-normalized trace linkage against one already-installed Ollama model. It is not connected to `test`, `check`, or `build`, enforces Ollama's `never` pull strategy, and treats model behavior categories as diagnostic output rather than merge gates. See [docs/ENVIRONMENT.md](ENVIRONMENT.md#opt-in-tool-search-smoke-automation) for invocation and case-selection details.

The separate `toolSearchMatrixBaseline` task reproduces the locked July 12 three-model/five-case protocol from canonical Java cases and writes a raw trace, shared v1 evidence manifest, and Markdown comparison under a new dated `local/evidence/tool-search-matrix/` directory. It verifies every raw-to-normalized discovery linkage and classifies contract failures into six explicit diagnostic categories. Its report compares both the originally recorded and corrected July 12 counts, with the request-construction correction called out as a confounder.

Saved matrix directories can be checked with `toolSearchMatrixVerify` or have
only their deterministic `SUMMARY.md` regenerated with
`toolSearchMatrixReanalyze`. Both commands are offline, accept current v1
manifests and the earlier unversioned legacy-v0 manifest, and never start Spring
or contact Ollama.

## Reasoning and Empty-Content Diagnostic

Spring AI's `OllamaChatModel` maps Ollama's `message.thinking` field into the
assistant message's properties under the key `thinking`, and maps
`message.content` into the assistant text. A boundary that reads only
`getOutput().getText()` therefore sees an empty response when a model returns
reasoning and no visible content, and Spring AI documents that a
thinking-capable model auto-enables thinking when no policy is sent. The
inspection behind that statement is recorded in
[`docs/logs/2026-09-03-thinking-field-inspection.md`](logs/2026-09-03-thinking-field-inspection.md).

Both local chat boundaries now record the response as separate dimensions
through one shared `ChatResponseCapture`: assistant content, any reasoning
field, whether reasoning was present, absent, or unavailable, the generation
finish reason, the evaluated output-token count, the explicitly requested
reasoning policy, and how the adapter handled that policy. Content and
reasoning are never concatenated, substituted, or merged.

Reasoning is an explicit recorded option rather than an inherited default. The
provider-neutral `ChatReasoningPolicy` has `ENABLED`, `DISABLED`, and
`PROVIDER_DEFAULT`; `OllamaReasoningOptions` maps it onto Spring AI's
`ThinkOption`, which stays inside the Ollama adapter. `PROVIDER_DEFAULT` sends
nothing and is not the same as `DISABLED`.

The opt-in `thinkingDiagnostic` task is a new diagnostic protocol with its own
suite, schema, and manifest settings. It reuses the tracked public-safe
fact-check fixture catalog, its confirmed ordering, and the tracked
`local-fact-check` prompt, but it is not a rerun, repair, replacement, or
reanalysis of the Phase 4 fact-check evidence and it never writes into that
suite. It locks five arms in a fixed order: a subject model with reasoning
explicitly enabled and explicitly disabled at 64 output tokens, the same pair
at 256, and a non-thinking control model with reasoning explicitly disabled at
64. Each paired subject arm holds prompt, fixture order, seed, temperature,
timeout, and every non-reasoning setting constant, so the only difference
inside a pair is the reasoning policy. Every one of its 30 rows is one logical
attempt at temperature `0.0`, seed `42`, `PT2M`, and pull strategy `never`,
and every row is retained.

`thinkingDiagnosticVerify` and `thinkingDiagnosticReanalyze` inspect saved
diagnostic evidence offline without starting Spring or contacting a provider.
Recorded content and reasoning stay in the ignored raw artifact; the
deterministic summary reports per-arm aggregates only.

One diagnostic suite completed on 2026-09-03 under Ollama `0.33.3`, retaining
all 30 rows. With reasoning explicitly enabled at `64` output tokens, the
subject artifact returned empty content with a populated reasoning field, the
full budget in evaluated tokens, and finish reason `length` in five of six rows;
the paired explicitly disabled arm produced visible content in two tokens in all
six. At `256` tokens reasoning fit within budget and every row produced content.
The evidence verified and reanalyzed offline. This explains a plausible cause of
the Phase 4 output-budget association without replacing or correcting it, and it
does not test the unset-policy behavior the retained runs actually used. The
bounded closeout is in
[`docs/logs/2026-09-03-thinking-diagnostic-run.md`](logs/2026-09-03-thinking-diagnostic-run.md).

The Phase 2 chat matrix, Phase 5 answer matrix, and Phase 4 fact-check suite
keep their existing protocol versions and row schemas. They continue to send no
reasoning policy, so their retained evidence still verifies and reanalyzes
unchanged, and their inherited-default behavior is recorded as a named
limitation rather than silently changed.

## Local Fixture Evaluation Benchmark

`POST /api/lab/evaluations` runs under the `local` profile. It:

- evaluates public deterministic fixtures through Spring AI's `Evaluator` contract without calling a model or provider,
- records user input, optional context, response text, evaluator provider/model, pass/fail, score, feedback, and evaluator metadata,
- accepts an optional `fixtureIds` list to select the public fixture cases,
- writes structured `*-evaluation.json` results to the same output directory.

This establishes the result-row contract for later AI-judged evaluation. It
does not claim to measure model quality; live evaluator models remain a
separate opt-in phase.

The AI-judged fact-checking work remains separate from that endpoint. Slice A1
contains prompt `local-fact-check` version `1` with exact
`{document}` and `{claim}` placeholders, a versioned six-fixture catalog made
from three repository-authored document pairs, and an actual-human confirmation
record tied to the exact catalog SHA-256. The fixtures are balanced at three
supported and three unsupported claims. Default tests lock all three artifact
digests and reject a pending, incomplete, or catalog-mismatched review record.

Slice A2 adds a plain Java, request-scoped recording boundary around Spring
AI's unchanged `FactCheckingEvaluator`. A caller must supply an explicit judge
model, temperature, seed, token limit, timeout, and exactly-one-attempt policy.
The dedicated Ollama factory accepts only an explicit loopback URL, forces pull
strategy `never`, disables Spring AI retries, and never inherits
`OLLAMA_MODEL`. Each boundary result keeps provider invocation success, Spring's
supported-claim boolean, exact `yes` / `no` verdict, expected-label agreement,
raw output, response metadata, token usage when available, latency, attempt
count, and failure/diagnostic category separate. Empty or malformed output is
not coerced to `no`.

Slice A3 adds the offline evidence lifecycle before any live runner. It locks
the exact twelve-row order, stores BLAKE3 document/claim identities instead of
duplicating fixture text, binds the prompt/catalog/human-review and immutable
judge identities, and writes suite-specific raw JSON, a shared v1 manifest,
and deterministic `SUMMARY.md` under ignored `local/evidence/evaluation-matrix/`
directories. The summary keeps supported and unsupported agreement,
repetition consistency, verdict tendency, formatting outcomes, token
availability, latency, attempts, and infrastructure failures separate and
does not claim an order effect.

Saved evidence can be checked or have only its deterministic summary
regenerated with the standalone offline tasks:

```bash
./gradlew :setaccio-lab:localEvaluationVerify \
  --run-dir=local/evidence/evaluation-matrix/YYYY-MM-DD-local
./gradlew :setaccio-lab:localEvaluationReanalyze \
  --run-dir=local/evidence/evaluation-matrix/YYYY-MM-DD-local
```

Slice A4 adds one explicitly invoked host-Ollama runner. It requires a
loopback URL, an already-installed judge tag, a positive token limit, an
ISO-8601 timeout, and a new dated output directory. Preflight validates the
tracked human-confirmed contract and resolves the tag to a full immutable
Ollama digest before allocating output. The runner then executes the locked
twelve rows sequentially with one attempt per row and pull strategy `never`:

```bash
./gradlew :setaccio-lab:localEvaluation \
  --ollama-base-url=http://localhost:11434 \
  --judge-model=YOUR_INSTALLED_TAG \
  --max-tokens=64 \
  --timeout=PT30S \
  --output-dir=local/evidence/evaluation-matrix/YYYY-MM-DD-local
```

The task is not connected to `test`, `check`, `build`, application startup, or
CI. It has no judge environment default, never records the endpoint, never
pulls a model, and does not contact a remote provider.

Slice A5 completed one clean-baseline run from commit `5d41362` with explicit
judge `gemma4:e2b`, its full installed digest, `64` output tokens, timeout
`PT2M`, and the locked twelve-row schedule. All 12 provider invocations and
attempt records completed with usage metadata and no infrastructure failure.
The bounded result contained ten empty responses plus two valid matching `no`
verdicts; there were no valid mismatches. The ignored evidence verified and
reanalyzed byte-for-byte offline without a retry, replacement row, model pull,
or raw-output publication.

Slice A6 closed the cycle by interpreting only that immutable evidence. No
supported row was evaluable; two of six planned unsupported rows were
evaluable and both agreed, while the other ten rows across both labels were
empty. One fixture had two consistent valid verdicts and five repetition
comparisons were incomplete, so the run does not establish reliability,
general factuality, or a verdict-label tendency. All empty responses ended at
the explicit `64`-token output limit, while both valid responses used two
completion tokens; that association registers a later, separately designed
output-budget compatibility hypothesis without claiming causation. The
Testcontainers outcome is `defer`, because provisioning would not answer the
observed verdict-yield question. No A5 row was rerun or replaced. See
[the local AI-judged evaluation plan](LOCAL-AI-EVALUATION-PLAN.md).
On 2026-08-05, Phase 2 closed after the controlled local chat matrix verified
and reanalyzed offline; the preserved Phase 1 evidence also still verifies.
Phase 3 then closed with one authorized, six-call Anthropic architecture
portability proof using a pinned hosted model ID, explicit unsupported-seed
semantics, bounded cost, and offline-verified ignored evidence. It makes no
quality, performance, reliability, or model-ranking claim. The existing
interactive endpoint remains unchanged. Remaining deferred work, start gates,
and non-authorization boundaries are indexed in
[the deferred-work guide](DEFERRED-WORK.md).

All benchmarks are local-first and offline-safe by default:

- default builds and tests require no credentials or running Ollama instance,
- live model runs require the `local` profile or explicit configuration,
- interactive endpoint output stays under ignored `build/lab-results/`, and
  formal run evidence stays under the ignored, durable
  `setaccio-lab/local/evidence/<suite>/` root that Gradle `clean` does not
  remove.

Result filenames include nanosecond timestamps and short run identifiers so repeated runs cannot overwrite one another when they start at the same instant.

