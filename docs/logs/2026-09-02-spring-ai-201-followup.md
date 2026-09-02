# Spring AI 2.0.1 compatibility follow-up

On 2026-09-02, the remaining Spring AI `2.0.1` follow-up work was completed:
the vision invocation boundary, explicit tool-call limits, and the stale
current-status version references.

## Vision `EmptyUsage` handling

`VisionModelInvoker` read usage metadata but tested only for `null`.
`ChatResponseMetadata`'s constructor defaults its usage field to `EmptyUsage`,
whose token getters return `0`, so the null test was effectively dead and an
absent usage report was recorded as a synthetic zero-token result. The vision
path now treats `EmptyUsage` as unavailable, matching the chat, tool, and
evaluator paths adapted in
[2026-08-31-dependencies.md](2026-08-31-dependencies.md).

## Vision option handling on the direct `ChatModel` path

`OllamaChatModel.buildRequestPrompt` substitutes the model's default options
only when the prompt carries none. A non-null partial options object is used
verbatim, so the vision boundary's model-plus-optional-settings object silently
discarded every configured Ollama default.

This is not a `2.0.1` regression. The method is byte-identical in
`spring-ai-ollama` `2.0.0` and `2.0.1`; it was a standing defect that the
upgrade review surfaced.

The boundary now materializes a complete options object from the model defaults
with `ollamaChatModel.getOptions().mutate()` and then applies the requested
model plus any explicit temperature, seed, or token setting over it. `ChatClient`
would also merge correctly, but it auto-registers a tool-calling advisor on
every call, which would have changed the vision protocol; the direct-call
protocol is unchanged.

## Tool-call limits

Spring AI `2.0.1` made tool-call limits configurable. `DefaultToolCallingManager`
defaults to 40 calls per tool and 150 total with `ToolCallLimitBehavior.THROW`;
exceeding either limit aborts the invocation rather than truncating it, so the
limits are part of observable run behaviour.

`ToolCallLimitPolicy` now pins those values and both tool paths build their
manager from it, so a later framework default cannot change the protocol
silently. The pinned values equal the current framework defaults, so present
behaviour is unchanged.

The effective limits are recorded here and in `AGENTS.md` rather than in saved
evidence. `ToolSearchMatrixEvidence` compares the exact manifest settings key
set against the locked protocol fields, so adding a settings key would make
every retained Tool Search manifest fail offline verification. No evidence
format changed and no retained evidence was touched.

## Evaluator contract recheck

The `RelevancyEvaluator` and `FactCheckingEvaluator` contracts were re-checked
against `2.0.1`. Their public members and their embedded evaluation prompt
texts are unchanged from `2.0.0`; the compiled classes differ only in
non-string metadata.

## Documentation

Stale current-status version references were updated to Spring AI `2.0.1` and
Spring Boot `4.1.1` in `AGENTS.md`, `setaccio-testcontainers/README.md`,
`docs/TEST-PLAN.md`, and `docs/LOCAL-AI-EVALUATION-PLAN.md`. Historical
provenance was preserved, including the manifest binding at
`docs/LOCAL-AI-EVALUATION-PLAN.md:149-150`, the dated entries under
`docs/logs/`, and the framework-version fixtures in existing tests.

## Verification

```text
./gradlew build --no-daemon
./gradlew :setaccio-lab:compileToolCompatibilityJava \
  :setaccio-lab:compileToolSearchSmokeJava \
  :setaccio-lab:compileRetrievalJava :setaccio-lab:compileChatMatrixJava --no-daemon
./gradlew :setaccio-lab:dependencyInsight --dependency spring-ai-model \
  --configuration runtimeClasspath --no-daemon
git diff --check
```

The new vision coverage was confirmed to fail against the pre-fix boundary and
pass against the fixed one: `EmptyUsage` token counts, configured-default
inheritance, and per-setting override each detect the old behaviour.

No provider, Docker, or model execution was used for this change. No push was
performed.
