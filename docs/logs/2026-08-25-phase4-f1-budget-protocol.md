# Phase 4 F1 fresh fact-check budget protocol

## Implementation

- Added the isolated `localEvaluationBudget` source set and provider-free
  `localEvaluationBudgetTest` task.
- Added the dedicated paired `localEvaluationBudget` task wrapper and runner.
  The runner owns both fresh arms, fixes maximum output tokens to exactly `64`
  and `256`, fixes the timeout to `PT2M`, and does not expose a free token-level
  override.
- The protocol reuses the original locked fact-check prompt, fixture catalog,
  human-confirmation record, counterbalanced twelve-row schedule, temperature
  `0.0`, seeds `42`/`43`, one-attempt policy, loopback-only endpoint, and no-pull
  behavior without changing the A5 `localEvaluation` runner.
- Preflight requires two distinct new dated directories, a complete immutable
  installed judge identity, and a clean 40-character Git commit. The paired
  runner rechecks clean commit and installed model identity before allocation,
  between arms, and after each arm; drift stops the pair.

## Verification and boundary

- `localEvaluationBudgetTest` passed with ten provider-free tests covering the
  exact arms, identity and schedule reuse, argument locking, fresh allocation,
  dirty-worktree rejection, commit drift, and model-digest drift.
- No Ollama endpoint was contacted, no model inventory was inspected, no model
  was pulled, and no formal F1 evidence directory was created.
- F2 paired evidence, F3 offline comparison, and F4 interpretation remain
  separate follow-up slices. Historic A5 evidence remains contextual only.
