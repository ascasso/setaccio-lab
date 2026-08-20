# 2026-08-20

## Phase 1 live matrix, evidence review, interpretation, and exit review

### Authorization and preflight

- Executed the locked Phase 1 command for the already-installed untreated
  `hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0` model.
- Ollama was reachable at `http://localhost:11434`; observed Ollama version was
  `0.32.14`.
- The exact model was installed and no pull was required. Its full digest was
  `2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed`.
- The worktree was clean at commit `62181fb40768231c87f9c3d76ca450fa1cb0842f`.
- Provider-free verification passed before the live run:
  `./gradlew :setaccio-lab:test :setaccio-lab:toolCompatibilityTest
  :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build
  --rerun-tasks --no-daemon`.

### Locked execution

- Standard Spring AI tool-calling advisor; no Tool Search.
- Eight ordered canonical cases, two repetitions, seeds `42` and `43`.
- Temperature `0.0`, `512` maximum output tokens per provider turn, `PT2M`
  complete logical-row deadline, one logical attempt per row, sequential
  order, and Ollama pull strategy `never`.
- All 16 planned logical row attempts executed in the fresh ignored directory
  `setaccio-lab/build/tool-compatibility/2026-08-20-lfm-baseline/`.
- The raw artifact SHA-256 was
  `e8bd749f4712337dde5352d6a0ccf331fb776c5de0c21e7392c085bb77582700`.

### Evidence review

- All 16 rows reached exactly one provider turn; all 16 first turns were
  classified `PROVIDER_FAILURE`.
- No row timed out, no row was retried or replaced, and no model was pulled;
  zero logical row attempts completed successfully.
- No tool calls or callback responses were observed. No final assistant
  response, usage metadata, output-limit state, or visible reasoning marker
  was observed.
- The saved safe failure message was `Ollama provider turn failed`. The
  evidence does not identify the underlying provider/framework cause.
- The deterministic analyzer reports 16 missing required calls and two exact
  empty-call-sequence matches for cases whose oracle has no required calls;
  those two matches are not successful tool calls and no row passed its full
  case contract.
- `toolCompatibilityVerify` passed offline, and
  `toolCompatibilityReanalyze` reproduced `SUMMARY.md` deterministically.

### Bounded interpretation

This run establishes that, under the locked untreated protocol and this
installed model digest, every logical row failed at the first observed Ollama
provider turn before tool-call or final-response evidence became available.
It does not establish why the provider turns failed, whether a prompt would
change the result, tool-calling quality, reliability, production suitability,
or a comparison with another model. It also provides no basis for interpreting
visible reasoning, usage, latency of successful rows, or tool-callback
behavior.

### Exit review

- Clean baseline: satisfied.
- One complete 16-row run: satisfied.
- Ordered provider-turn and per-call evidence: schema present; no per-call
  observations were reached because every row failed at turn one.
- Exact oracle-based selection and argument dimensions: analyzed separately;
  no observed tool calls and no full contract passes.
- No prompt intervention: satisfied.
- Offline integrity verification and deterministic reanalysis: satisfied.
- Bounded interpretation recorded: satisfied in this log and the public-safe
  status documents.
- Phase 2 decision: deferred. A paired prompt-intervention runner and fresh
  evidence are not authorized by this closeout; no prompt effect is inferred
  from the untreated provider-failure baseline.

Raw evidence remains ignored and unpublished. No credentials, Docker,
additional provider, model pull, release, tag, or push was used or authorized
by this closeout.
