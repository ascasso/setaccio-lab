# Phase 4 output-budget breakpoint-study implementation

On 2026-08-26, the project owner explicitly started the separately planned
Phase 4 breakpoint study following the completed `64`/`256` Outcome A
closeout.

## Locked study protocol

- Five fresh output-token arms: `64`, `96`, `128`, `192`, and `256`.
- Each arm retains six fact-check fixtures, two sequential repetitions,
  temperature `0.0`, seeds `42`/`43`, timeout `PT2M`, one attempt per row,
  loopback Ollama, and pull strategy `never`.
- The study therefore schedules 60 rows in token-arm order.
- A clean Git commit and unchanged installed full judge digest are required
  before allocation and between every arm.
- Each arm writes immutable ignored evidence immediately after its 12
  one-attempt rows; no row is retried, replaced, or repaired.

## Implementation boundary

- Added opt-in `localEvaluationBreakpoint` execution plus standalone
  `localEvaluationBreakpointVerify`, `localEvaluationBreakpointReanalyze`, and
  `localEvaluationBreakpointCompare` tasks.
- The offline lifecycle requires all five arm directories, strict non-budget
  protocol parity, and deterministic five-way aggregate output before any
  interpretation.
- Provider-free tests cover the exact arm set, runner arguments, evidence
  write/verify/reanalysis, baseline-drift rejection, and aggregate report.

No model was invoked and no formal breakpoint evidence was created during this
implementation. A clean implementation commit is required before the one
fresh five-arm execution.
