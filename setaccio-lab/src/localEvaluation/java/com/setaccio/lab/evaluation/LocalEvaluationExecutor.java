package com.setaccio.lab.evaluation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class LocalEvaluationExecutor {

    private final Clock clock;

    LocalEvaluationExecutor() {
        this(Clock.systemUTC());
    }

    LocalEvaluationExecutor(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    LocalEvaluationResult execute(LocalEvaluationPreflight.Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared preflight must not be null");
        }
        LocalEvaluationContract contract = prepared.contract();
        List<LocalEvaluationScheduleEntry> schedule = LocalEvaluationProtocol.schedule(contract.catalog());
        List<LocalEvaluationRow> rows = new ArrayList<>(schedule.size());
        Instant startedAt = clock.instant();
        for (LocalEvaluationScheduleEntry entry : schedule) {
            LocalFactCheckFixture fixture = contract.catalog().require(entry.fixtureId());
            LocalFactCheckJudgeSettings judgeSettings = prepared.settings()
                    .judgeSettingsFor(entry.repetition());
            LocalFactCheckJudgeResult result = prepared.session()
                    .evaluate(fixture, judgeSettings, contract.prompt());
            rows.add(LocalEvaluationRow.from(entry, result));
        }
        Instant finishedAt = clock.instant();
        return LocalEvaluationProtocol.result(
                startedAt,
                finishedAt,
                prepared.settings(),
                prepared.modelIdentity(),
                rows,
                contract.prompt(),
                contract.catalog(),
                contract.review());
    }
}
