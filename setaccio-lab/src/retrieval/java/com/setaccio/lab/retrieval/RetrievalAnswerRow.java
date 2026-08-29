package com.setaccio.lab.retrieval;

/** One sequential answer attempt retaining the exact R3 retrieval row that supplied its context. */
public record RetrievalAnswerRow(
        int sequence,
        RetrievalEvaluationRow retrieval,
        String renderedPrompt,
        RetrievalAnswerInvocationOutcome invocation,
        RetrievalAnswerReferenceAnalysis referenceAnalysis,
        boolean explicitAbstentionObserved,
        RetrievalAnswerSupportAssessment unsupportedAssertionAssessment
) {

    public RetrievalAnswerRow {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (retrieval == null || renderedPrompt == null || renderedPrompt.isBlank() || invocation == null
                || referenceAnalysis == null || unsupportedAssertionAssessment == null) {
            throw new IllegalArgumentException("retrieval answer row fields must not be null or blank");
        }
    }
}
