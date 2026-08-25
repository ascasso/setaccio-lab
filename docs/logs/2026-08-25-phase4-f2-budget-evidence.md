# Phase 4 F2 paired fact-check evidence lifecycle

## Implementation

- Added the isolated `LocalEvaluationBudgetEvidence` boundary around the
  existing per-arm local fact-check evidence format. It writes both fresh arms
  with the same code baseline and verifies the shared suite, framework,
  prompt/catalog/review, model, schedule, generation, timeout, and attempt
  identities. Only maximum output tokens may differ.
- Added standalone `localEvaluationBudgetVerify` and
  `localEvaluationBudgetReanalyze` tasks. They accept two saved run
  directories directly under ignored `build/evaluation-matrix/`, do not start
  Spring, and do not contact a model provider.
- Kept F3 comparison out of this slice. The pair verifier checks protocol
  parity but does not calculate yields, accuracy denominators, finish-state,
  token, or latency comparisons.

## Verification and boundary

- `localEvaluationBudgetTest` passed with sixteen provider-free tests,
  including pair write/verify/reanalyze repair, raw tamper preservation,
  extra-artifact rejection, Git-baseline drift, distinct-directory and
  token-arm guards, and offline argument/path validation.
- No Ollama endpoint was contacted, no model inventory was inspected, no model
  was pulled, and no formal F1 evidence directory was created.
- The historic A5 evidence remains contextual only. F3 offline comparison and
  F4 interpretation remain separate follow-up slices.
