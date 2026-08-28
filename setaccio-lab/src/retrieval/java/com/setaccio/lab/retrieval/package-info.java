/**
 * Provider-free public retrieval corpus and query contracts for Phase 5.
 *
 * <p>This source set implements the validated document corpus, human-gated
 * retrieval-only query labels, one deterministic lexical ranking baseline, a
 * provider-free saved-evidence lifecycle for retrieval-only metrics, and an
 * explicit opt-in local Ollama embedding boundary. R4 retains exact installed
 * model identity and normalized vectors, but does not generate answers or run
 * relevancy evaluation.</p>
 */
package com.setaccio.lab.retrieval;
