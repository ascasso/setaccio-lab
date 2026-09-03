package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvaluationBudgetPreflightTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final String COMMIT = "a".repeat(40);
    private static final EvidenceCodeBaseline CLEAN = new EvidenceCodeBaseline(COMMIT, false);
    private static final LocalEvaluationModelIdentity MODEL = new LocalEvaluationModelIdentity(
            "judge-model",
            "judge-model:latest",
            "b".repeat(64));

    @TempDir
    Path projectDirectory;

    @Test
    void preflightsBothFreshArmsWithoutAllocatingOrChangingTheOriginalContract() {
        TrackingSession session = new TrackingSession(MODEL);
        LocalEvaluationBudgetPreflight.Prepared prepared = prepare(
                new Repository(List.of(CLEAN, CLEAN, CLEAN, CLEAN, CLEAN)),
                session);

        assertThat(prepared.outputDirectory64()).isEqualTo(projectDirectory.resolve(
                "local/evidence/evaluation-matrix/2026-08-25-budget-64").toAbsolutePath().normalize());
        assertThat(prepared.outputDirectory256()).isEqualTo(projectDirectory.resolve(
                "local/evidence/evaluation-matrix/2026-08-25-budget-256").toAbsolutePath().normalize());
        assertThat(prepared.budget64().maxTokens()).isEqualTo(64);
        assertThat(prepared.budget256().maxTokens()).isEqualTo(256);
        assertThat(prepared.budget64().timeoutMillis()).isEqualTo(Duration.ofMinutes(2).toMillis());
        assertThat(prepared.contract().prompt().id()).isEqualTo(LocalFactCheckPromptDefinition.ID);
        assertThat(prepared.contract().catalog().id()).isEqualTo(LocalFactCheckFixtureCatalog.ID);
        assertThat(prepared.contract().review().id()).isEqualTo(LocalFactCheckFixtureReview.ID);
        assertThat(session.installedChecks).isOne();
        assertThat(Files.exists(prepared.outputDirectory64())).isFalse();
        assertThat(Files.exists(prepared.outputDirectory256())).isFalse();
    }

    @Test
    void allocatesBothDirectoriesOnlyAfterRepeatedCleanBaselineChecks() {
        LocalEvaluationBudgetPreflight.Prepared prepared = prepare(
                new Repository(List.of(CLEAN, CLEAN, CLEAN, CLEAN, CLEAN)),
                new TrackingSession(MODEL));

        LocalEvaluationBudgetPreflight.AllocatedOutputs outputs = prepared.allocateBoth();

        assertThat(outputs.budget64()).isEqualTo(prepared.outputDirectory64());
        assertThat(outputs.budget256()).isEqualTo(prepared.outputDirectory256());
        assertThat(Files.isDirectory(outputs.budget64())).isTrue();
        assertThat(Files.isDirectory(outputs.budget256())).isTrue();
    }

    @Test
    void rejectsDirtyBaselineBeforeCreatingAProviderSession() {
        TrackingSession session = new TrackingSession(MODEL);

        assertThatThrownBy(() -> prepare(new Repository(List.of(
                new EvidenceCodeBaseline(COMMIT, true))), session))
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("clean Git commit");
        assertThat(session.installedChecks).isZero();
        assertThat(Files.exists(projectDirectory.resolve("build"))).isFalse();
    }

    @Test
    void stopsWhenCommitDriftsBeforeTheSecondArm() {
        LocalEvaluationBudgetPreflight.Prepared prepared = prepare(
                new Repository(List.of(
                        CLEAN,
                        new EvidenceCodeBaseline("c".repeat(40), false))),
                new TrackingSession(MODEL));

        assertThatThrownBy(prepared::requireRepositoryUnchanged)
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
    }

    @Test
    void stopsWhenTheInstalledJudgeDigestChangesBetweenArmChecks() {
        TrackingSession session = new TrackingSession(MODEL, new LocalEvaluationModelIdentity(
                "judge-model",
                "judge-model:latest",
                "d".repeat(64)));
        LocalEvaluationBudgetPreflight.Prepared prepared = prepare(
                new Repository(List.of(CLEAN, CLEAN)),
                session);

        assertThatThrownBy(prepared::requireModelIdentityUnchanged)
                .isInstanceOf(LocalEvaluationBudgetProtocolIntegrityException.class)
                .hasMessageContaining("model identity drifted");
    }

    private LocalEvaluationBudgetPreflight.Prepared prepare(
            Repository repository,
            TrackingSession session
    ) {
        return new LocalEvaluationBudgetPreflight().prepare(
                new LocalEvaluationBudgetPreflight.Input(
                        projectDirectory,
                        "http://localhost:11434",
                        "judge-model",
                        "local/evidence/evaluation-matrix/2026-08-25-budget-64",
                        "local/evidence/evaluation-matrix/2026-08-25-budget-256"),
                () -> LocalEvaluationContract.load(OBJECT_MAPPER),
                repository,
                (baseUrl, timeout) -> {
                    assertThat(baseUrl).isEqualTo("http://localhost:11434");
                    assertThat(timeout).isEqualTo(Duration.ofMinutes(2));
                    return session;
                });
    }

    private static final class Repository implements LocalEvaluationBudgetPreflight.RepositoryState {

        private final Deque<EvidenceCodeBaseline> baselines;
        private EvidenceCodeBaseline last;

        private Repository(List<EvidenceCodeBaseline> baselines) {
            this.baselines = new ArrayDeque<>(baselines);
            this.last = baselines.getLast();
        }

        @Override
        public EvidenceCodeBaseline capture(Path projectDirectory) {
            if (baselines.isEmpty()) {
                return last;
            }
            last = baselines.removeFirst();
            return last;
        }
    }

    private static final class TrackingSession implements LocalEvaluationPreflight.JudgeSession {

        private final Deque<LocalEvaluationModelIdentity> identities = new ArrayDeque<>();
        private int installedChecks;

        private TrackingSession(LocalEvaluationModelIdentity... identities) {
            this.identities.addAll(List.of(identities));
        }

        @Override
        public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
            installedChecks++;
            return identities.isEmpty() ? MODEL : identities.removeFirst();
        }

        @Override
        public LocalFactCheckJudgeResult evaluate(
                LocalFactCheckFixture fixture,
                LocalFactCheckJudgeSettings settings,
                LocalFactCheckPromptDefinition prompt
        ) {
            throw new AssertionError("F1 preflight tests must not invoke a provider");
        }
    }
}
