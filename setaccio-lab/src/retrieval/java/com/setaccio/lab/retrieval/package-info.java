/**
 * Provider-free public retrieval corpus and query contracts for Phase 5.
 *
 * <p>This source set implements the validated document corpus, human-gated
 * retrieval-only query labels, one deterministic lexical ranking baseline, a
 * provider-free saved-evidence lifecycle for retrieval-only metrics, an
 * explicit opt-in local Ollama embedding boundary, and an opt-in R5 answer
 * boundary. R5 consumes verified R3 evidence without re-running retrieval,
 * preserves exact document ranks/text beside each answer, and leaves semantic
 * support assessment to a later slice. It does not run relevancy evaluation.</p>
 */
package com.setaccio.lab.retrieval;
