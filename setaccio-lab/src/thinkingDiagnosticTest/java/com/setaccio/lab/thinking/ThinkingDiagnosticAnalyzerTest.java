package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;

import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingDiagnosticAnalyzerTest {

    private final LocalFactCheckFixtureCatalog catalog = ThinkingDiagnosticTestSupport.catalog();
    private final ThinkingDiagnosticAnalyzer analyzer = new ThinkingDiagnosticAnalyzer(catalog);

    @Test
    void acceptsACompleteRunAndAggregatesEachArmSeparately() {
        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(result());

        assertThat(analysis.valid()).isTrue();
        assertThat(analysis.armSummaries()).hasSize(ThinkingDiagnosticProtocol.ARMS.size());
        ThinkingDiagnosticAnalyzer.ArmSummary enabled = analysis.armSummaries().stream()
                .filter(arm -> arm.armId().equals("subject-thinking-enabled-64"))
                .findFirst().orElseThrow();
        assertThat(enabled.rowCount()).isEqualTo(LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(enabled.rowsWithThinking()).isEqualTo(LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(enabled.rowsWithContent()).isZero();
        assertThat(enabled.rowsAtBudget()).isEqualTo(LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(enabled.outcomeCounts())
                .containsEntry(ThinkingDiagnosticOutcome.EMPTY_CONTENT_WITH_THINKING,
                        LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
        assertThat(enabled.finishReasonCounts())
                .containsEntry("length", LocalFactCheckFixtureCatalog.FIXTURE_COUNT);
    }

    @Test
    void rejectsAnIncompleteRun() {
        ThinkingDiagnosticResult complete = result();
        List<ThinkingDiagnosticRow> truncated = new ArrayList<>(complete.rows());
        truncated.removeLast();

        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(withRows(complete, truncated));

        assertThat(analysis.valid()).isFalse();
        assertThat(String.join(" ", analysis.integrityFailures()))
                .contains("must retain exactly " + ThinkingDiagnosticProtocol.ROW_COUNT + " rows");
    }

    @Test
    void rejectsARowThatDriftedFromItsLockedSchedulePosition() {
        ThinkingDiagnosticResult complete = result();
        List<ThinkingDiagnosticRow> reordered = new ArrayList<>(complete.rows());
        ThinkingDiagnosticRow first = reordered.get(0);
        reordered.set(0, reordered.get(1));
        reordered.set(1, first);

        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(withRows(complete, reordered));

        assertThat(analysis.valid()).isFalse();
        assertThat(String.join(" ", analysis.integrityFailures()))
                .contains("does not match its locked schedule position");
    }

    @Test
    void rejectsDriftedTopLevelProtocolAndPromptIdentity() {
        ThinkingDiagnosticResult complete = result();
        ThinkingDiagnosticResult drifted = new ThinkingDiagnosticResult(
                complete.protocolVersion(), "other-provider", complete.endpointCategory(),
                complete.executionStrategy(), complete.pullModelStrategy(), complete.temperature(),
                complete.seed(), complete.maxAttempts(), complete.requestTimeoutMillis(), complete.arms(),
                complete.modelIdentities(), complete.ollamaVersion(), "other-prompt",
                complete.promptVersion(), complete.promptSha256(), complete.fixtureCatalogId(),
                complete.fixtureCatalogVersion(), complete.fixtureCatalogSha256(),
                complete.orderedSchedule(), complete.rows());

        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(drifted);

        assertThat(analysis.valid()).isFalse();
        assertThat(String.join(" ", analysis.integrityFailures()))
                .contains("settings do not match the locked protocol")
                .contains("prompt identity does not match the tracked prompt");
    }

    @Test
    void rejectsFixtureHashDriftAndIncoherentOutcome() {
        ThinkingDiagnosticResult complete = result();
        List<ThinkingDiagnosticRow> rows = new ArrayList<>(complete.rows());
        ThinkingDiagnosticRow first = rows.getFirst();
        ThinkingDiagnosticRow second = rows.get(1);
        rows.set(0, copyRow(
                first,
                "f".repeat(64),
                first.claimBlake3(),
                ThinkingDiagnosticOutcome.CONTENT_WITHOUT_THINKING,
                0));
        rows.set(1, copyRow(
                second,
                second.documentBlake3(),
                "e".repeat(64),
                second.outcome(),
                second.attemptCount()));

        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(withRows(complete, rows));

        assertThat(analysis.valid()).isFalse();
        assertThat(String.join(" ", analysis.integrityFailures()))
                .contains("fixture identity drifted from the tracked catalog")
                .contains("does not retain the locked one-attempt policy")
                .contains("outcome does not match its retained response fields");
    }

    @Test
    void rejectsAFailedInvocationWithoutARecordedError() {
        ThinkingDiagnosticResult complete = result();
        List<ThinkingDiagnosticRow> rows = new ArrayList<>(complete.rows());
        rows.set(0, failedRowWithoutError(rows.getFirst()));

        ThinkingDiagnosticAnalyzer.Analysis analysis = analyzer.analyze(withRows(complete, rows));

        assertThat(analysis.valid()).isFalse();
        assertThat(String.join(" ", analysis.integrityFailures()))
                .contains("has an incoherent failed-invocation outcome");
    }

    private ThinkingDiagnosticResult result() {
        return new ThinkingDiagnosticExecutor(
                settings -> new ThinkingDiagnosticTestSupport.PolicyAwareChatModel(),
                ThinkingDiagnosticTestSupport.prompt())
                .execute(catalog, ThinkingDiagnosticTestSupport.identities(), "0.33.2");
    }

    private static ThinkingDiagnosticResult withRows(
            ThinkingDiagnosticResult source,
            List<ThinkingDiagnosticRow> rows
    ) {
        return new ThinkingDiagnosticResult(
                source.protocolVersion(), source.provider(), source.endpointCategory(),
                source.executionStrategy(), source.pullModelStrategy(), source.temperature(),
                source.seed(), source.maxAttempts(), source.requestTimeoutMillis(), source.arms(),
                source.modelIdentities(), source.ollamaVersion(), source.promptId(),
                source.promptVersion(), source.promptSha256(), source.fixtureCatalogId(),
                source.fixtureCatalogVersion(), source.fixtureCatalogSha256(),
                source.orderedSchedule(), rows);
    }

    private static ThinkingDiagnosticRow copyRow(
            ThinkingDiagnosticRow source,
            String documentBlake3,
            String claimBlake3,
            ThinkingDiagnosticOutcome outcome,
            int attemptCount
    ) {
        return new ThinkingDiagnosticRow(
                source.sequence(), source.armId(), source.modelRole(), source.requestedModel(),
                source.requestedReasoningPolicy(), source.reasoningPolicySupport(),
                source.modelAdvertisesThinking(), source.maxOutputTokens(), source.seed(),
                source.fixtureId(), source.pairId(), source.expectedVerdict(), documentBlake3,
                claimBlake3, source.invocationSucceeded(), source.content(), source.thinking(),
                source.thinkingPresence(), source.finishReason(), source.evaluatedOutputTokens(),
                source.promptTokens(), source.totalTokens(), source.normalizedJudgeVerdict(),
                source.expectedVerdictMatched(), outcome, source.latencyMillis(), attemptCount,
                source.error());
    }

    private static ThinkingDiagnosticRow failedRowWithoutError(ThinkingDiagnosticRow source) {
        return new ThinkingDiagnosticRow(
                source.sequence(), source.armId(), source.modelRole(), source.requestedModel(),
                source.requestedReasoningPolicy(), source.reasoningPolicySupport(),
                source.modelAdvertisesThinking(), source.maxOutputTokens(), source.seed(),
                source.fixtureId(), source.pairId(), source.expectedVerdict(), source.documentBlake3(),
                source.claimBlake3(), false, null, null,
                com.setaccio.lab.chat.ChatThinkingPresence.UNAVAILABLE, null, null, null, null,
                null, null, ThinkingDiagnosticOutcome.PROVIDER_FAILURE, source.latencyMillis(),
                ThinkingDiagnosticProtocol.MAX_ATTEMPTS, " ");
    }
}
