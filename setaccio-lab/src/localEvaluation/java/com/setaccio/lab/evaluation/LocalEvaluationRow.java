package com.setaccio.lab.evaluation;

public record LocalEvaluationRow(
        int sequence,
        int repetition,
        int seed,
        String fixtureId,
        String pairId,
        String documentBlake3,
        String claimBlake3,
        LocalFactCheckExpectedVerdict expectedVerdict,
        LocalFactCheckJudgeSettings judgeSettings,
        boolean invocationSucceeded,
        Boolean springEvaluatorPassed,
        LocalFactCheckJudgeVerdict normalizedJudgeVerdict,
        Boolean expectedVerdictMatched,
        LocalFactCheckDiagnosticCategory diagnosticCategory,
        String rawResponse,
        LocalFactCheckJudgeResponseMetadata responseMetadata,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        String error
) {

    static LocalEvaluationRow from(
            LocalEvaluationScheduleEntry schedule,
            LocalFactCheckJudgeResult result
    ) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (!schedule.fixtureId().equals(result.fixtureId())
                || schedule.expectedVerdict() != result.expectedVerdict()
                || schedule.seed() != result.settings().seed()) {
            throw new IllegalArgumentException("Judge result does not match its scheduled fixture");
        }
        return new LocalEvaluationRow(
                schedule.sequence(),
                schedule.repetition(),
                schedule.seed(),
                schedule.fixtureId(),
                schedule.pairId(),
                schedule.documentBlake3(),
                schedule.claimBlake3(),
                schedule.expectedVerdict(),
                result.settings(),
                result.invocationSucceeded(),
                result.springEvaluatorPassed(),
                result.normalizedJudgeVerdict(),
                result.expectedVerdictMatched(),
                result.diagnosticCategory(),
                result.rawResponse(),
                result.responseMetadata(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens(),
                result.latencyMillis(),
                result.attemptCount(),
                safeError(result.diagnosticCategory()));
    }

    static String safeError(LocalFactCheckDiagnosticCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case JUDGE_MODEL_UNAVAILABLE -> "Judge model was unavailable";
            case TIMEOUT -> "Judge invocation timed out";
            case PROVIDER_FAILURE -> "Judge provider invocation failed";
            case NONE, EMPTY_RESPONSE, MALFORMED_VERDICT, EXPECTATION_MISMATCH -> null;
        };
    }
}
