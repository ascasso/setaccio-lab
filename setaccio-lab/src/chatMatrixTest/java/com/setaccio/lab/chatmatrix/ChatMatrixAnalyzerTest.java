package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMatrixAnalyzerTest {

    private final ChatMatrixAnalyzer analyzer = new ChatMatrixAnalyzer(ChatMatrixTestFixtures.CATALOG);

    @Test
    void acceptsTheLockedSuccessAndClassifiedDiagnosticSurfaces() {
        ChatMatrixAnalyzer.MatrixAnalysis success = analyzer.analyze(ChatMatrixTestFixtures.successfulResult());
        ChatMatrixAnalyzer.MatrixAnalysis diagnostic = analyzer.analyze(ChatMatrixTestFixtures.diagnosticResult());

        assertThat(success.valid()).isTrue();
        assertThat(success.totalRows()).isEqualTo(6);
        assertThat(success.successfulResponses()).isEqualTo(6);
        assertThat(success.completedInvocations()).isEqualTo(6);
        assertThat(success.rowsWithUsage()).isEqualTo(6);
        assertThat(success.minimumSuccessfulLatencyMillis()).isEqualTo(10);
        assertThat(success.maximumSuccessfulLatencyMillis()).isEqualTo(60);

        assertThat(diagnostic.valid()).isTrue();
        assertThat(diagnostic.successfulResponses()).isEqualTo(4);
        assertThat(diagnostic.completedInvocations()).isEqualTo(5);
        assertThat(diagnostic.rowsWithUsage()).isEqualTo(4);
    }

    @Test
    void rejectsProtocolCatalogModelScheduleAndRowDrift() {
        ChatMatrixResult source = ChatMatrixTestFixtures.successfulResult();

        ChatMatrixResult catalogDrift = ChatMatrixTestFixtures.copy(
                source,
                source.runSettings(),
                source.modelIdentity(),
                "b".repeat(64),
                source.orderedSchedule(),
                source.rows());
        assertThat(analyzer.analyze(catalogDrift).integrityFailures())
                .contains("Raw chat matrix prompt catalog identity drifted from the tracked contract.");

        OllamaChatModelIdentity modelDrift = new OllamaChatModelIdentity(
                "ollama", "model-a", "different:latest", "a".repeat(64));
        ChatMatrixResult wrongModel = ChatMatrixTestFixtures.copy(
                source,
                source.runSettings(),
                modelDrift,
                source.promptCatalogSha256(),
                source.orderedSchedule(),
                source.rows());
        assertThat(analyzer.analyze(wrongModel).integrityFailures())
                .contains("Raw chat matrix model identity does not match the requested installed model.");

        List<ChatMatrixScheduleEntry> reversed = new ArrayList<>(source.orderedSchedule());
        java.util.Collections.reverse(reversed);
        ChatMatrixResult scheduleDrift = ChatMatrixTestFixtures.copy(
                source,
                source.runSettings(),
                source.modelIdentity(),
                source.promptCatalogSha256(),
                reversed,
                source.rows());
        assertThat(analyzer.analyze(scheduleDrift).integrityFailures())
                .contains("Raw chat matrix ordered schedule drifted from the locked protocol.");

        ChatMatrixRow first = source.rows().getFirst();
        ChatMatrixRow retried = new ChatMatrixRow(
                first.sequence(),
                first.repetition(),
                first.seed(),
                first.promptId(),
                first.promptSha256(),
                first.generationSettings(),
                first.optionSupport(),
                first.invocationSucceeded(),
                first.rawResponse(),
                first.promptTokens(),
                first.completionTokens(),
                first.totalTokens(),
                first.latencyMillis(),
                2,
                first.failureCategory(),
                first.error());
        List<ChatMatrixRow> rows = new ArrayList<>(source.rows());
        rows.set(0, retried);
        ChatMatrixResult rowDrift = ChatMatrixTestFixtures.copy(
                source,
                source.runSettings(),
                source.modelIdentity(),
                source.promptCatalogSha256(),
                source.orderedSchedule(),
                rows);
        assertThat(analyzer.analyze(rowDrift).integrityFailures())
                .contains("Raw chat matrix row 1 must record exactly one attempt.");
    }

    @Test
    void rejectsMissingOrReplacementRows() {
        ChatMatrixResult source = ChatMatrixTestFixtures.successfulResult();
        ChatMatrixResult missing = ChatMatrixTestFixtures.resultWithRows(
                source.rows().subList(0, 5));

        assertThat(analyzer.analyze(missing).integrityFailures())
                .contains("Raw chat matrix must contain exactly six rows.");

        List<ChatMatrixRow> rows = new ArrayList<>(source.rows());
        rows.set(1, rows.get(0));
        ChatMatrixResult replacement = ChatMatrixTestFixtures.resultWithRows(rows);
        assertThat(analyzer.analyze(replacement).integrityFailures())
                .contains("Raw chat matrix row 2 does not match the locked schedule.");
    }
}
