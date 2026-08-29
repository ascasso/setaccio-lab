/**
 * Provider-free public retrieval corpus and query contracts for Phase 5.
 *
 * <p>This source set implements the validated document corpus, human-gated
 * retrieval-only query labels, one deterministic lexical ranking baseline, a
 * provider-free saved-evidence lifecycle for retrieval-only metrics, an
 * explicit opt-in local Ollama embedding boundary, an opt-in R5 answer
 * boundary, and an opt-in R6 Spring AI relevance-evaluation boundary. R5
 * consumes verified R3 evidence without re-running retrieval and preserves
 * exact document ranks/text beside each answer. R6 consumes a verified R5 run
 * without re-running retrieval or answer generation; it supplies only those
 * preserved documents to {@code RelevancyEvaluator} and keeps its observation
 * distinct from retrieval expectation, human support judgment, and answer
 * correctness.</p>
 */
package com.setaccio.lab.retrieval;
