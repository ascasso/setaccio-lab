package com.setaccio.lab.thinking;

import com.setaccio.lab.evaluation.LocalFactCheckFixture;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic, provider-free integrity checking and aggregation over one saved suite. */
public final class ThinkingDiagnosticAnalyzer {

    private final LocalFactCheckFixtureCatalog catalog;

    public ThinkingDiagnosticAnalyzer(LocalFactCheckFixtureCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    public Analysis analyze(ThinkingDiagnosticResult result) {
        List<String> failures = new ArrayList<>();
        if (result == null) {
            failures.add("Thinking diagnostic result is missing.");
            return new Analysis(List.copyOf(failures), List.of());
        }

        List<ThinkingDiagnosticScheduleEntry> expectedSchedule =
                ThinkingDiagnosticProtocol.schedule(catalog);
        if (!expectedSchedule.equals(result.orderedSchedule())) {
            failures.add("Retained schedule does not match the locked diagnostic schedule.");
        }
        if (!ThinkingDiagnosticProtocol.ARMS.equals(result.arms())) {
            failures.add("Retained arms do not match the locked diagnostic arms.");
        }
        if (result.rows().size() != ThinkingDiagnosticProtocol.ROW_COUNT) {
            failures.add("Diagnostic must retain exactly "
                    + ThinkingDiagnosticProtocol.ROW_COUNT + " rows.");
        }
        if (!catalog.id().equals(result.fixtureCatalogId())
                || !catalog.version().equals(result.fixtureCatalogVersion())
                || !catalog.sha256().equals(result.fixtureCatalogSha256())) {
            failures.add("Retained fixture catalog identity does not match the tracked catalog.");
        }

        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities =
                new LinkedHashMap<>();
        for (ThinkingDiagnosticModelIdentity identity : result.modelIdentities()) {
            if (identities.put(identity.role(), identity) != null) {
                failures.add("Retained model identities contain a duplicate role.");
            }
        }
        for (ThinkingDiagnosticModelRole role : ThinkingDiagnosticModelRole.values()) {
            if (!identities.containsKey(role)) {
                failures.add("Retained model identities are missing role " + role + ".");
            }
        }

        LinkedHashSet<Integer> sequences = new LinkedHashSet<>();
        int position = 0;
        for (ThinkingDiagnosticRow row : result.rows()) {
            if (!sequences.add(row.sequence())) {
                failures.add("Duplicate row sequence " + row.sequence() + ".");
            }
            if (position >= expectedSchedule.size()) {
                position++;
                continue;
            }
            ThinkingDiagnosticScheduleEntry expected = expectedSchedule.get(position);
            if (row.sequence() != expected.sequence()
                    || !row.armId().equals(expected.armId())
                    || !row.fixtureId().equals(expected.fixtureId())
                    || !row.pairId().equals(expected.pairId())
                    || row.expectedVerdict() != expected.expectedVerdict()
                    || row.seed() != expected.seed()) {
                failures.add("Row " + position + " does not match its locked schedule position.");
            }
            ThinkingDiagnosticArm arm = ThinkingDiagnosticProtocol.ARMS.stream()
                    .filter(candidate -> candidate.armId().equals(expected.armId()))
                    .findFirst()
                    .orElse(null);
            if (arm != null) {
                if (row.maxOutputTokens() != arm.maxOutputTokens()
                        || row.requestedReasoningPolicy() != arm.reasoningPolicy()
                        || row.modelRole() != arm.modelRole()) {
                    failures.add("Row " + position + " does not match its arm settings.");
                }
                ThinkingDiagnosticModelIdentity identity = identities.get(arm.modelRole());
                if (identity != null
                        && (!identity.requestedModel().equals(row.requestedModel())
                        || identity.advertisesThinking() != row.modelAdvertisesThinking())) {
                    failures.add("Row " + position + " does not match its resolved model identity.");
                }
            }
            LocalFactCheckFixture fixture = fixture(expected.fixtureId());
            if (fixture != null && fixture.expectedVerdict() != row.expectedVerdict()) {
                failures.add("Row " + position + " expected verdict drifted from the tracked catalog.");
            }
            position++;
        }

        return new Analysis(List.copyOf(failures), armSummaries(result));
    }

    private LocalFactCheckFixture fixture(String fixtureId) {
        try {
            return catalog.require(fixtureId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<ArmSummary> armSummaries(ThinkingDiagnosticResult result) {
        List<ArmSummary> summaries = new ArrayList<>();
        for (ThinkingDiagnosticArm arm : result.arms()) {
            List<ThinkingDiagnosticRow> rows = result.rows().stream()
                    .filter(row -> row.armId().equals(arm.armId()))
                    .toList();
            Map<ThinkingDiagnosticOutcome, Integer> outcomes = new LinkedHashMap<>();
            for (ThinkingDiagnosticOutcome outcome : ThinkingDiagnosticOutcome.values()) {
                int count = (int) rows.stream().filter(row -> row.outcome() == outcome).count();
                if (count > 0) {
                    outcomes.put(outcome, count);
                }
            }
            Map<String, Integer> finishReasons = new LinkedHashMap<>();
            rows.stream()
                    .map(row -> row.finishReason() == null ? "unavailable" : row.finishReason())
                    .sorted()
                    .forEach(reason -> finishReasons.merge(reason, 1, Integer::sum));
            Integer minTokens = rows.stream()
                    .map(ThinkingDiagnosticRow::evaluatedOutputTokens)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(null);
            Integer maxTokens = rows.stream()
                    .map(ThinkingDiagnosticRow::evaluatedOutputTokens)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null);
            summaries.add(new ArmSummary(
                    arm.armId(),
                    arm.modelRole(),
                    arm.reasoningPolicy().name(),
                    arm.maxOutputTokens(),
                    rows.size(),
                    (int) rows.stream().filter(ThinkingDiagnosticRow::contentPresent).count(),
                    (int) rows.stream().filter(row -> row.thinking() != null).count(),
                    (int) rows.stream().filter(ThinkingDiagnosticRow::budgetSaturated).count(),
                    minTokens,
                    maxTokens,
                    outcomes,
                    finishReasons));
        }
        return List.copyOf(summaries);
    }

    /** Deterministic per-arm aggregate. Carries counts only, never recorded text. */
    public record ArmSummary(
            String armId,
            ThinkingDiagnosticModelRole modelRole,
            String reasoningPolicy,
            int maxOutputTokens,
            int rowCount,
            int rowsWithContent,
            int rowsWithThinking,
            int rowsAtBudget,
            Integer minEvaluatedOutputTokens,
            Integer maxEvaluatedOutputTokens,
            Map<ThinkingDiagnosticOutcome, Integer> outcomeCounts,
            Map<String, Integer> finishReasonCounts
    ) {}

    public record Analysis(List<String> integrityFailures, List<ArmSummary> armSummaries) {
        public boolean valid() {
            return integrityFailures.isEmpty();
        }
    }
}
