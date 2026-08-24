# 2026-08-24

## Phase 3 controlled cohort evidence review and bounded T3.4 interpretation

### Authorization, lock, and evidence integrity

- The project owner approved the exact ordered six-model cohort, one explicit
  local-only execution, and this offline T3.4 interpretation. The cohort used
  five peers plus the separately labelled `qwen3.8:27b-mlx` reference; it did
  not use a mutable `:latest` alias, a pull, a remote provider, Docker, or a
  per-model prompt or thinking-mode override.
- The one run began from clean commit
  `e897edf81cd8397abab469eb80bdb054cadbe5cd`, using Ollama `0.32.15`, the
  untreated `tool-system-none` v1 prompt, the standard Spring AI
  `ToolCallingAdvisor`, temperature `0.0`, explicit seeds `42` and `43`, `512`
  maximum output tokens per provider turn, one `PT2M` logical-row deadline,
  one logical attempt, and no pull.
- The ignored evidence directory is
  `setaccio-lab/build/tool-compatibility/2026-08-24-approved-cohort/`. It
  retained all 96 planned sequential rows (six models, eight cases, two
  repetitions), has raw-artifact SHA-256
  `94f16ae48211b9816dfaba9ff33156a111efaff7ce93aaf29b25ea86cfdfbde4`, and
  verified and reanalyzed offline after execution and again before this review.
- The exact deployed identities remain distinct and ordered:

| Position | Role | Installed tag | Digest | Deployed artifact/runtime |
| ---: | --- | --- | --- | --- |
| 1 | peer | `hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0` | `2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed` | GGUF / Q8_0 via Ollama |
| 2 | peer | `granite4.1:3b` | `6fd349357287c7ffc9e38189a93b48ea175d24fc566b38f09cfc564fb7f303eb` | GGUF / Q4_K_M via Ollama |
| 3 | peer | `ministral-3:3b` | `a48e77f25d7933c64552d810c3ca5c7fc8cce4ad7e1ff1432fe24574c8e146e0` | GGUF / Q4_K_M via Ollama |
| 4 | peer | `gemma4:e2b` | `7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e` | GGUF / Q4_K_M via Ollama |
| 5 | peer | `qwen3.5:0.8b` | `f3817196d142eaf72ce79dfebe53dcb20bd21da87ce13e138a8f8e10a866b3a4` | GGUF / Q8_0 via Ollama |
| 6 | reference | `qwen3.8:27b-mlx` | `5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e` | safetensors / MLX / nvfp4 via Ollama |

### Per-model observations

#### 1. LFM2.5 Q8_0 peer

- None of the 16 logical rows completed: each stopped at its first observed
  provider turn with `PROVIDER_FAILURE`. No tool call, callback, final
  response, usage field, output-limit state, or visible reasoning marker was
  retained.
- This identifies a provider-turn compatibility boundary for this deployed
  artifact under this protocol. It does not identify the underlying cause or
  measure its tool-selection, argument, callback, latency, or response
  behavior.

#### 2. Granite 4.1 3B peer

- Fourteen of 16 full case contracts passed. All rows completed; all 16
  observed tool calls had valid raw JSON and declared-schema validation, and
  the expected deterministic callback failure was retained in both scheduled
  repetitions with lexical error markers and no lexical success claim.
- The two non-passing rows were both `catalog-multi-step`: the ordered calls
  reached both expected tools, but the first lookup used `invoice-sample`
  rather than the locked `fixture-invoice-sample` argument value. Every other
  case passed in both repetitions, including no-match and no-applicable-tool
  handling.
- Two final responses matched the report's lexical format-pollution markers;
  this is a marker observation rather than a semantic judgment. Median
  successful-row latency was `1089.0 ms` (range `181-2917 ms`), with complete
  `32/32` provider-turn token coverage and `1159.1` observed total tokens per
  passing row.

#### 3. Ministral 3B peer

- Twelve of 16 full case contracts passed. All rows completed, raw JSON and
  declared-schema validation succeeded for all observed calls, and both
  deterministic-failure rows retained the expected callback failure and error
  markers without a lexical success claim.
- Both `catalog-multi-step` rows selected `lab_list_catalog_items` first and
  stopped after that call, rather than making the required ordered lookup then
  list sequence. Both `no-applicable-domain-tool` rows invoked the forbidden
  `lab_fail_fixture`, whose callback failed; the remaining six case types
  passed in both repetitions.
- No final response matched the lexical format-pollution markers. Median
  successful-row latency was `1534.5 ms` (range `1131-13661 ms`), with complete
  `32/32` provider-turn token coverage and `1723.0` observed total tokens per
  passing row.

#### 4. Gemma 4 E2B peer

- Fourteen of 16 full case contracts passed. The two non-passing rows were the
  two `catalog-multi-step` repetitions: their first provider turn reached the
  explicit `512`-token limit, with no tool call or final response retained.
  This is an observed association under this deployed protocol, not a causal
  explanation of the limit event.
- For the 12 observed tool calls, raw JSON, declared-schema validation, and
  locked expected arguments all succeeded. Both no-match and deterministic
  failure cases passed in both repetitions; the expected failure was retained
  with lexical error markers and no lexical success claim.
- No final response matched the lexical format-pollution markers. Median
  successful-row latency was `6704.0 ms` (range `4445-11331 ms`), with complete
  `28/28` provider-turn token coverage and `1476.0` observed total tokens per
  passing row.

#### 5. Qwen 3.5 0.8B peer

- Fourteen of 16 full case contracts passed. Both `catalog-multi-step` rows
  satisfied the locked two-call sequence, arguments, and dependency order.
- The two non-passing rows were both `no-applicable-domain-tool`: each invoked
  the forbidden `lab_fail_fixture` instead of abstaining, and its callback
  failed. The other seven case types passed in both repetitions. The expected
  deterministic-failure rows were separately retained with lexical error
  markers and no lexical success claim.
- No final response matched the lexical format-pollution markers. Median
  successful-row latency was `1777.5 ms` (range `1218-3822 ms`), with complete
  `32/32` provider-turn token coverage and `1831.9` observed total tokens per
  passing row.

#### 6. Qwen 3.8 27B MLX reference

- All 16 full case contracts passed under this exact suite. The retained rows
  include valid raw JSON, declared-schema validation, locked call sequences and
  arguments, both no-match responses, both valid abstentions, and both expected
  deterministic callback failures with lexical error markers and no lexical
  success claim.
- No final response matched the lexical format-pollution markers. Median
  successful-row latency was `18196.5 ms` (range `3813-28251 ms`), with complete
  `30/30` provider-turn token coverage and `1757.6` observed total tokens per
  passing row.
- This artifact is a separately labelled reference, not a peer-size result or
  correctness oracle. Its MLX/nvfp4 deployment differs from the peers' GGUF
  deployments, so these observations are not a backend-normalized comparison.

### Cross-cutting limits and bounded conclusions

- No retained final response showed the report's visible-reasoning markers.
  The report's error-reporting, success-claim, and format-pollution counts are
  lexical observations only; they do not determine semantic quality.
- The deterministic no-match case passed in both repetitions for every model
  that completed its rows. The expected deterministic callback-failure case
  was also retained in both repetitions for every completed model; it is not
  counted as an unplanned provider failure.
- The single LFM2.5 artifact remained unevaluable beyond the first provider
  turn. The other observed limitations repeated across both seeds for their
  named case types, but two repetitions under this one deployed protocol do not
  establish statistical reliability or a general model property.
- Latency and token observations are descriptive properties of each locked
  deployed tag, digest, artifact/runtime format, quantization or precision,
  and Ollama `0.32.15`. They are not an efficiency score and must not be
  attributed solely to weights, model family, architecture, or size.
- This review makes no aggregate winner, model selection, general tool-calling
  capability, semantic correctness, production-suitability, or reference-model
  superiority claim. It does not alter the untreated prompt policy.

### Follow-up boundary

- T3.5 is not authorized. No reference-model comparison report, additional
  inference, model change, retry, repair, replacement row, pull, or new output
  directory is authorized by this interpretation.
- If the owner later wants a new experiment, it must be separately designed
  and authorized as fresh evidence rather than a correction of this run. The
  observed LFM provider boundary, two repeated multi-step patterns, two
  repeated no-applicable-tool patterns, and Gemma's two limit events are
  candidate questions only, not conclusions about cause or general capability.
