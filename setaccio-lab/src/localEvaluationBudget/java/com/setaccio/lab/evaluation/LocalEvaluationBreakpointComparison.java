package com.setaccio.lab.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Renders five-arm descriptive aggregates only after strict offline study verification. */
final class LocalEvaluationBreakpointComparison {

    private final LocalEvaluationBreakpointEvidence evidence;

    LocalEvaluationBreakpointComparison(
            ObjectMapper objectMapper,
            LocalFactCheckPromptDefinition prompt,
            LocalFactCheckFixtureCatalog catalog,
            LocalFactCheckFixtureReview review
    ) {
        evidence = new LocalEvaluationBreakpointEvidence(
                Objects.requireNonNull(objectMapper, "objectMapper must not be null"), prompt, catalog, review);
    }

    ComparisonResult compare(Map<Integer, Path> directories) {
        LocalEvaluationBreakpointEvidence.StudySnapshot study = evidence.loadVerifiedStudy(directories);
        Map<Integer, LocalEvaluationBudgetComparison.ArmMetrics> metrics = new LinkedHashMap<>();
        Map<Integer, String> runIds = new LinkedHashMap<>();
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            LocalEvaluationBreakpointEvidence.ArmSnapshot arm = study.arms().get(tokens);
            metrics.put(tokens, LocalEvaluationBudgetComparison.ArmMetrics.from(arm.result()));
            runIds.put(tokens, arm.manifest().runId());
        }
        LocalEvaluationBreakpointEvidence.ArmSnapshot canonical = study.arms().get(64);
        return new ComparisonResult(Map.copyOf(runIds), canonical.manifest().codeBaseline().gitCommit(),
                canonical.result().judgeModelIdentity().digest(), Map.copyOf(metrics),
                new LocalEvaluationBreakpointComparisonReport().render(runIds, canonical, metrics));
    }

    record ComparisonResult(
            Map<Integer, String> runIds,
            String sharedGitCommit,
            String judgeDigest,
            Map<Integer, LocalEvaluationBudgetComparison.ArmMetrics> metrics,
            String report
    ) {}
}
