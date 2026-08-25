package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LocalEvaluationBudgetTestFixtures {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    static final LocalFactCheckPromptDefinition PROMPT = new LocalFactCheckPromptDefinition();
    static final LocalFactCheckFixtureCatalog CATALOG = new LocalFactCheckFixtureCatalog(OBJECT_MAPPER);
    static final LocalFactCheckFixtureReview REVIEW = new LocalFactCheckFixtureReview(OBJECT_MAPPER, CATALOG);
    static final EvidenceCodeBaseline CLEAN_BASELINE = new EvidenceCodeBaseline("a".repeat(40), false);
    static final LocalEvaluationModelIdentity MODEL_IDENTITY = new LocalEvaluationModelIdentity(
            "judge-model",
            "judge-model:latest",
            "b".repeat(64));

    private LocalEvaluationBudgetTestFixtures() {}

    static LocalEvaluationResult result(int maxTokens) {
        LocalEvaluationRunSettings settings = LocalEvaluationBudgetProtocol.settings(
                "judge-model",
                maxTokens);
        List<LocalEvaluationRow> rows = new ArrayList<>();
        for (LocalEvaluationScheduleEntry entry : LocalEvaluationProtocol.schedule(CATALOG)) {
            boolean supported = entry.expectedVerdict() == LocalFactCheckExpectedVerdict.SUPPORTED;
            LocalFactCheckJudgeResult judgeResult = new LocalFactCheckJudgeResult(
                    entry.fixtureId(),
                    entry.expectedVerdict(),
                    settings.judgeSettingsFor(entry.repetition()),
                    true,
                    supported,
                    supported
                            ? LocalFactCheckJudgeVerdict.SUPPORTED
                            : LocalFactCheckJudgeVerdict.UNSUPPORTED,
                    true,
                    LocalFactCheckDiagnosticCategory.NONE,
                    supported ? "yes" : "no",
                    new LocalFactCheckJudgeResponseMetadata(
                            "response-" + entry.sequence(),
                            MODEL_IDENTITY.normalizedInstalledName(),
                            Map.of("fixture", entry.fixtureId())),
                    10,
                    2,
                    12,
                    entry.sequence(),
                    1,
                    null);
            rows.add(LocalEvaluationRow.from(entry, judgeResult));
        }
        Instant startedAt = Instant.parse("2026-08-25T10:00:00Z");
        return LocalEvaluationProtocol.result(
                startedAt,
                startedAt.plusSeconds(12),
                settings,
                MODEL_IDENTITY,
                rows,
                PROMPT,
                CATALOG,
                REVIEW);
    }
}
