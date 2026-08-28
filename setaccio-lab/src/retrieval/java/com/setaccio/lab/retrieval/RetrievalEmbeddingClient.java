package com.setaccio.lab.retrieval;

import java.util.List;

/** Narrow embedding-provider boundary used by the opt-in R4 runner and provider-free tests. */
interface RetrievalEmbeddingClient {

    EmbeddingResponse embed(RetrievalEmbeddingModelIdentity modelIdentity, List<String> inputs);

    record EmbeddingResponse(
            String effectiveModel,
            List<List<Float>> vectors,
            Long totalDurationNanos,
            Long loadDurationNanos,
            Integer promptEvalCount
    ) {

        public EmbeddingResponse {
            vectors = vectors == null ? List.of() : vectors.stream()
                    .map(vector -> vector == null ? List.<Float>of() : List.copyOf(vector))
                    .toList();
        }
    }
}
