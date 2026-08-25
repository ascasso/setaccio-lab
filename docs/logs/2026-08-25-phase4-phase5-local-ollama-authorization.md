# 2026-08-25

## Standing local Ollama authorization for Phases 4 and 5

### Owner direction

The project owner authorized local Ollama to be used liberally whenever useful
during subsequently requested Phase 4 and Phase 5 work. The Mac's local compute
capacity is not an authorization blocker, and no additional per-call,
per-command, per-model, per-session, or per-run permission is required.

The standing scope permits Codex to:

- start or connect to loopback Ollama;
- inspect the runtime, installed inventory, and model metadata;
- select among and invoke already-installed local models for implementation,
  smoke checks, diagnostics, protocol preflight, controlled experiments,
  embedding generation, answer generation, and evaluator work;
- repeat disposable diagnostics when useful; and
- execute or restart a complete formal protocol when its clean-baseline or
  evidence-integrity rule requires fresh output.

### Boundaries retained

- The authorization applies only while working on an explicitly requested
  Phase 4 or Phase 5 slice; it does not start a slice by itself.
- Default tests, `check`, `build`, application startup, and CI remain free of
  live provider calls.
- Disposable diagnostics remain separate from formal evidence.
- Formal rows retain their locked attempt policy. Standing authorization does
  not permit selective retry, repair, replacement, or promotion of exploratory
  output into preserved evidence.
- Formal evidence records exact requested/effective installed tags, full
  digests, Ollama version, material settings, and required clean-code baseline
  in fresh ignored output directories.
- The Phase 4 fresh 64/256 arms still execute from the same clean commit. If
  code or worktree state invalidates that pair, both arms restart in fresh
  directories.
- Phase 5 R0–R3 keep formal tasks and evidence provider-free to isolate corpus,
  fixtures, lexical retrieval, and evidence correctness. Separate local
  diagnostics are permitted. R4–R6 may use recorded installed local embedding,
  answer, and evaluator models.

This direction does not authorize a model pull, download, removal, rename,
silent substitution, non-loopback endpoint, remote provider, credential use,
provider spending, Docker, publication of ignored raw output, push, release,
or tag.

### Documentation effect

The active Phase 4/5 plan and its dispatch packets now distinguish standing
local execution authority from implementation-slice start gates and formal
evidence constraints. The deferred-work index, environment guide, test plan,
local evaluation plan, changelog, and repository agent guidance carry the same
boundary. No Ollama call was needed or made for this documentation change.

### Verification

- The change began from clean pushed commit `52f452b` on
  `feature/tool-compatibility-plan`.
- Active Phase 4/5 plan and packet wording was checked for obsolete per-call,
  per-run, local-model-selection, and live-local-judge approval gates.
- The remaining separate-authorization language applies to unrequested slice
  scope, Phase 3, pulls, remote providers, credentials, Docker, or release
  operations—not already-installed loopback Ollama calls in requested Phase
  4/5 work.
- `git diff --check` passed.
