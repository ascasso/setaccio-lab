# 2026-08-25

## Phase 3 T3.6 capability frontier

### Authorization and scope

- The project owner separately authorized T3.6 after T3.5 completed.
- The authorized boundary covered provider-free analyzer, report, tests,
  public-safe closeout documentation, one deterministic offline analysis of
  `setaccio-lab/build/tool-compatibility/2026-08-24-approved-cohort/`, and one
  focused local commit.
- No Ollama inference, remote provider, model pull, download, removal, rename,
  substitution, retry, replacement row, Docker use, new evidence directory,
  release, tag, or push was used or authorized.

### Provider-free implementation

- The isolated `toolCompatibilityCohortFrontier` task accepts one explicit
  saved cohort directory, strictly verifies the shared-v1 manifest, raw result,
  and deterministic summary, and writes its report only to standard output.
- The analyzer requires every locked case/repetition row for every exact model
  identity. A model qualifies only when all of its locked rows passed the
  suite-owned case contract.
- The size comparison uses the recorded installed-artifact byte size from the
  saved Ollama inventory metadata. It does not use tag naming, advertised
  parameter count, or an estimate.
- The frontier is not measurable when no model qualifies, a qualifying size is
  unavailable or invalid, or multiple qualifiers share the smallest recorded
  size. Missing or unverifiable evidence stops before analysis.
- Provider-free tests cover the measurable rule, visible reference role,
  no-qualifier case, missing qualifying size, tied minimum, strict verification,
  evidence immutability, and exact one-directory CLI.

### Preserved evidence

- Run: `2026-08-24-approved-cohort`
- Clean execution baseline:
  `e897edf81cd8397abab469eb80bdb054cadbe5cd`
- Ollama runtime recorded by the evidence: `0.32.15`
- Schedule: six installed artifacts, eight locked cases, two repetitions,
  `16` rows per artifact and `96` retained rows total
- Prompt policy: untreated, following the bound T2.5 `inconclusive` decision
- Raw SHA-256:
  `94f16ae48211b9816dfaba9ff33156a111efaff7ce93aaf29b25ea86cfdfbde4`
- Summary SHA-256:
  `270984bd572890327f80a358fb4a22558aef422226fd83acb243d37dd7076eec`
- Manifest SHA-256:
  `700a448e096c22a53cc05668d78a0264cfcdcd91bf2fd9f56a0dd54d5c7889ec`

### Deterministic qualification

| Tested installed artifact | Role | Recorded size (bytes) | Passed rows | Qualifies |
| --- | --- | ---: | ---: | --- |
| LFM2.5 Q8_0 | peer | 2,874,774,591 | 0 / 16 | no |
| Granite 4.1 3B | peer | 2,099,520,281 | 14 / 16 | no |
| Ministral 3B | peer | 2,953,828,889 | 12 / 16 | no |
| Gemma 4 E2B | peer | 7,162,405,886 | 14 / 16 | no |
| Qwen 3.5 0.8B | peer | 1,036,046,583 | 14 / 16 | no |
| `qwen3.8:27b-mlx` | reference | 18,174,721,847 | 16 / 16 | yes |

Exactly one tested installed artifact qualified. The frontier is therefore
measurable under the authorized rule:

> Among the tested installed models, `qwen3.8:27b-mlx` was the smallest model
> by recorded installed-artifact size that passed all locked cases in both
> repetitions under this exact protocol.

The selected artifact remains the separately labelled reference, full digest
`5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e`,
using the saved `safetensors/MLX via Ollama` and `nvfp4` deployment metadata.
Because it was the only qualifier, “smallest” compares a one-member qualifying
set; it does not imply that smaller tested artifacts were close to or incapable
of satisfying some other protocol.

### Interpretation boundary

- The recorded byte size is an installed-artifact property, not parameter
  count and not a normalized comparison between GGUF and MLX deployments.
- The result is limited to the exact installed tags, full digests, artifact and
  runtime formats, Ollama `0.32.15`, untreated prompt policy, eight locked
  cases, two repetitions, and one retained run.
- The reference pass is not semantic ground truth. This is not a model ranking,
  production selection, general capability statement, backend-normalized
  result, or evidence that `qwen3.8:27b-mlx` is the smallest model capable of
  tool calling.
- Phase 3 is closed through T3.6. Any new inference, cohort analysis rule, run,
  rerun, repair, replacement, pull, substitution, customization, release, tag,
  or push requires separate authorization.

### Verification and closeout

- `./gradlew :setaccio-lab:toolCompatibilityTest --no-daemon` passed.
- `./gradlew :setaccio-lab:test --no-daemon` passed.
- `./gradlew :setaccio-lab:help --task toolCompatibilityCohortFrontier
  --no-daemon` passed and exposed the required `--run-dir` option.
- `./gradlew :setaccio-lab:check --dry-run --no-daemon` passed without
  scheduling the frontier or dedicated tool-compatibility test task.
- The authorized analysis command completed once and wrote the deterministic
  report to standard output:

```bash
./gradlew :setaccio-lab:toolCompatibilityCohortFrontier \
  --run-dir=build/tool-compatibility/2026-08-24-approved-cohort \
  --no-daemon
```

- The saved raw result, summary, and manifest hashes remained byte-identical
  after analysis. No Ollama or other provider was contacted.
- `git diff --check` passed before the focused local commit.
