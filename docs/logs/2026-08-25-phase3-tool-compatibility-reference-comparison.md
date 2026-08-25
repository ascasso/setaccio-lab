# 2026-08-25

## Phase 3 T3.5 provider-free reference comparison

### Authorization and scope

- The project owner authorized provider-free T3.5 implementation and one
  deterministic offline descriptive comparison of
  `setaccio-lab/build/tool-compatibility/2026-08-24-approved-cohort/`.
- The authorized boundary allowed an isolated
  `toolCompatibilityCohortCompare` task, provider-free tests, public-safe
  documentation, and one focused commit. It did not require or use a new
  Ollama call, rerun, replacement row, model pull, model change, remote
  provider, Docker, or new evidence directory.
- The comparator reads one explicit direct child of
  `build/tool-compatibility/`, strictly verifies its manifest, raw result, and
  deterministic T3.4 summary before comparison, and writes the T3.5 report
  only to standard output. It does not modify or add an evidence artifact.

### Provider-free implementation

- The comparison pairs each peer row with the separately labelled reference by
  exact locked case ID, repetition, and sequence.
- It classifies each pair as both pass, reference only, peer only, or neither;
  retains both deterministic primary diagnostics and output-limit states; and
  emits signed reference-minus-peer row-latency and total-token deltas.
- Missing or unverifiable evidence stops before report rendering. Total-token
  differences remain unavailable when either side did not retain a total.
- The report displays the full deployed tag, immutable digest,
  artifact/runtime format, and quantization or precision for every side. Its
  fixed boundary rejects reference-ground-truth, aggregate-score, winner,
  selection, general-capability, semantic-correctness, production, and
  backend-normalized interpretations.

### Preserved evidence

- Run: `2026-08-24-approved-cohort`
- Clean execution baseline:
  `e897edf81cd8397abab469eb80bdb054cadbe5cd`
- Ollama runtime recorded by the evidence: `0.32.15`
- Rows: five peers plus one separately labelled reference, eight cases, two
  repetitions, `96` retained rows total
- Reference: `qwen3.8:27b-mlx`, digest
  `5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e`,
  `safetensors/MLX via Ollama`, `nvfp4`
- Raw SHA-256:
  `94f16ae48211b9816dfaba9ff33156a111efaff7ce93aaf29b25ea86cfdfbde4`
- Summary SHA-256:
  `270984bd572890327f80a358fb4a22558aef422226fd83acb243d37dd7076eec`

### Deterministic pass overlap

Counts below are locked case/repetition observations out of `16` per peer.

| Peer | Both pass | Reference only | Peer only | Neither |
| --- | ---: | ---: | ---: | ---: |
| LFM2.5 Q8_0 | 0 | 16 | 0 | 0 |
| Granite 4.1 3B | 14 | 2 | 0 | 0 |
| Ministral 3B | 12 | 4 | 0 | 0 |
| Gemma 4 E2B | 14 | 2 | 0 | 0 |
| Qwen 3.5 0.8B | 14 | 2 | 0 | 0 |

The reference passed every locked row in this saved run. That observation does
not make its answer semantic ground truth or establish reference superiority.

### Reference-only compatibility observations

- LFM2.5: all 16 peer rows carried `PROVIDER_FAILURE`; no peer-side total-token
  value was available.
- Granite: both `catalog-multi-step` rows carried
  `EXPECTED_ARGUMENT_MISMATCH` on the peer side.
- Ministral: both `catalog-multi-step` rows carried
  `EXPECTED_CALL_SEQUENCE_MISMATCH`, and both
  `no-applicable-domain-tool` rows carried `CALLBACK_INVOCATION_FAILURE` after
  the peer selected the forbidden deterministic failure tool.
- Gemma: both `catalog-multi-step` rows carried
  `EXPECTED_CALL_SEQUENCE_MISMATCH`, and both retained a reached output-limit
  state at the explicit 512-token provider-turn limit.
- Qwen 3.5: both `no-applicable-domain-tool` rows carried
  `CALLBACK_INVOCATION_FAILURE` after the peer selected the forbidden
  deterministic failure tool.

These deterministic primary categories identify the comparison surface; the
more detailed per-model T3.4 record remains the source for call, argument,
callback, and output interpretation.

### Latency and total-token observations

The ranges below are arithmetic reference-minus-peer row deltas, not an
efficiency score. They mix the locked reference's MLX deployment with the
peers' GGUF deployments and include failed or incomplete rows where stated.

| Peer | Row-latency delta range | Total-token delta range |
| --- | ---: | ---: |
| LFM2.5 Q8_0 | +3,801 to +28,158 ms | unavailable |
| Granite 4.1 3B | +3,632 to +25,334 ms | +95 to +700 |
| Ministral 3B | +2,864 to +21,529 ms | -798 to +385 |
| Gemma 4 E2B | -632 to +16,920 ms | +14 to +1,095 |
| Qwen 3.5 0.8B | -1,454 to +24,429 ms | -1,343 to +156 |

LFM2.5 latency pairs compare immediate failed peer turns with completed
reference rows and therefore do not support a performance interpretation.
Across all peers, these deltas describe only the exact deployed artifacts,
runtime paths, prompts, settings, cases, and two repetitions in the preserved
run. They are not backend-normalized and cannot be attributed solely to model
weights, family, architecture, or size.

### Verification and boundary

- `./gradlew :setaccio-lab:toolCompatibilityTest --no-daemon` passed.
- `./gradlew :setaccio-lab:test --no-daemon` passed.
- `./gradlew :setaccio-lab:help --task toolCompatibilityCohortCompare
  --no-daemon` passed and exposed the required `--run-dir` option.
- `./gradlew :setaccio-lab:check --dry-run --no-daemon` passed without
  scheduling the comparison or dedicated tool-compatibility test task.
- `git diff --check` passed.
- The authorized comparison command completed once and wrote only to standard
  output:

```bash
./gradlew -q :setaccio-lab:toolCompatibilityCohortCompare \
  --run-dir=build/tool-compatibility/2026-08-24-approved-cohort \
  --no-daemon
```

- The saved evidence hashes remained unchanged after comparison. No Ollama or
  other provider was contacted.
- This closes T3.5 only. Optional T3.6, any new inference, rerun, replacement,
  repair, pull, substitution, customization, evidence-based model selection,
  release, tag, or push remains separately unauthorized.
