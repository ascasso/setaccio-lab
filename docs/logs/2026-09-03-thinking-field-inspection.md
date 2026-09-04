# Spring AI thinking-field inspection

On 2026-09-03 the project owner explicitly started a slice to diagnose the
cross-surface empty-response observation recorded in
[2026-09-02-model-capability-observations.md](2026-09-02-model-capability-observations.md).

This record is the **source-inspection** half of that slice. Everything below
was read from Spring AI `2.0.0` sources, Spring AI `2.0.1` bytecode, and this
repository's own code. No model was invoked, no evidence directory was
allocated, and no retained evidence was read, reinterpreted, or mutated.

Source inspection and experimental confirmation are different things. The
findings in "What the source says" are established now and stand whether or not
any later run succeeds. The mechanism in "What is not established" is a
hypothesis that only a controlled run can test.

## What the source says

Verified against `spring-ai-ollama` `2.0.0` sources and `2.0.1` bytecode. Where
the two versions are compared, the mapping is identical.

### 1. The Ollama wire message carries a separate `thinking` field

`OllamaApi.Message` is a record with both `content` and `thinking`:

```java
public record Message(
        @JsonProperty("role") Role role,
        @JsonProperty("content") @Nullable String content,
        @JsonProperty("images") @Nullable List<String> images,
        @JsonProperty("tool_calls") @Nullable List<ToolCall> toolCalls,
        @JsonProperty("tool_name") @Nullable String toolName,
        @JsonProperty("thinking") @Nullable String thinking
) {
```

`javap` on the `2.0.1` jar shows the same six record components and a public
`thinking()` accessor. The field is present in both versions.

### 2. `OllamaChatModel` routes thinking away from the message text

In `OllamaChatModel.internalCall`:

```java
String thinking = ollamaResponse.message().thinking();
Map<String, Object> messageProperties = thinking != null
        ? Map.of(THINKING_METADATA_KEY, thinking) : Map.of();
var assistantMessage = AssistantMessage.builder()
    .content(ollamaResponse.message().content())
    .properties(messageProperties)
    .toolCalls(toolCalls)
    .build();
```

`THINKING_METADATA_KEY` is the literal `"thinking"`. Bytecode inspection of
`2.0.1` shows the same `Message.thinking()` call and the same `"thinking"`
string constant in `lambda$internalCall$3` and `lambda$internalStream$6`.

So `getOutput().getText()` returns **only** `content`. Reasoning reaches the
framework through `getOutput().getMetadata().get("thinking")` — a different
accessor that nothing in this repository currently calls.

### 3. Generation metadata carries the finish reason, conditionally

```java
ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.NULL;
if (ollamaResponse.promptEvalCount() != null && ollamaResponse.evalCount() != null) {
    ChatGenerationMetadata.Builder builder = ChatGenerationMetadata.builder()
        .finishReason(ollamaResponse.doneReason());
    if (thinking != null) {
        builder.metadata(THINKING_METADATA_KEY, thinking);
    }
    generationMetadata = builder.build();
}
```

Two consequences worth recording. The finish reason is Ollama's `done_reason`,
and it is only populated when **both** `prompt_eval_count` and `eval_count` are
present; otherwise the generation metadata is `ChatGenerationMetadata.NULL` and
the finish reason is absent. Thinking is therefore reachable in two places:
assistant-message properties, and — under that condition only — generation
metadata.

### 4. Evaluated output tokens are Ollama's `eval_count`

```java
private static DefaultUsage getDefaultUsage(OllamaApi.ChatResponse response) {
    return new DefaultUsage(Optional.ofNullable(response.promptEvalCount()).orElse(0),
            Optional.ofNullable(response.evalCount()).orElse(0));
}
```

`Usage.getCompletionTokens()` is `eval_count`. Ollama counts generated tokens
there; it does not expose a separate counter that excludes reasoning tokens.

### 5. Reasoning is an explicit request option that this repository never sets

`OllamaApi.ChatRequest` has a `@JsonProperty("think") @Nullable ThinkOption think`
component, and `OllamaChatModel.ollamaChatRequest` fills it from
`requestOptions.getThinkOption()`. `ThinkOption` is a sealed interface with
`ThinkBoolean` (`ENABLED`/`DISABLED`) and `ThinkLevel` (`low`/`medium`/`high`),
built through `OllamaChatOptions.Builder.enableThinking()`,
`disableThinking()`, `thinkLow/Medium/High()`, or `thinkOption(...)`.

The `OllamaChatOptions.thinkOption` javadoc states the default behavior
directly:

> **Default Behavior (Ollama 0.12+):**
> - Thinking-capable models (e.g., qwen3:*-thinking, deepseek-r1, deepseek-v3.1)
>   **auto-enable thinking by default** when this field is not set.
> - Standard models (e.g., qwen2.5:*, llama3.2) do not enable thinking by default.
> - To explicitly control behavior, use `enableThinking()` or `disableThinking()`.

### 6. What this repository currently reads and sets

Two production invocation boundaries call a local Ollama chat model.

`setaccio-lab/src/main/java/com/setaccio/lab/chat/OllamaChatInvocation.java`
builds `OllamaChatOptions` with `model`, `temperature`, `seed`, and
`numPredict` only — no `thinkOption` — and reads the response with:

```java
response.getResult().getOutput().getText()
```

`setaccio-lab/src/main/java/com/setaccio/lab/evaluation/LocalFactCheckJudgeBoundary.java`
wraps the evaluator's `ChatModel` in its own `RecordingChatModel`. That wrapper
receives the whole `ChatResponse` before `FactCheckingEvaluator` consumes it,
so thinking and generation metadata are reachable there — but it also reads
only `getOutput().getText()`, plus response-level `ChatResponseMetadata`
entries, which are a different map from the assistant message's properties and
from generation metadata. Its options come from
`LocalFactCheckJudgeSettings.ollamaOptions()`, which likewise sets no
`thinkOption`.

Neither chat boundary reads assistant-message properties, neither reads the
generation finish reason, and neither sets `ThinkOption`.

Across all of `setaccio-lab/src`, no production code reads
`getOutput().getMetadata()` and no production code references `ThinkOption`,
`enableThinking`, or `disableThinking`. The tool-compatibility suite is the
partial exception worth naming precisely, because it is further along than the
chat paths and none of it applies to them:
`ToolCompatibilityInvocationBoundary` already records
`generation.getMetadata().getFinishReason()` per provider turn;
`ToolCompatibilityVisibleReasoningDetector` looks for textual markers such as
`thinking...` inside assistant *text*, which is a different signal from the
structured `thinking` field; `ToolCompatibilityCohortOllamaInventorySource`
reads the advertised `thinking` capability from `ollama show`; and an existing
cohort test already asserts `options.getThinkOption()` is `null`, pinning the
inherited-default behavior rather than choosing it.

The consequence is mechanical, not speculative: for any model whose default is
to think, this repository sends no reasoning policy, the model's own default
applies, and if that model returns populated `thinking` with empty or absent
`content`, both boundaries classify the row `EMPTY_RESPONSE` while discarding
the reasoning the provider actually returned.

Changing only `OllamaChatInvocation` would leave the fact-check path
uninstrumented, because that path does not go through it.

## What is not established

The inspection above establishes a **capability gap in this repository's
recording**, not a cause for any retained observation.

Not established by inspection:

- that `gemma4:e2b` at digest `7fbdbf8f5e45` actually returned populated
  thinking in any retained run;
- that reasoning tokens consumed the `64`, `128`, or `256` token budgets before
  visible content appeared;
- that the framework, rather than the artifact, is responsible for any recorded
  empty response;
- any quantitative relationship between reasoning policy and visible-verdict
  yield.

Distinguishing "the artifact returned reasoning that the lab discarded" from
"the artifact returned nothing" requires a controlled run that retains content,
thinking, finish reason, and evaluated output tokens separately, at more than
one budget, with the reasoning policy set explicitly rather than inherited.
That run is a separate step of this slice and is recorded separately.

## Boundary

This record retracts nothing and reinterprets nothing. The Phase 2, Phase 4,
and Phase 5 closeouts remain accurate as written; each was bounded to what its
evidence supported and none claimed a mechanism. The Phase 4 output-budget
curve remains a valid observation of visible-verdict yield under maximum
output-token budgets.

No evidence was rerun, repaired, replaced, reanalyzed, mutated, or published.
No model was invoked, pulled, substituted, removed, or customized.
