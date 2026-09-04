# LFM tool-capability check

On 2026-09-03 the project owner explicitly started a small, standalone
diagnostic slice: determine whether the currently installed Phase 1/Phase 2
artifact rejects a tool-bearing chat request at the Ollama provider boundary.
This record is scoped to that question only.

The Phase 1 and Phase 2 closeouts recorded that every first provider turn
across 48 attempts (Phase 1: 16 rows; Phase 2: 32 interleaved attempts) against
`hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0` was classified
`PROVIDER_FAILURE` by Spring AI's standard `ToolCallingAdvisor`, with no tool
call ever observed and no identified cause. The 2026-09-02 read-only capability
observations noted the same artifact currently advertises `completion` only,
not `tools`, and named that as a plausible but untested cause.

## What this diagnostic does and does not establish

The historical evidence does not identify the failure cause, and this
diagnostic does not change that. Current capability metadata and this
diagnostic's calls use the current Ollama runtime; the closed phases used
earlier runtime state. This diagnostic can establish current
deployed-artifact/runtime behavior only, not the historical root cause.

## Confirmed identity, no pull or modification

- Requested tag: `hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0`
  (already installed; no pull, rename, substitution, or modification was
  performed).
- Full digest: `2c88e114a368b8500aabb7cf32e8a16c274d2265b640c601198a784a559bc5ed`
  — matches the digest recorded in the Phase 1/Phase 2 closeouts and the
  2026-09-02 observations log.
- Ollama runtime: `0.33.3`, loopback endpoint `http://localhost:11434`. The
  2026-09-02 observations log recorded `0.33.2`; the runtime has since
  advanced. Capabilities were re-confirmed live under `0.33.3` immediately
  before this diagnostic.
- Advertised capabilities (`/api/show`): `["completion"]`. Unchanged from the
  2026-09-02 observation. The model's Modelfile template also contains no
  tool-call formatting block (no tool-role or tool-call placeholder), which is
  independently consistent with the advertised capability list.

## Diagnostic procedure

Exactly two direct, non-streaming `POST /api/chat` calls were made against the
loopback endpoint, at the provider boundary (not through Spring AI or
`ToolCallingAdvisor`), with no retries:

- **Call A**: one user-role message with a minimal, deterministic arithmetic
  prompt, plus a single minimal deterministic zero-argument function tool
  definition (`get_current_time`, no parameters) in the top-level `tools`
  array.
- **Call B**: byte-identical to Call A except the `tools` field is omitted
  entirely. No other field differs.

Both calls shared: `temperature=0.0`, `seed=42`, `num_predict=512`,
`stream=false`, a 120-second client timeout, and identical message content.
The `tools` field was the only material difference between the two request
bodies.

No follow-up calls were made based on either result, and neither call was
retried.

## Outcomes

| Call | Tools field | HTTP status | Result |
| --- | --- | --- | --- |
| A | present (1 function) | `400` | Provider rejected the request synchronously with an explicit error stating the model does not support tools. |
| B | absent | `200` | Provider returned a complete, well-formed chat completion (`done: true`, `done_reason: "stop"`). |

Neither call timed out, and neither produced a connection-level failure; both
completed with a definite HTTP status from the provider.

## Interpretation

The tool-bearing request failed while the otherwise-identical tool-free
request succeeded. Stated plainly: **the currently deployed
artifact/runtime rejects this tool-bearing chat request at the provider
boundary**, synchronously, with an explicit provider-side error — not a
timeout, a malformed response, or a Spring AI-side classification artifact.

This is consistent with the historical Phase 1 and Phase 2 `PROVIDER_FAILURE`
observations, which also occurred on the first provider turn of a
tool-bearing request against this same tagged artifact.

This does **not** prove that provider-boundary tool rejection was the
historical cause of the Phase 1/Phase 2 `PROVIDER_FAILURE` classifications —
those runs used an earlier Ollama runtime, and runtime/manifest behavior can
differ across versions. It is also **not** proof about the underlying LFM2.5
model architecture's latent tool-calling ability: the rejection may originate
in this specific GGUF conversion, its imported Modelfile/template (which
lacks tool-call formatting), Ollama's capability gate, or elsewhere in this
particular deployed artifact, rather than in the architecture itself.

How this diagnostic should be weighed against the two closed phases — whether
it warrants any change to their recorded status, a new deferred-work note, or
no action at all — remains the project owner's decision. This record does not
make that call, and no Phase 1 or Phase 2 evidence or closeout was read for
reinterpretation, rerun, repaired, replaced, or rewritten.

## Evidence retention

Verbatim request bodies, response bodies, HTTP status codes, curl exit codes,
timestamps, full model/runtime identity, and SHA-256 checksums for every file
are retained in the ignored, durable bundle:

```
setaccio-lab/local/evidence/lfm-tool-capability/2026-09-03-lfm-tool-capability/
```

That bundle contains 8 files (2 request bodies, 2 response bodies, one
`ollama version` capture, one trimmed `ollama list` entry, one `ollama show`
capture, and `metadata.json` describing the run), plus `SHA256SUMS.txt`. Raw
response payloads are not reproduced in this tracked log.

## What this record does not authorize

No additional generation call beyond the exact two described above was made.
No model pull, rename, substitution, or modification occurred. No retry or
repair of either call was performed. No remote provider, credential, Docker,
release, or tag activity occurred. No Phase 1 or Phase 2 evidence or closeout
was mutated.
