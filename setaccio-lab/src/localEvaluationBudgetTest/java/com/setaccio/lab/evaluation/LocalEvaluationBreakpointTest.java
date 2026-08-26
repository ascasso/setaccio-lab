package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvaluationBreakpointTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void locksThePreRegisteredFiveArmProtocol() {
        assertThat(LocalEvaluationBreakpointProtocol.VERSION).isOne();
        assertThat(LocalEvaluationBreakpointProtocol.MAX_TOKENS).containsExactly(64, 96, 128, 192, 256);
        List<LocalEvaluationRunSettings> settings = LocalEvaluationBreakpointProtocol.MAX_TOKENS.stream()
                .map(tokens -> LocalEvaluationBreakpointProtocol.settings("judge-model", tokens))
                .toList();

        LocalEvaluationBreakpointProtocol.requireStudySettings(settings);

        assertThatThrownBy(() -> LocalEvaluationBreakpointProtocol.settings("judge-model", 160))
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("64, 96, 128, 192, or 256");
    }

    @Test
    void acceptsOnlyAllFiveRunnerOutputDirectories() {
        LocalEvaluationBreakpointRunner.Arguments parsed = LocalEvaluationBreakpointRunner.Arguments.parse(new String[] {
                "--judge-model", "judge-model", "--output-dir-256", "build/evaluation-matrix/256",
                "--output-dir-128", "build/evaluation-matrix/128", "--ollama-base-url", "http://localhost:11434",
                "--output-dir-64", "build/evaluation-matrix/64", "--output-dir-192", "build/evaluation-matrix/192",
                "--output-dir-96", "build/evaluation-matrix/96"
        });

        assertThat(parsed.outputDirectories()).containsEntry(128, "build/evaluation-matrix/128");
        assertThat(parsed.outputDirectories()).hasSize(5);
        assertThatThrownBy(() -> LocalEvaluationBreakpointRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434", "--judge-model", "judge-model",
                "--output-dir-64", "build/evaluation-matrix/64", "--output-dir-96", "build/evaluation-matrix/96"
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preflightsAllFiveArmsBeforeAllocatingAnyOutput() {
        Map<Integer, String> outputs = new LinkedHashMap<>();
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            outputs.put(tokens, "build/evaluation-matrix/2026-08-26-breakpoint-" + tokens);
        }
        LocalEvaluationBreakpointPreflight.Prepared prepared = new LocalEvaluationBreakpointPreflight().prepare(
                new LocalEvaluationBreakpointPreflight.Input(
                        temporaryDirectory, "http://localhost:11434", "judge-model", outputs),
                () -> LocalEvaluationContract.load(LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER),
                project -> cleanBaseline(),
                (baseUrl, timeout) -> {
                    assertThat(baseUrl).isEqualTo("http://localhost:11434");
                    assertThat(timeout).isEqualTo(Duration.ofMinutes(2));
                    return new LocalEvaluationPreflight.JudgeSession() {
                        @Override
                        public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
                            return LocalEvaluationBudgetTestFixtures.MODEL_IDENTITY;
                        }

                        @Override
                        public LocalFactCheckJudgeResult evaluate(
                                LocalFactCheckFixture fixture,
                                LocalFactCheckJudgeSettings settings,
                                LocalFactCheckPromptDefinition prompt
                        ) {
                            throw new AssertionError("Breakpoint preflight must not invoke a provider");
                        }
                    };
                });

        assertThat(prepared.settings().keySet()).containsExactly(64, 96, 128, 192, 256);
        assertThat(prepared.outputDirectories().values()).allMatch(path -> !Files.exists(path));
    }

    @Test
    void writesVerifiesReanalyzesAndReportsTheFiveFreshArms() throws Exception {
        LocalEvaluationBreakpointEvidence evidence = new LocalEvaluationBreakpointEvidence(
                LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
                LocalEvaluationBudgetTestFixtures.PROMPT,
                LocalEvaluationBudgetTestFixtures.CATALOG,
                LocalEvaluationBudgetTestFixtures.REVIEW);
        Map<Integer, Path> directories = directories("valid");
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            evidence.writeArm(directories.get(tokens), result(tokens), cleanBaseline());
        }

        assertThat(evidence.verifyStudy(directories).valid()).isTrue();
        Path summary = directories.get(128).resolve(LocalEvaluationEvidence.SUMMARY_FILENAME);
        String expected = Files.readString(summary);
        Files.delete(summary);
        assertThat(evidence.reanalyzeStudy(directories).valid()).isTrue();
        assertThat(Files.readString(summary)).isEqualTo(expected);

        LocalEvaluationBreakpointComparison.ComparisonResult comparison = new LocalEvaluationBreakpointComparison(
                LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
                LocalEvaluationBudgetTestFixtures.PROMPT,
                LocalEvaluationBudgetTestFixtures.CATALOG,
                LocalEvaluationBudgetTestFixtures.REVIEW).compare(directories);
        assertThat(comparison.report())
                .contains("# Offline Fact-Check Output-Budget Breakpoint Study")
                .contains("| Metric | 64 tokens | 96 tokens | 128 tokens | 192 tokens | 256 tokens |")
                .contains("| Valid normalized verdict | 12/12 | 12/12 | 12/12 | 12/12 | 12/12 |")
                .doesNotContain("response-");
    }

    @Test
    void rejectsBaselineDriftBeforeFiveWayComparison() throws Exception {
        LocalEvaluationBreakpointEvidence evidence = new LocalEvaluationBreakpointEvidence(
                LocalEvaluationBudgetTestFixtures.OBJECT_MAPPER,
                LocalEvaluationBudgetTestFixtures.PROMPT,
                LocalEvaluationBudgetTestFixtures.CATALOG,
                LocalEvaluationBudgetTestFixtures.REVIEW);
        Map<Integer, Path> directories = directories("drift");
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            evidence.writeArm(directories.get(tokens), result(tokens), new EvidenceCodeBaseline(
                    tokens == 192 ? "c".repeat(40) : "a".repeat(40), false));
        }

        LocalEvaluationBreakpointEvidence.OfflineStudyResult result = evidence.verifyStudy(directories);
        assertThat(result.valid()).isFalse();
        assertThat(result.failures()).anyMatch(failure -> failure.contains("Git code baseline differs at 192 tokens"));
    }

    private Map<Integer, Path> directories(String name) throws Exception {
        Map<Integer, Path> result = new LinkedHashMap<>();
        for (int tokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            result.put(tokens, Files.createDirectory(temporaryDirectory.resolve(name + "-" + tokens)));
        }
        return Map.copyOf(result);
    }

    private static EvidenceCodeBaseline cleanBaseline() {
        return new EvidenceCodeBaseline("a".repeat(40), false);
    }

    private static LocalEvaluationResult result(int maxTokens) {
        LocalEvaluationResult base = LocalEvaluationBudgetTestFixtures.result(64);
        LocalEvaluationRunSettings settings = LocalEvaluationBreakpointProtocol.settings("judge-model", maxTokens);
        List<LocalEvaluationRow> rows = new ArrayList<>();
        for (LocalEvaluationRow row : base.rows()) {
            rows.add(new LocalEvaluationRow(
                    row.sequence(), row.repetition(), row.seed(), row.fixtureId(), row.pairId(), row.documentBlake3(),
                    row.claimBlake3(), row.expectedVerdict(), settings.judgeSettingsFor(row.repetition()),
                    row.invocationSucceeded(), row.springEvaluatorPassed(), row.normalizedJudgeVerdict(),
                    row.expectedVerdictMatched(), row.diagnosticCategory(), row.rawResponse(), row.responseMetadata(),
                    row.promptTokens(), row.completionTokens(), row.totalTokens(), row.latencyMillis(), row.attemptCount(),
                    row.error()));
        }
        return new LocalEvaluationResult(
                base.protocolVersion(), base.suite(), base.provider(), base.endpointCategory(), base.startedAt(),
                base.finishedAt(), base.executionStrategy(), base.pullModelStrategy(), settings, base.judgeModelIdentity(),
                base.promptId(), base.promptVersion(), base.promptSha256(), base.fixtureCatalogId(),
                base.fixtureCatalogVersion(), base.fixtureCatalogSha256(), base.fixtureReviewId(),
                base.fixtureReviewVersion(), base.fixtureReviewSha256(), base.orderedSchedule(), rows);
    }
}
