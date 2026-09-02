# R4 Qwen3 Embedding candidate priority

## Decision

For the deferred Phase 5 R4 embedding-retrieval slice, the dedicated
`qwen3-embedding` family is the top candidate. The first versioned tag to
inspect is `qwen3-embedding:0.6b`; the unqualified `:latest` alias and larger
`:4b`/`:8b` tags are not first-choice substitutes.

This priority follows the official Ollama embedding documentation and catalog,
which list Qwen3 Embedding among the recommended embedding models and expose
versioned `0.6b`, `4b`, and `8b` tags:

- [Ollama embeddings](https://docs.ollama.com/capabilities/embeddings)
- [Qwen3 Embedding tags](https://ollama.com/library/qwen3-embedding/tags)

## Boundary

This is a planning priority, not an installed-capability, quality, ranking, or
model-selection conclusion. A future explicitly requested R4 slice must first
inspect the already-installed `qwen3-embedding:0.6b` tag, retain its complete
digest, and confirm literal `embedding` capability in `ollama show`. An
`embedding length` field is insufficient. If the tag is absent or fails that
gate, stop without pulling, silently substituting, or creating formal evidence.
