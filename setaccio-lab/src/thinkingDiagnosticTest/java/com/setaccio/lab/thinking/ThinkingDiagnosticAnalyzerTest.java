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
}
