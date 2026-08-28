package com.setaccio.lab.retrieval;

/** One R6 evaluator observation retaining its exact R5 answer and source context. */
public record RetrievalRelevancyRow(
        int sequence,
        RetrievalAnswerRow answer,
        RetrievalRelevancyDeterministicExpectation deterministicExpectation,
        RetrievalRelevancyModelRelationship modelRelationship,
        RetrievalRelevancyEvaluatorOutcome evaluatorOutcome,
        RetrievalRelevancyHumanSupportJudgment humanSupportJudgment,
        RetrievalRelevancyAnswerCorrectness answerCorrectness
) {

    public RetrievalRelevancyRow {
        if (sequence < 1 || answer == null || deterministicExpectation == null || modelRelationship == null
                || evaluatorOutcome == null || humanSupportJudgment == null || answerCorrectness == null) {
            throw new IllegalArgumentException("retrieval relevancy row fields must not be null and sequence must be positive");
        }
    }
}
