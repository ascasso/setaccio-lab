package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes at most one evaluator request for each preserved R5 row. */
final class RetrievalRelevancyExecutor {

    private final RetrievalRelevancyEvaluator evaluator;

    RetrievalRelevancyExecutor(RetrievalRelevancyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    RetrievalRelevancyResult execute(
            RetrievalRelevancySourceEvidence sourceEvidence,
            RetrievalAnswerResult answerEvidence,
            RetrievalRelevancyPromptDefinition promptDefinition,
            RetrievalRelevancyModelIdentity modelIdentity,
            RetrievalRelevancyRunSettings runSettings
    ) {
        if (sourceEvidence == null || answerEvidence == null || promptDefinition == null || modelIdentity == null
                || runSettings == null) {
            throw new IllegalArgumentException("R6 execution fields must not be null");
        }
        Instant startedAt = Instant.now();
        RetrievalRelevancyPromptContract prompt = promptDefinition.contract();
        RetrievalRelevancyModelRelationship relationship = relationship(answerEvidence.modelIdentity(), modelIdentity);
        List<RetrievalRelevancyRow> rows = new ArrayList<>(answerEvidence.rows().size());
        for (RetrievalAnswerRow answer : answerEvidence.rows()) {
            RetrievalEvaluationRow retrieval = answer.retrieval();
            RetrievalRelevancyEvaluatorOutcome outcome;
            if (retrieval.retrievedDocuments().isEmpty()) {
                outcome = RetrievalRelevancyEvaluatorBoundary.notAttempted(
                        modelIdentity, prompt, RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT);
            } else if (!answer.invocation().successful()) {
                outcome = RetrievalRelevancyEvaluatorBoundary.notAttempted(
                        modelIdentity, prompt, RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_NO_ANSWER);
            } else {
                outcome = evaluator.evaluate(retrieval.query(), retrieval.retrievedDocuments(), answer.invocation().answerText());
            }
            rows.add(new RetrievalRelevancyRow(
                    answer.sequence(),
                    answer,
                    RetrievalRelevancyDeterministicExpectation.from(retrieval),
                    relationship,
                    outcome,
                    RetrievalRelevancyHumanSupportJudgment.NOT_REVIEWED,
                    RetrievalRelevancyAnswerCorrectness.NOT_ASSESSED));
        }
        return new RetrievalRelevancyResult(
                RetrievalRelevancyProtocol.VERSION,
                RetrievalRelevancyProtocol.SUITE,
                startedAt,
                Instant.now(),
                RetrievalRelevancyProtocol.EXECUTION_ENGINE,
                RetrievalRelevancyProtocol.EXECUTION_STRATEGY,
                sourceEvidence,
                answerEvidence,
                prompt,
                modelIdentity,
                runSettings,
                rows);
    }

    static RetrievalRelevancyModelRelationship relationship(
            RetrievalAnswerModelIdentity answerModel,
            RetrievalRelevancyModelIdentity evaluatorModel
    ) {
        return answerModel.providerId().equals(evaluatorModel.providerId())
                && answerModel.effectiveModel().equals(evaluatorModel.effectiveModel())
                && answerModel.digest().equals(evaluatorModel.digest())
                ? RetrievalRelevancyModelRelationship.SELF_EVALUATION
                : RetrievalRelevancyModelRelationship.SEPARATE_EVALUATOR;
    }
}
