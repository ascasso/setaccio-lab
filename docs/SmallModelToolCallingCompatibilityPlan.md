# Small-Model Tool-Calling Compatibility Plan

## Status

Proposed next work after completion of:

- the local fact-checking cycle;
- the shared evidence lifecycle extraction;
- the provider-neutral chat invocation boundary;
- the controlled Ollama chat matrix;
- the bounded Anthropic architecture-portability proof;
- the existing standard-versus-regex Tool Search baseline.

This plan authorizes no implementation, live Ollama call, model pull, Docker use, release, tag, push, or remote-provider expenditure by itself. Each live execution remains a separate explicit action after its implementation and provider-free preflight are complete.

## Purpose

Evaluate whether small, fast, locally hosted language models can reliably perform constrained tool-calling tasks even when their general factual knowledge and open-ended conversational quality are limited.

The motivating observation is that a small model may perform poorly at unsupported factual recall yet still be useful as a tool router, argument extractor, or deterministic agent component when authoritative information is supplied by tools.

The initial subject is:

```text
hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0
```

The plan begins with one untreated model and the existing tool cases. It does not initially add new tools, providers, indexes, fixtures, or model types.

## Primary research questions

### RQ1 — Baseline compatibility

Can the untreated LFM2.5 model complete the existing Setaccio tool-calling protocol through Spring AI and Ollama?

### RQ2 — Failure location

When a case fails, where does it fail?

Possible boundaries include:

- tool selection;
- tool-call serialization;
- argument generation;
- callback execution;
- multi-step continuation;
- final-answer generation;
- output-budget exhaustion;
- reasoning leakage;
- provider or framework incompatibility.

### RQ3 — Prompt intervention

Does a narrowly defined tool-discipline system prompt improve contract completion without changing model bytes, fixtures, tools, generation settings, execution order, or framework versions?

### RQ4 — Small-model viability

After the one-model protocol is proven, how do several small local models differ across tool compatibility, abstention, argument correctness, final-answer yield, token use, and latency?

### RQ5 — Output-budget compatibility

Does increasing the explicit output-token limit improve valid result yield for the existing fact-checking judge protocol while all other experimental variables remain unchanged?

### RQ6 — Retrieval readiness

What is the smallest real retrieval path that can preserve retrieved evidence and support a future relevancy evaluation without misrepresenting ordinary fixture context as RAG?

---

# Non-goals

This work will not:

- produce a general intelligence score;
- claim that one model is universally better;
- rank models using one aggregate number;
- add new production Setaccio behavior;
- copy private Setaccio application code or roadmap material;
- add Docker or Testcontainers dependencies to `setaccio-lab`;
- add remote providers;
- add MCP in the first phases;
- add a new Tool Search index;
- change the existing interactive endpoint contract;
- automatically pull missing Ollama models;
- place live runs in `test`, `check`, `build`, application startup, or CI;
- treat structural compliance as semantic correctness;
- treat two repetitions as statistical reliability;
- replace or selectively retry failed rows;
- publish ignored raw model outputs unless separately reviewed and authorized;
- hide the experimental system prompt inside an opaque mutable Ollama tag.

---

# Shared principles

## Explicit experimental variables

Every controlled run must identify the variable intentionally changed from its baseline.

All other relevant variables must either:

- remain identical;
- be rejected before execution;
- or be recorded as a declared limitation.

## Immutable model identity

Every Ollama model must be resolved before output-directory allocation to:

- requested tag;
- normalized installed name;
- full immutable Ollama digest.

Missing models, incomplete digests, and duplicate aliases resolving to identical bytes must fail preflight.

No task may pull a model.

## One attempt

Every row receives exactly one model invocation attempt.

There are no:

- framework retries;
- SDK retries;
- replacement rows;
- selective reruns;
- fallback models.

A failed attempt remains part of the evidence.

## Sequential execution

Controlled matrices execute sequentially.

This avoids hidden concurrency effects and keeps:

- row ordering deterministic;
- tool state deterministic;
- latency interpretation bounded;
- failures attributable to one invocation path.

## Evidence lifecycle

Each controlled run writes a new ignored directory containing:

```text
raw-results.json
manifest.json
SUMMARY.md
```

Names may become suite-specific, but each run must contain exactly the declared artifacts.

Every run must support:

- offline verification;
- deterministic reanalysis;
- tamper detection;
- non-overwriting creation;
- relative artifact paths;
- SHA-256 artifact integrity;
- Git and framework provenance;
- no hostname, credentials, endpoint, absolute path, or private corpus data.

## Separate outcome dimensions

The analysis must not collapse distinct outcomes into one score.

At minimum, retain separately:

- provider invocation success;
- valid tool-call production;
- correct tool selection;
- forbidden-tool avoidance;
- argument validity;
- callback execution;
- callback result correctness;
- multi-step continuation;
- final-response presence;
- final-response contract assertions;
- token metadata availability;
- output-limit behavior;
- reasoning leakage;
- latency;
- infrastructure failure.

---

# Phase 0 — Plan and contract gate

## Objective

Define and lock the experiment before implementing a live runner.

## Slice T0.1 — Documentation and authorization boundary

Create:

```text
docs/SMALL-MODEL-TOOL-CALLING-PLAN.md
```

Update:

```text
docs/DEFERRED-WORK.md
docs/TEST-PLAN.md
docs/ENVIRONMENT.md
CHANGELOG.md
AGENTS.md
docs/logs/YYYY-MM-DD.md
```

### Required decisions

Lock the following before code implementation:

- initial model tag;
- baseline advisor mode;
- selected existing case IDs;
- repetition count;
- temperature;
- seeds;
- output-token limit;
- timeout;
- one-attempt policy;
- execution order;
- raw-result filename;
- manifest suite ID;
- summary rules;
- system-prompt handling;
- live-run output root.

### Recommended initial protocol

```text
Model:
hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0

Advisor:
standard ToolCallingAdvisor only

Cases:
existing public-safe cases, unchanged

Repetitions:
2

Temperature:
0.0

Seeds:
42 and 43

Maximum output tokens:
512

Timeout:
PT2M

Attempts:
1

Execution:
strictly sequential

Pull strategy:
never
```

The `512`-token starting budget is deliberately larger than the fact-checking experiment’s `64` tokens because this model visibly emits substantial reasoning text. It should still be treated as an explicit bounded policy, not as a claim that 512 is optimal.

### Case-selection recommendation

Use all existing canonical cases if their total call count remains modest:

- arithmetic;
- deterministic time;
- catalog lookup;
- multi-step execution;
- no-match behavior;
- tool abstention;
- deterministic callback failure.

If the current standard matrix contains cases added specifically for Tool Search discovery behavior, exclude only those that cannot be interpreted meaningfully in standard mode. Record the exact case IDs and order in a tracked catalog.

### Exit criteria

- The plan is reviewed.
- Experimental variables are explicit.
- No live execution has occurred.
- Existing deferred-work text is updated to distinguish this narrow compatibility study from a broad tool-calling expansion.
- Default lifecycle remains provider-free.

---

# Phase 1 — Untreated LFM baseline

## Objective

Determine whether the unmodified LFM2.5 model is technically and behaviorally compatible with the existing standard tool-calling path.

No custom system prompt is introduced in this phase.

## Slice T1.1 — Provider-free protocol model

Create a dedicated source set or package for the controlled matrix, following existing matrix conventions.

Suggested package:

```text
com.setaccio.lab.toolcompat
```

Suggested source sets:

```text
toolCompatibility
toolCompatibilityTest
```

Reuse existing shared evidence primitives and existing canonical tool cases where semantics match.

Do not alter the interactive `/api/lab/tools` endpoint.

### Core protocol types

Suggested records or value types:

```java
ToolCompatibilityProtocol
ToolCompatibilityCaseSelection
ToolCompatibilityRunSettings
ToolCompatibilityModelIdentity
ToolCompatibilityPromptIdentity
ToolCompatibilityRow
ToolCompatibilityResult
ToolCompatibilityFailure
ToolCompatibilityDiagnostic
```

Avoid creating generic abstractions unless at least two real consumers already demonstrate identical semantics.

## Slice T1.2 — Explicit system-prompt representation

Even though Phase 1 uses no custom system prompt, make system-prompt state explicit.

Suggested representation:

```java
record SystemPromptIdentity(
    String id,
    int version,
    String sha256,
    String text,
    boolean present
) {}
```

For the untreated baseline:

```text
id: none
version: 0
text: ""
present: false
sha256: SHA-256 of zero bytes or a clearly documented sentinel policy
```

Prefer a real digest policy over `null` so comparability logic remains deterministic.

Do not infer or extract training-time behavior from model weights.

Do not describe the absence of a Modelfile `SYSTEM` block as proof that the weights contain no persona or instruction tuning.

## Slice T1.3 — Invocation boundary

Reuse the existing Ollama construction rules:

- explicit loopback URL;
- pull strategy `never`;
- exact requested model;
- full installed digest resolution;
- temperature `0.0`;
- seed per repetition;
- explicit maximum output tokens;
- explicit timeout;
- retries disabled;
- exactly one attempt.

Use the existing Spring AI standard tool-calling path without Tool Search.

The runner must retain:

- raw assistant response;
- tool-call requests;
- tool-call arguments;
- callback outputs;
- final assistant response;
- response metadata;
- token usage when available;
- finish reason when available;
- latency;
- classified failure.

## Slice T1.4 — Canonical result row

Each row should retain enough evidence to distinguish tool routing from final-answer generation.

Suggested shape:

```java
record ToolCompatibilityRow(
    int sequence,
    String caseId,
    int repetition,
    Integer seed,

    String provider,
    String requestedModel,
    String effectiveModel,
    String modelDigest,

    String systemPromptId,
    int systemPromptVersion,
    String systemPromptSha256,

    double temperature,
    int maxOutputTokens,
    Duration timeout,
    int attemptCount,

    boolean providerInvocationSucceeded,
    boolean toolCallProduced,
    boolean toolCallValid,
    boolean requiredToolSelected,
    boolean forbiddenToolSelected,
    boolean callbackExecuted,
    boolean callbackSucceeded,
    boolean finalResponsePresent,
    boolean caseContractPassed,

    List<ToolCallEvidence> toolCalls,
    List<ToolResponseEvidence> toolResponses,
    List<NamedAssertionResult> assertions,

    String rawAssistantOutput,
    String finalAssistantOutput,

    boolean thinkTagDetected,
    boolean reasoningMarkerDetected,
    boolean outputLimitReached,

    TokenUsageEvidence usage,
    Duration latency,

    String failureCategory,
    String diagnosticCategory,
    String safeErrorMessage
) {}
```

Reuse existing tool trace and assertion types if their semantics already match.

Do not duplicate raw callback data into multiple fields unless needed for verification.

## Slice T1.5 — Reasoning-leakage diagnostics

Add deterministic diagnostics for visible reasoning markers.

Initial markers may include:

```text
<think>
</think>
Thinking...
...done thinking.
Here's a thinking process:
```

Classify, but do not automatically fail, a row based only on reasoning leakage unless the locked case contract explicitly forbids it.

Record separately:

- marker detected anywhere;
- marker detected before first tool call;
- marker detected after tool execution;
- reasoning text present in final user-facing output.

Do not claim these markers reveal the model’s actual private reasoning process. Name the category `visibleReasoningText` or `reasoningStyleOutput`, not `chainOfThought`.

## Slice T1.6 — Deterministic analysis

The summary should include counts for:

### Invocation

- planned rows;
- completed attempts;
- provider successes;
- provider failures;
- timeouts;
- unavailable models;
- empty provider responses.

### Tool selection

- required tool selected;
- required tool missing;
- forbidden tool selected;
- unnecessary tool use;
- valid abstentions.

### Tool execution

- valid tool call;
- malformed tool call;
- valid arguments;
- invalid arguments;
- callback success;
- callback failure;
- expected deterministic callback failure correctly retained.

### Completion

- final response present;
- final response empty;
- final contract pass;
- tool succeeded but final answer failed;
- output limit reached.

### Reasoning-style output

- `<think>` marker detected;
- other reasoning marker detected;
- reasoning marker before tool call;
- reasoning marker in final response.

### Usage and latency

- rows with complete usage;
- prompt tokens;
- completion tokens;
- total tokens;
- successful-row median latency;
- observed successful-row latency range.

With two repetitions, do not calculate percentiles.

## Slice T1.7 — Offline evidence lifecycle

Suggested task names:

```bash
./gradlew :setaccio-lab:toolCompatibilityTest

./gradlew :setaccio-lab:toolCompatibilityMatrix \
  --ollama-base-url=http://localhost:11434 \
  --model=hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0 \
  --max-tokens=512 \
  --timeout=PT2M \
  --output-dir=build/tool-compatibility/YYYY-MM-DD-lfm-baseline

./gradlew :setaccio-lab:toolCompatibilityVerify \
  --run-dir=build/tool-compatibility/YYYY-MM-DD-lfm-baseline

./gradlew :setaccio-lab:toolCompatibilityReanalyze \
  --run-dir=build/tool-compatibility/YYYY-MM-DD-lfm-baseline
```

The live task must require every option shown above.

Do not inherit:

- `OLLAMA_MODEL`;
- application chat defaults;
- endpoint defaults;
- an output directory;
- a token budget;
- a timeout.

Temperature and seeds may remain protocol constants if locked in code and recorded in evidence.

## Slice T1.8 — Provider-free tests

Tests must cover:

### Preflight

- missing model option;
- unknown option;
- duplicate option;
- missing installed model;
- incomplete digest;
- duplicate alias for same digest where disallowed;
- non-loopback endpoint;
- endpoint with userinfo;
- endpoint with path/query/fragment where disallowed;
- reused output directory;
- output path outside required root;
- symbolic-link output path;
- invalid token bounds;
- invalid timeout bounds.

### Execution

Using fake model and deterministic tools:

- exact row count;
- exact row order;
- seeds 42 and 43;
- one attempt per row;
- no replacement after failure;
- successful single-step tool;
- successful multi-step tool;
- abstention;
- no-match;
- invalid arguments;
- callback failure;
- tool success followed by empty final response;
- output-limit completion;
- visible reasoning marker;
- usage present;
- usage absent;
- timeout;
- provider failure.

### Evidence

- raw-result schema validation;
- manifest suite and run identity;
- prompt/system-prompt identity;
- full model digest;
- settings parity;
- artifact SHA-256;
- missing artifact rejection;
- extra artifact rejection;
- empty artifact rejection;
- path traversal rejection;
- symlink rejection;
- row-order drift rejection;
- attempt-count drift rejection;
- summary drift rejection;
- deterministic reanalysis.

## Phase 1 live-run gate

Before the live run:

- worktree state is captured;
- model is already installed;
- full digest resolves;
- no model pull is needed;
- provider-free tests pass;
- output directory does not exist;
- exact command is reviewed;
- no remote credential is involved.

## Phase 1 interpretation

The closeout must state only what the evidence supports.

Allowed examples:

- “The model produced valid tool calls in N of M rows.”
- “Tool execution succeeded, but final responses were empty in N rows.”
- “Visible `<think>` output appeared before the tool call in N rows.”
- “The model reached the explicit output limit in N rows.”

Not allowed:

- “LFM is a good agent.”
- “LFM is better than Gemma.”
- “Small models are sufficient for production.”
- “The model reasoned correctly.”
- “The model is reliable.”

## Phase 1 exit criteria

- One clean or explicitly diagnostic baseline run completes.
- Raw evidence verifies offline.
- Reanalysis reproduces `SUMMARY.md`.
- Failure locations are distinguishable.
- No prompt intervention has been introduced.
- A bounded interpretation is committed.
- Phase 2 is either authorized or explicitly deferred based on the evidence.

---

# Phase 2 — Controlled system-prompt intervention

## Objective

Test whether one explicit tool-discipline system prompt changes compatibility outcomes for the exact same model and protocol.

## Experimental hypothesis

An explicit system prompt emphasizing tool discipline and suppression of visible reasoning text may:

- increase required-tool selection;
- reduce unnecessary tool calls;
- reduce unsupported answer fabrication;
- reduce visible `<think>` output;
- improve final-response yield;
- reduce token use before the first tool call.

No direction is assumed in advance.

## Slice T2.1 — Tracked prompt catalog

Create a dedicated catalog containing exactly two prompt conditions:

### Condition A — Untreated baseline

```text
ID: tool-system-none
Version: 1
Text: empty
```

### Condition B — Tool discipline

```text
ID: tool-system-discipline
Version: 1
```

Exact text:

```text
You are a tool-using assistant.

Use a tool when the request requires external information or an action.
Do not invent tool results.
Do not call a tool when it is unnecessary.
Use only the tools available to you.
Think silently and do not output internal reasoning or <think> tags.
After a tool completes, answer using only its returned result.
```

Lock:

- catalog ID;
- catalog version;
- catalog SHA-256;
- per-prompt SHA-256;
- exact UTF-8 bytes;
- deterministic order.

Do not use an Ollama-derived model tag to encode the prompt.

## Slice T2.2 — Paired execution protocol

Recommended order:

```text
For each case:
  repetition 1:
    untreated
    prompted
  repetition 2:
    prompted
    untreated
```

This alternates condition order across repetitions.

Alternatively, execute whole conditions sequentially if existing suite infrastructure requires it, but record and acknowledge that order is not counterbalanced.

Keep identical:

- model digest;
- case catalog;
- advisor;
- tool definitions;
- temperature;
- seeds;
- output budget;
- timeout;
- attempt count;
- Spring Boot version;
- Spring AI version;
- execution engine.

## Slice T2.3 — Comparison gate

Add:

```bash
./gradlew :setaccio-lab:toolCompatibilityCompare \
  --baseline-run=build/tool-compatibility/YYYY-MM-DD-lfm-baseline \
  --candidate-run=build/tool-compatibility/YYYY-MM-DD-lfm-prompted
```

The comparison must reject mismatches in:

- model digest;
- model order;
- case IDs;
- case order;
- repetition count;
- seeds;
- temperature;
- output tokens;
- timeout;
- attempts;
- advisor mode;
- tool catalog identity;
- framework versions;
- execution engine.

Permit differences only in:

- system-prompt identity;
- Git commit;
- generated timestamp;
- output-directory identity.

## Slice T2.4 — Deterministic comparison report

Report paired changes by case and repetition:

- contract fail → pass;
- pass → fail;
- unchanged pass;
- unchanged fail;
- required tool newly selected;
- required tool newly missed;
- forbidden tool newly selected;
- final response newly present;
- final response newly empty;
- visible reasoning marker removed;
- visible reasoning marker introduced;
- output-limit state changed;
- completion-token delta;
- latency delta.

Do not declare an overall winner automatically.

## Slice T2.5 — Human interpretation

Human review should answer:

- Did the prompt improve behavior in the cases that matter?
- Did it merely suppress text while harming tool selection?
- Did it increase abstention incorrectly?
- Did it make final answers mechanically terse or incomplete?
- Did it introduce prompt-specific artifacts?
- Is the prompt worth adopting for later small-model studies?

Decision vocabulary:

```text
adopt
revise
reject
inconclusive
```

Bind the human decision to:

- baseline run identity;
- candidate run identity;
- prompt catalog digest;
- comparison report digest;
- review date.

## Phase 2 exit criteria

- Paired runs verify offline.
- Comparison preconditions pass.
- Deterministic comparison is reproducible.
- Human decision is recorded separately from deterministic analysis.
- No model ranking claim is made.

---

# Phase 3 — Small-local-model compatibility cohort

## Objective

Determine how several small local models behave under the same existing tool contract.

This phase begins only after Phases 1 and 2 establish a working protocol.

## Candidate cohort

Recommended initial cohort:

```text
hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0
granite4.1:3b
ministral-3:3b
gemma4:e2b
qwen3.5:0.8b
dolphin-phi:latest
```

Reference model:

```text
qwen3.6:latest
```

The reference model is not grouped as a size peer. It provides a higher-capability local comparison point.

Before locking the cohort, inspect each installed model’s:

- architecture;
- tool-calling support;
- chat template;
- default system prompt;
- reasoning-output behavior;
- immutable digest.

Models that cannot express Spring AI/Ollama tool calls should not be silently removed. They may remain as compatibility failures if the protocol question includes unsupported models.

## Slice T3.1 — Cohort preflight

Require explicit ordered model tags.

Resolve each to:

- normalized installed identity;
- full digest;
- size metadata if reliably available;
- template/tool capability metadata where available.

Reject:

- missing tags;
- duplicate aliases for identical digests;
- incomplete identities;
- cloud-only models;
- models requiring remote execution;
- models pulled automatically.

## Slice T3.2 — Prompt policy

Use the Phase 2 human decision:

- if `adopt`, use the adopted system prompt for the cohort;
- if `reject`, use untreated prompts;
- if `revise`, complete a new prompt experiment first;
- if `inconclusive`, prefer untreated operation and record the limitation.

Do not choose per-model prompts in the initial cohort. That would introduce a confounded optimization layer.

## Slice T3.3 — Locked model matrix

Recommended protocol:

```text
Models:
explicit ordered cohort

Cases:
same canonical tool cases

Repetitions:
2

Seeds:
42 and 43 where supported

Temperature:
0.0

Maximum output tokens:
512

Timeout:
PT2M

Attempts:
1

Advisor:
standard ToolCallingAdvisor

Order:
sequential
```

If a model/provider does not support seed, record unsupported semantics explicitly. Do not simulate a seed.

For Ollama models, seed should normally remain supported and explicit.

## Slice T3.4 — Analysis dimensions

Produce per-model sections without a total rank.

### Compatibility

- provider invocation yield;
- valid tool-call yield;
- callback execution yield;
- final-answer yield.

### Discipline

- required tool use;
- forbidden tool use;
- valid abstention;
- unnecessary invocation;
- no-match handling.

### Arguments

- schema validity;
- expected argument values;
- omitted required values;
- invented values.

### Multi-step behavior

- first tool correct;
- second tool correct;
- dependency order;
- continuation after first callback;
- premature final response;
- duplicate calls.

### Failure recovery

- deterministic callback failure retained;
- fabricated success after failure;
- correct error reporting;
- empty final response.

### Output behavior

- visible reasoning markers;
- output-limit completion;
- response-format pollution;
- final-answer concision.

### Efficiency

- median successful-row latency;
- observed successful-row latency range;
- prompt tokens;
- completion tokens;
- total tokens;
- tokens per passing row.

“Tokens per passing row” may be reported descriptively but must not become a universal efficiency score.

## Slice T3.5 — Reference-model comparison

Compare each small model with the reference model only descriptively.

Examples:

- cases passed by both;
- cases passed only by the reference;
- cases passed only by the small model;
- latency and token differences;
- compatibility failures unique to one model.

Do not infer that the reference answer is semantically correct merely because the reference model is larger.

## Slice T3.6 — Optional capability frontier

After the first cohort, a later analysis may define:

> the smallest tested model that passed every required case under this exact protocol.

This must be phrased narrowly.

Allowed:

> “Among the six tested installed models, model X was the smallest model that passed all locked cases in both repetitions.”

Not allowed:

> “Model X is the smallest model capable of tool calling.”

## Phase 3 exit criteria

- Cohort identities and digests are locked.
- All planned rows are retained.
- Evidence verifies and reanalyzes offline.
- Results remain multidimensional.
- No global leaderboard or general intelligence claim is made.
- Follow-up hypotheses are identified without silently expanding scope.

---

# Phase 4 — Fact-check output-budget compatibility

## Objective

Test the deferred hypothesis arising from the prior `64`-token fact-check run.

Observed prior association:

- ten empty outputs reached the explicit 64-token limit;
- two valid `no` verdicts used two completion tokens.

The earlier evidence did not establish causation.

## Primary hypothesis

Increasing the explicit output-token budget while preserving every other material protocol variable may increase the number of valid `yes` or `no` verdicts.

## Slice F1 — Two-arm experiment

Use exactly two budgets:

```text
64
256
```

Do not begin with four or more token levels.

### Fixed variables

Retain:

- exact judge model digest;
- prompt ID, version, text, and digest;
- fixture catalog identity and order;
- human-confirmation record;
- twelve-row counterbalanced schedule;
- temperature `0.0`;
- seeds `42` and `43`;
- timeout `PT2M`;
- one attempt;
- no pull;
- loopback-only endpoint;
- Spring Boot version;
- Spring AI version;
- execution engine.

### Changed variable

```text
maximum output tokens
```

## Slice F2 — Paired evidence

Use two new run directories.

Do not reuse, overwrite, or modify the previous A5 evidence.

Suggested names:

```text
build/evaluation-matrix/YYYY-MM-DD-budget-64
build/evaluation-matrix/YYYY-MM-DD-budget-256
```

The 64-token arm is a new paired baseline. Do not compare a fresh 256-token run directly against the historic 64-token run if code, framework, or environment provenance differs.

## Slice F3 — Comparison

Add an offline comparison requiring parity except for maximum output tokens and code baseline where explicitly allowed.

Report:

- valid verdict yield;
- empty output yield;
- malformed verdict yield;
- expectation agreement among valid verdicts;
- supported-label yield;
- unsupported-label yield;
- repetition consistency;
- output-limit finish state;
- completion-token distribution;
- latency.

Do not calculate accuracy over rows without valid verdicts.

## Slice F4 — Interpretation

Possible outcomes:

### Outcome A

256 produces substantially more valid verdicts.

Register a later breakpoint study.

### Outcome B

256 remains mostly empty.

Reject the simple output-budget explanation and investigate prompt/model compatibility separately.

### Outcome C

256 produces longer malformed text.

The model may need a stronger verdict-format prompt rather than merely more tokens.

### Outcome D

Results differ inconsistently between repetitions.

Treat as inconclusive; do not infer reliability from two repetitions.

## Optional later breakpoint slice

Only after a successful 64-versus-256 result:

```text
64
96
128
192
256
```

This later slice must be separately planned and authorized.

## Phase 4 exit criteria

- Fresh paired evidence exists.
- Only output budget differs materially.
- Verification and deterministic comparison pass.
- Causal language remains bounded to the controlled comparison.
- No judge ranking or general factuality claim is made.

---

# Phase 5 — Public-safe retrieval and relevancy foundation

## Objective

Create a genuine retrieval path that preserves retrieved documents and can support future relevancy evaluation.

This phase is intentionally later because it introduces a new model/evaluation surface rather than reusing an existing tool protocol.

## Research questions

- Did retrieval return the expected supporting document?
- Was the expected document ranked sufficiently high?
- Did the answer use only retrieved evidence?
- Did the model abstain when retrieval returned no support?
- Can retrieval quality be evaluated separately from answer quality?

## Slice R0 — Retrieval contract design

Define a small repository-authored public-safe corpus.

Recommended first corpus:

- 12–20 short Markdown documents;
- stable document IDs;
- stable filenames;
- no copyrighted external articles;
- no personal data;
- no private Setaccio material;
- a mix of overlapping and distinct topics;
- deliberate distractor documents;
- at least two questions with no supporting document.

Each document should have:

```text
documentId
relativePath
contentSha256 or BLAKE3
title
topic
privacyReviewState
```

## Slice R1 — Query fixture catalog

Create stable fixtures containing:

```text
caseId
query
expectedSupportingDocumentIds
allowedSupportingDocumentIds
forbiddenDocumentIds
expectedNoMatch
humanReviewState
```

Do not encode the expected answer in the retrieval fixture if the first phase evaluates retrieval only.

## Slice R2 — Deterministic lexical baseline

Before embeddings, implement a simple deterministic retrieval baseline such as:

- exact term matching;
- BM25;
- or another plain Java lexical method.

This provides a provider-free retrieval path and helps validate the evidence model before introducing embedding variability.

Record:

- query identity;
- corpus identity;
- retrieved document IDs;
- rank;
- retrieval score;
- content digest;
- retrieval parameters.

## Slice R3 — Retrieval-only evaluation

Initial metrics:

- expected document retrieved;
- expected document in top 1;
- expected document in top 3;
- forbidden document retrieved;
- correct no-match;
- result stability.

Do not call an LLM.

Do not use `RelevancyEvaluator` yet.

## Slice R4 — Embedding retrieval

After the lexical lifecycle is proven, add an explicit embedding provider/model.

Potential local candidates should be selected separately.

Retain:

- embedding provider;
- requested and effective model;
- immutable local digest where applicable;
- vector dimension;
- chunking policy;
- normalization policy;
- distance metric;
- top K;
- corpus identity.

Keep embedding generation opt-in and out of default tests.

Use fixtures or recorded vectors for provider-free tests.

## Slice R5 — Answer generation

Only after retrieval evidence is preserved, add a model answer stage.

Each answer row should retain:

- retrieved document identities;
- exact document ranks;
- prompt identity;
- answer model identity;
- answer text;
- citation or document-reference behavior;
- unsupported assertions;
- abstention behavior.

Do not merge retrieval success and answer correctness.

## Slice R6 — Relevancy evaluation

Introduce Spring AI `RelevancyEvaluator` only when the actual retrieved documents are supplied to it.

Keep separate:

- deterministic retrieval expectation;
- evaluator invocation success;
- evaluator score/verdict;
- human support judgment;
- answer correctness.

An AI evaluator is not ground truth.

## Phase 5 exit criteria

- A real retrieval path exists.
- Retrieved documents are preserved in evidence.
- Retrieval-only evaluation is complete before LLM judging.
- No ordinary fixture context is described as RAG.
- Embeddings, answer generation, and AI judgment remain separately attributable.

---

# Documentation and branch strategy

## Recommended branch sequence

```text
feature/tool-compatibility-plan
feature/lfm-tool-baseline
feature/lfm-system-prompt
feature/small-model-tool-cohort
feature/fact-check-token-budget
feature/retrieval-foundation
```

Use the repository’s normal integration branch flow.

Commit every completed in-scope slice after verification.

Do not push without explicit instruction.

## Suggested commit sequence for Phase 1

```text
Document small-model tool compatibility protocol
Add provider-free tool compatibility contracts
Add deterministic tool compatibility analyzer
Add offline tool compatibility evidence lifecycle
Add explicit host-Ollama tool compatibility runner
Record controlled LFM baseline
Interpret LFM compatibility evidence
Close LFM baseline phase
```

## Suggested commit sequence for Phase 2

```text
Add versioned tool system-prompt catalog
Add paired system-prompt compatibility protocol
Add offline tool prompt comparison
Record controlled LFM prompt intervention
Record human prompt decision
Close LFM prompt phase
```

## Suggested commit sequence for Phase 3

```text
Lock small-model compatibility cohort
Add multi-model compatibility matrix
Add cohort offline analysis and verification
Record controlled small-model cohort
Interpret small-model compatibility evidence
Close small-model cohort phase
```

---

# Required documentation updates per phase

Every completed phase should update as applicable:

```text
README.md
AGENTS.md
CHANGELOG.md
docs/TEST-PLAN.md
docs/ENVIRONMENT.md
docs/DEFERRED-WORK.md
docs/logs/YYYY-MM-DD.md
```

Add a dedicated closeout section stating:

- what was implemented;
- what was executed;
- exact model identities;
- exact settings;
- evidence location;
- verification outcome;
- bounded findings;
- unresolved questions;
- work explicitly not authorized by the closeout.

---

# Suggested Gradle task inventory

## Phase 1 and 2

```text
toolCompatibilityTest
toolCompatibilityMatrix
toolCompatibilityVerify
toolCompatibilityReanalyze
toolCompatibilityCompare
```

## Phase 3

The same matrix task may accept multiple explicit models if its protocol remains clear:

```text
toolCompatibilityCohort
toolCompatibilityCohortVerify
toolCompatibilityCohortReanalyze
```

Avoid adding separate task names unnecessarily if one strongly validated task can support one or many explicit model tags without ambiguity.

## Phase 4

Existing evaluation tasks may be extended only if request and evidence parity remain clear.

Possible dedicated tasks:

```text
localEvaluationBudgetMatrix
localEvaluationBudgetVerify
localEvaluationBudgetReanalyze
localEvaluationBudgetCompare
```

A dedicated task is preferable to overloading the original A5 runner if the original runner’s contract was intentionally fixed.

## Phase 5

```text
retrievalFixtureTest
retrievalMatrix
retrievalVerify
retrievalReanalyze
retrievalCompare
```

Later:

```text
embeddingRetrievalMatrix
retrievalAnswerMatrix
retrievalEvaluationMatrix
```

---

# Acceptance criteria summary

## Phase 1

- Untreated LFM model runs through existing standard tool cases.
- Full digest is recorded.
- No prompt customization occurs.
- Tool and final-answer failures are distinguishable.
- Evidence verifies offline.

## Phase 2

- Exactly one explicit prompt intervention is tested.
- Model digest and all non-prompt settings match.
- Comparison is deterministic.
- Human adoption decision is separate.

## Phase 3

- Multiple explicit small models use one locked protocol.
- No model receives private optimization.
- Results remain multidimensional.
- Reference model is labeled separately.
- No aggregate winner is produced.

## Phase 4

- Fresh 64- and 256-token arms are paired.
- Only maximum output tokens differ.
- Valid-yield and formatting effects are separated.
- No unsupported causal or factuality claim is made.

## Phase 5

- Retrieval is real and document identities are preserved.
- Retrieval is evaluated before answer generation.
- Relevancy evaluation receives actual retrieved documents.
- Public/private and offline-default boundaries remain intact.

---

# Work explicitly postponed

## Testcontainers

Postpone until there is a concrete provisioning or service-connection question.

Containerizing Ollama would not answer:

- reasoning leakage;
- tool selection;
- malformed arguments;
- final-response emptiness;
- token-budget compatibility.

Any future container work stays in `setaccio-testcontainers`.

## Additional hosted providers

The Anthropic proof already demonstrates the provider-neutral chat boundary.

Do not add OpenAI, Google, Amazon, or Microsoft merely to increase provider count.

Add another provider only for a specific capability question, such as:

- provider-native tool semantics;
- structured output differences;
- server-side tool metadata;
- multimodal behavior;
- unsupported-option handling.

## MCP

Postpone until the direct tool-calling compatibility baseline is understood.

MCP would add:

- discovery;
- transport;
- server lifecycle;
- remote capability metadata;
- another failure boundary.

The first small-model study should not confuse model tool behavior with MCP transport behavior.

## New Tool Search indexes

Regex Tool Search already has a controlled baseline.

Do not introduce semantic, vector, or hybrid discovery indexes until there is a specific discovery hypothesis.

## Additional model types

Postpone image generation, transcription, speech synthesis, moderation, and general embedding work.

Embeddings become justified as part of the retrieval phase rather than as isolated API-surface coverage.

## Interactive endpoint migration

Do not migrate `/api/lab/tools`, `/api/lab/chat`, or other interactive endpoints to new internal boundaries without explicit parity tests and a separately stated migration purpose.

## Release and tag

Postpone until the next body of work forms a coherent public milestone.

When chosen, decide together:

- semantic version;
- changelog release section;
- branch promotion;
- release notes;
- tag.

---

# Recommended immediate next action

Begin with Phase 0 and Phase 1 only.

The first authorized implementation scope should be:

> Add a provider-free, offline-verifiable standard tool-calling compatibility matrix for one already-installed LFM2.5 Ollama model, reusing the existing canonical public-safe tool cases without changing the interactive endpoint, tool catalog, Tool Search implementation, or default Gradle lifecycle.

The first live execution should occur only after:

- the protocol is locked;
- all provider-free tests pass;
- the model digest resolves;
- the output directory is fresh;
- the exact command is explicitly reviewed.

This gives the project one clean answer before introducing prompt interventions or model comparisons:

> Can this specific small model complete this specific existing tool contract, and if not, at which boundary does it fail?

# Appendix A 
 Spring AI 2.0 Tool-Calling Implementation Notes

## Status

This appendix supplements the implementation guidance in the Small-Model Tool-Calling Compatibility Plan.

It introduces no new research questions, experimental variables, or authorization. It records implementation constraints discovered before coding so that Phase 1 preserves correct evidence boundaries when exercising Spring AI 2.0 tool calling through Ollama.

These notes apply to the existing standard `ToolCallingAdvisor` execution path unless a later phase explicitly authorizes a different execution model.

---

# A1. Tool-call detection precedence

## Background

A successful assistant response requesting tool execution may legitimately contain:

- one or more tool calls,
- empty assistant text,
- `null` assistant text,
- preliminary reasoning-style text,
- or any combination of the above.

Therefore, assistant text alone must never determine whether the model produced a valid action.

## Required evaluation order

Assistant responses shall be evaluated in the following order:

1. assistant message present;
2. tool-call presence;
3. assistant text presence;
4. finish metadata;
5. downstream callback outcome.

The existence of one or more tool calls is the primary indicator that the model requested tool execution.

A blank or missing assistant text field shall not by itself classify the row as an empty model response.

## Classification guidance

The following conditions shall remain separate:

```text
provider invocation succeeded

tool call produced

tool call valid

callback executed

callback succeeded

final assistant response present

final contract passed
```

A row may therefore legitimately satisfy:

```text
provider invocation succeeded = true

tool call produced = true

assistant text present = false
```

without representing a failure.

---

# A2. Finish-reason handling

## Background

Provider finish metadata is provider-specific diagnostic information.

Different Ollama models, GGUF chat templates, or provider adapters may report different finish reasons while still requesting identical tool execution.

The experiment therefore shall not rely on finish reason to determine whether a tool request occurred.

## Required behavior

Tool-call presence shall be the ground truth.

Finish reason shall be retained only as diagnostic metadata.

Possible observations include:

```text
toolCalls + finishReason=tool_calls

toolCalls + finishReason=stop

toolCalls + finishReason=null

noToolCalls + finishReason=stop

noToolCalls + finishReason=length
```

No finish-reason value shall override the observed tool-call collection.

---

# A3. Assistant lifecycle boundaries

The recorder shall preserve distinct lifecycle stages.

At minimum, evidence shall distinguish:

```text
provider invocation

assistant tool request

tool execution

tool callback result

final assistant completion
```

The implementation shall avoid collapsing these stages into one success flag.

Examples:

A callback may succeed while the final assistant response is empty.

A valid tool request may be followed by malformed final output.

A provider invocation may succeed without producing any tool request.

These represent different compatibility findings.

---

# A4. Tool argument schema fidelity

## Objective

The benchmark measures compatibility with the declared tool contract.

It does not attempt to maximize callback success through permissive coercion.

## Required behavior

Tool argument DTOs shall retain ordinary strict typing.

Example:

```java
record CountRequest(int count) {}
```

The benchmark shall record observed model behavior rather than relaxing schema validation.

For example:

Correct:

```json
{
  "count": 5
}
```

Potential incompatibility:

```json
{
  "count": "5"
}
```

If callback binding fails because the generated arguments do not satisfy the declared schema, the failure shall be preserved as evidence.

The benchmark shall not intentionally introduce lenient coercion solely to improve compatibility statistics.

---

# A5. Tool argument failure taxonomy

Where practical, callback failures should distinguish their primary cause.

Suggested categories include:

```text
MALFORMED_JSON

SCHEMA_TYPE_MISMATCH

MISSING_REQUIRED_ARGUMENT

UNKNOWN_ARGUMENT

CALLBACK_BINDING_FAILURE

CALLBACK_INVOCATION_FAILURE
```

The implementation should classify observable failure causes rather than relying on specific exception class names.

---

# A6. Visible reasoning text

Some local models emit observable reasoning-style text before, during, or after tool requests.

Examples include:

```text
<think>

</think>

Thinking...

Here's a thinking process:
```

These markers are observable output only.

The benchmark shall not describe them as the model's actual internal reasoning.

Suggested terminology:

```text
visibleReasoningText

reasoningStyleOutput
```

rather than:

```text
chainOfThought
```

unless future provider documentation explicitly establishes stronger semantics.

Visible reasoning text should remain a diagnostic observation unless a specific case contract explicitly forbids it.

---

# A7. Standard ToolCallingAdvisor observability

Phase 1 intentionally evaluates the existing standard Spring AI `ToolCallingAdvisor` path.

The implementation shall first determine whether the standard advisor exposes sufficient trace information to preserve:

```text
initial assistant tool request

tool execution

tool callback

final assistant response
```

If the standard advisor provides adequate observability, no custom execution loop shall be introduced.

If observability is incomplete, the limitation shall be documented rather than immediately replacing the standard execution model.

A custom execution loop using lower-level Spring AI tool-calling APIs remains possible in a future separately authorized phase, but is outside the scope of this plan.

---

# A8. Row-level resilience

The compatibility matrix should continue executing after ordinary model compatibility failures.

Examples include:

- malformed tool arguments,
- callback binding failures,
- callback execution failures,
- unsupported model tool behavior,
- provider parsing failures,
- empty assistant responses.

These outcomes shall be retained as row evidence.

By contrast, protocol integrity failures shall terminate execution before or during the run.

Examples include:

- failed preflight validation,
- unresolved model identity,
- invalid output directory,
- evidence corruption,
- manifest integrity failure,
- protocol drift,
- programmer invariant violations.

The implementation shall distinguish between:

```text
compatibility failure
```

and

```text
experimental integrity failure
```

Only the latter should abort the controlled matrix.

---

# A9. Implementation principle

The compatibility study exists to observe model behavior rather than compensate for it.

Accordingly, the implementation should prefer preserving evidence over silently repairing model output.

Whenever practical:

- record,
- classify,
- preserve,
- verify,
- and interpret,

rather than automatically correcting or normalizing incompatible model behavior.

This principle is consistent with the existing evidence lifecycle used throughout `setaccio-lab`.
