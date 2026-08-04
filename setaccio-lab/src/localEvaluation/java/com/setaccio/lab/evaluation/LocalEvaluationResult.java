package com.setaccio.lab.evaluation;

import java.time.Instant;
import java.util.List;

public record LocalEvaluationResult(
        int protocolVersion,
        String suite,
        String provider,
        String endpointCategory,
        Instant startedAt,
        Instant finishedAt,
        String executionStrategy,
        String pullModelStrategy,
        LocalEvaluationRunSettings runSettings,
        LocalEvaluationModelIdentity judgeModelIdentity,
        String promptId,
        String promptVersion,
        String promptSha256,
        String fixtureCatalogId,
        String fixtureCatalogVersion,
        String fixtureCatalogSha256,
        String fixtureReviewId,
        String fixtureReviewVersion,
        String fixtureReviewSha256,
        List<LocalEvaluationScheduleEntry> orderedSchedule,
        List<LocalEvaluationRow> rows
) {

    public LocalEvaluationResult {
        orderedSchedule = orderedSchedule == null ? List.of() : List.copyOf(orderedSchedule);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
