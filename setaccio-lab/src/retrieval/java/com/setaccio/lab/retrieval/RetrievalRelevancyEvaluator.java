package com.setaccio.lab.retrieval;

import java.util.List;

/** R6 boundary for evaluating an answer against the exact documents retrieved for that row. */
@FunctionalInterface
interface RetrievalRelevancyEvaluator {

    RetrievalRelevancyEvaluatorOutcome evaluate(
            String query,
            List<RetrievalEvaluationRetrievedDocument> retrievedDocuments,
            String answerText
    );
}
