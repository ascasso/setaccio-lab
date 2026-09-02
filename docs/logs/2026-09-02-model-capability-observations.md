# Read-only model capability observations

On 2026-09-02, after the documentation publication cycle, a read-only
`ollama show` inspection was run against four already-installed models that
appear in retained closeouts. The standing local Ollama authorization covers
inspecting installed inventory and model metadata without further approval.

This is not an experiment. No protocol was locked, no evidence directory was
allocated, no model was invoked, pulled, or modified, and no retained evidence
was read, reinterpreted, or mutated.

## Observations

Host Ollama runtime `0.33.2`. Every digest below matches the identity recorded
in the corresponding closeout.

| Model | Digest prefix | Advertised capabilities |
| --- | --- | --- |
| `gemma4:e2b` | `7fbdbf8f5e45` | `completion` `vision` `audio` `tools` `thinking` |
| `granite4.1:3b` | `6fd349357287` | `completion` `tools` |
| `qwen3.8:27b-mlx` | `5642e97495e1` | `completion` `vision` `tools` `thinking` |
| LFM2.5 GGUF `Q8_0` | `2c88e114a368` | `completion` |

## Association with retained observations

Two associations are visible between these capability strings and outcomes
already recorded in immutable closeouts. Both are stated as associations only.

### Empty responses and the `thinking` capability

Every retained run that produced empty responses used one artifact:
`gemma4:e2b` at digest `7fbdbf8f5e45`, which advertises `thinking`. The
observation is cross-surface but single-model, not cross-model.

| Run | Model | Output budget | Empty responses |
| --- | --- | --- | --- |
| Phase 2 chat matrix | `gemma4:e2b` | 128 | 6 of 6 |
| Fact-check A5 | `gemma4:e2b` | 64 | 10 of 12 |
| Phase 5 R5 answer matrix | `gemma4:e2b` | 256 | 4 of 14 |
| Phase 5 R6 relevancy matrix | `granite4.1:3b` | 64 | 0 of 8 eligible |

R6 is the useful contrast. It ran at `64` output tokens, the same budget at
which A5 produced ten empty responses, against a model that does not advertise
`thinking`, and recorded no empty-response outcome.

This registers a candidate mechanism for the Phase 4 output-budget
association: reasoning tokens may consume a small output budget before any
assistant content is produced, and a response field carrying only reasoning
may reach the framework as empty content. The retained Slice A6 observation is
consistent with that shape — all ten empty responses reached the explicit
`64`-token limit while both valid responses used two completion tokens — but
consistency is not confirmation.

Nothing here tests that mechanism. Distinguishing it from the alternatives
(the framework dropping available content, or the artifact returning nothing)
requires a separately authorized diagnostic that retains the content field, any
reasoning field, the finish reason, the evaluated output-token count, and the
explicit reasoning policy, at two budgets, against a direct provider call.

### Phase 1 and Phase 2 `PROVIDER_FAILURE` and the `tools` capability

The Phase 1 and Phase 2 artifact, LFM2.5 GGUF `Q8_0` at digest `2c88e114a368`,
currently advertises `completion` only. It does not advertise `tools`. Both
phases exercised Spring AI's standard `ToolCallingAdvisor` against it, and
every first provider turn across Phase 1's 16 rows and Phase 2's 32 interleaved
attempts was classified `PROVIDER_FAILURE` with no tool call ever observed.

An artifact that does not advertise tool support is a plausible cause of a
first-turn failure on a tool-calling path. A GGUF imported from an external
repository may lack the template that declares that capability.

Two limits on this observation. The capability string was read today under
Ollama `0.33.2`; the Phase 1 and Phase 2 runs used an earlier runtime, and the
Phase 3 cohort ran under `0.32.15`. Advertised capabilities can differ across
runtime versions and manifest revisions, so this is current metadata, not the
metadata as of those runs. Separately, the retained Phase 1 and Phase 2
evidence does not identify the underlying failure cause, and this inspection
does not change that.

## Boundary

Capability strings describe what an artifact's manifest advertises, not its
runtime behavior. These observations establish hypotheses to be tested, not
results.

This record retracts nothing. The Phase 1, Phase 2, Phase 4, and Phase 5
closeouts remain accurate as written: each was bounded to what its evidence
supported, and none claimed a mechanism. Offering a candidate mechanism is not
a reinterpretation of that evidence.

No evidence was rerun, repaired, replaced, reanalyzed, mutated, or published.
No model was pulled, substituted, removed, or customized. No experiment was
started, and none is authorized by this record.
