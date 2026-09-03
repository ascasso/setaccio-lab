package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.ollama.api.OllamaApi;

class LocalEvaluationPreflightTest {

    private static final String RUN = "local/evidence/evaluation-matrix/2026-08-03-a4-test";
    private static final LocalEvaluationModelIdentity MODEL = new LocalEvaluationModelIdentity(
            "judge-model",
            "judge-model:latest",
            "a".repeat(64));

    @TempDir
    Path projectDirectory;

    private final LocalEvaluationPreflight preflight = new LocalEvaluationPreflight();

    @Test
    void acceptsOnlyCompleteExplicitOptionsAndRejectsUnknownOrDuplicateArguments() {
        LocalEvaluationRunner.Arguments parsed = LocalEvaluationRunner.Arguments.parse(new String[] {
                "--judge-model", "judge-model",
                "--output-dir", RUN,
                "--ollama-base-url", "http://localhost:11434",
                "--timeout", "PT30S",
                "--max-tokens", "64"
        });

        assertThat(parsed).isEqualTo(new LocalEvaluationRunner.Arguments(
                "http://localhost:11434", "judge-model", "64", "PT30S", RUN));
        assertThatThrownBy(() -> LocalEvaluationRunner.Arguments.parse(new String[] {
                "--judge-model", "judge-model"
        })).hasMessageContaining("Expected --ollama-base-url");
        assertThatThrownBy(() -> LocalEvaluationRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--judge-model", "judge-model",
                "--max-tokens", "64",
                "--timeout", "PT30S",
                "--unsupported", RUN
        })).hasMessageContaining("Expected --ollama-base-url");
        assertThatThrownBy(() -> LocalEvaluationRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--judge-model", "judge-model",
                "--judge-model", "second-model",
                "--timeout", "PT30S",
                "--output-dir", RUN
        })).hasMessageContaining("Expected --ollama-base-url");
    }

    @Test
    void rejectsEveryMissingRequiredPreflightOptionBeforeCreatingOutput() {
        List<LocalEvaluationPreflight.Input> inputs = List.of(
                input(null, "judge-model", "64", "PT30S", RUN),
                input("http://localhost:11434", null, "64", "PT30S", RUN),
                input("http://localhost:11434", "judge-model", null, "PT30S", RUN),
                input("http://localhost:11434", "judge-model", "64", null, RUN),
                input("http://localhost:11434", "judge-model", "64", "PT30S", null));

        inputs.forEach(input -> assertThatThrownBy(() -> prepare(input, trackedContract(), session(MODEL)))
                .isInstanceOf(RuntimeException.class));
        assertThat(Files.exists(outputRoot())).isFalse();
    }

    @Test
    void rejectsNonLoopbackOrStructuredEndpointBeforeModelInventoryAndOutputAllocation() {
        TrackingSessionFactory sessions = new TrackingSessionFactory(session(MODEL));

        for (String endpoint : List.of(
                "https://example.com:11434",
                "http://localhost:11434/api",
                "http://user@localhost:11434",
                "http://localhost:11434?token=no")) {
            assertThatThrownBy(() -> preflight.prepare(
                    input(endpoint, "judge-model", "64", "PT30S", RUN),
                    this::trackedContract,
                    sessions))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("loopback HTTP URL");
        }
        assertThat(sessions.created).isZero();
        assertThat(Files.exists(outputRoot())).isFalse();
    }

    @Test
    void rejectsInvalidTokenTimeoutAndOutputSettingsBeforeOpeningJudgeSession() throws Exception {
        TrackingSessionFactory sessions = new TrackingSessionFactory(session(MODEL));
        for (String tokens : List.of("0", "32769", "none", "1.5")) {
            assertThatThrownBy(() -> preflight.prepare(
                    input("http://localhost:11434", "judge-model", tokens, "PT30S", RUN),
                    this::trackedContract,
                    sessions)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String timeout : List.of("PT0S", "-PT1S", "PT10M0.001S", "30s")) {
            assertThatThrownBy(() -> preflight.prepare(
                    input("http://localhost:11434", "judge-model", "64", timeout, RUN),
                    this::trackedContract,
                    sessions)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String output : List.of(
                "local/evidence/evaluation-matrix/no-date",
                "local/evidence/evaluation-matrix/2026-99-99-run",
                "local/evidence/evaluation-matrix/nested/2026-08-03-run",
                "../local/evidence/evaluation-matrix/2026-08-03-run")) {
            assertThatThrownBy(() -> preflight.prepare(
                    input("http://localhost:11434", "judge-model", "64", "PT30S", output),
                    this::trackedContract,
                    sessions)).isInstanceOf(IllegalArgumentException.class);
        }
        Path reused = projectDirectory.resolve(RUN);
        Files.createDirectories(reused);
        assertThatThrownBy(() -> prepare(
                input("http://localhost:11434", "judge-model", "64", "PT30S", RUN),
                trackedContract(),
                session(MODEL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThat(sessions.created).isZero();
    }

    @Test
    void rejectsAbsentUnconfirmedIncompleteAndDigestDriftedContractsBeforeOpeningSession() {
        TrackingSessionFactory sessions = new TrackingSessionFactory(session(MODEL));
        LocalEvaluationPreflight.Input input = validInput();
        assertThatThrownBy(() -> preflight.prepare(input, () -> null, sessions))
                .hasMessageContaining("contract is absent");

        LocalEvaluationContract tracked = trackedContract();
        LocalEvaluationContract.Identity identity = tracked.identity();
        List<LocalEvaluationContract.Identity> invalid = List.of(
                copyIdentity(identity, "0".repeat(64), identity.catalogSha256(), identity.reviewStatus(),
                        identity.reviewSha256(), identity.confirmedFixtureIds()),
                copyIdentity(identity, identity.promptSha256(), "1".repeat(64), identity.reviewStatus(),
                        identity.reviewSha256(), identity.confirmedFixtureIds()),
                copyIdentity(identity, identity.promptSha256(), identity.catalogSha256(), "pending",
                        identity.reviewSha256(), identity.confirmedFixtureIds()),
                copyIdentity(identity, identity.promptSha256(), identity.catalogSha256(), identity.reviewStatus(),
                        "2".repeat(64), identity.confirmedFixtureIds()),
                copyIdentity(identity, identity.promptSha256(), identity.catalogSha256(), identity.reviewStatus(),
                        identity.reviewSha256(), identity.confirmedFixtureIds().subList(0, 5)));
        invalid.forEach(drifted -> assertThatThrownBy(() -> preflight.prepare(
                input,
                () -> new LocalEvaluationContract(
                        tracked.prompt(), tracked.catalog(), tracked.review(), drifted),
                sessions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local evaluation preflight failed"));
        assertThat(sessions.created).isZero();
        assertThat(Files.exists(outputRoot())).isFalse();
    }

    @Test
    void resolvesOnlyTheExplicitInstalledTagWithAFullDigestWithoutCallingAProvider() {
        OllamaApi.Model installed = new OllamaApi.Model(
                "judge-model:latest", null, null, 0L, "a".repeat(64), null);
        LocalEvaluationModelIdentity identity = LocalEvaluationModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(installed)),
                "judge-model");

        assertThat(identity).isEqualTo(MODEL);
        assertThatThrownBy(() -> LocalEvaluationModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of()),
                "judge-model"))
                .isInstanceOf(LocalFactCheckJudgeModelUnavailableException.class)
                .hasMessageContaining("not installed");
        assertThatThrownBy(() -> LocalEvaluationModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(new OllamaApi.Model(
                        "judge-model:latest", null, null, 0L, "short", null))),
                "judge-model"))
                .isInstanceOf(LocalFactCheckJudgeModelUnavailableException.class)
                .hasMessageContaining("complete immutable digest");
    }

    @Test
    void rejectsMissingOrMismatchedResolvedModelBeforeCreatingOutput() {
        LocalEvaluationPreflight.JudgeSession unavailable = new LocalEvaluationPreflight.JudgeSession() {
            @Override
            public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
                throw new LocalFactCheckJudgeModelUnavailableException("missing");
            }

            @Override
            public LocalFactCheckJudgeResult evaluate(
                    LocalFactCheckFixture fixture,
                    LocalFactCheckJudgeSettings settings,
                    LocalFactCheckPromptDefinition prompt
            ) {
                throw new AssertionError("Preflight test must not invoke the judge");
            }
        };
        assertThatThrownBy(() -> prepare(validInput(), trackedContract(), unavailable))
                .isInstanceOf(LocalFactCheckJudgeModelUnavailableException.class);
        assertThatThrownBy(() -> prepare(
                validInput(),
                trackedContract(),
                session(new LocalEvaluationModelIdentity(
                        "other-model", "other-model:latest", "b".repeat(64)))))
                .isInstanceOf(LocalFactCheckJudgeModelUnavailableException.class)
                .hasMessageContaining("does not match");
        assertThat(Files.exists(outputRoot())).isFalse();
    }

    @Test
    void completesAllPreflightBeforeAllocatingOneNonOverwritingDirectory() {
        LocalEvaluationPreflight.Prepared prepared = prepare(validInput(), trackedContract(), session(MODEL));

        assertThat(prepared.settings().requestedModel()).isEqualTo("judge-model");
        assertThat(prepared.settings().maxTokens()).isEqualTo(64);
        assertThat(prepared.settings().timeoutMillis()).isEqualTo(30_000);
        assertThat(Files.exists(prepared.outputDirectory())).isFalse();
        assertThat(LocalEvaluationPreflight.allocate(prepared)).isEqualTo(prepared.outputDirectory());
        assertThat(Files.isDirectory(prepared.outputDirectory())).isTrue();
        assertThatThrownBy(() -> LocalEvaluationPreflight.allocate(prepared))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void acceptsLiteralIpv4AndIpv6LoopbackEndpoints() {
        LocalEvaluationPreflight.Prepared ipv4 = prepare(
                input("http://127.0.0.1:11434", "judge-model", "64", "PT30S", RUN),
                trackedContract(),
                session(MODEL));
        LocalEvaluationPreflight.Prepared ipv6 = prepare(
                input("http://[::1]:11434", "judge-model", "64", "PT30S", RUN),
                trackedContract(),
                session(MODEL));

        assertThat(ipv4.outputDirectory()).isEqualTo(ipv6.outputDirectory());
        assertThat(Files.exists(ipv4.outputDirectory())).isFalse();
    }

    private LocalEvaluationPreflight.Prepared prepare(
            LocalEvaluationPreflight.Input input,
            LocalEvaluationContract contract,
            LocalEvaluationPreflight.JudgeSession session
    ) {
        return preflight.prepare(input, () -> contract, (baseUrl, timeout) -> session);
    }

    private LocalEvaluationPreflight.Input validInput() {
        return input("http://localhost:11434", "judge-model", "64", "PT30S", RUN);
    }

    private LocalEvaluationPreflight.Input input(
            String baseUrl,
            String judgeModel,
            String maxTokens,
            String timeout,
            String output
    ) {
        return new LocalEvaluationPreflight.Input(
                projectDirectory, baseUrl, judgeModel, maxTokens, timeout, output);
    }

    private LocalEvaluationContract trackedContract() {
        return new LocalEvaluationContract(
                LocalEvaluationTestFixtures.PROMPT,
                LocalEvaluationTestFixtures.CATALOG,
                LocalEvaluationTestFixtures.REVIEW);
    }

    private Path outputRoot() {
        return projectDirectory.resolve("local/evidence/evaluation-matrix");
    }

    private static LocalEvaluationContract.Identity copyIdentity(
            LocalEvaluationContract.Identity source,
            String promptSha256,
            String catalogSha256,
            String reviewStatus,
            String reviewSha256,
            List<String> confirmedFixtureIds
    ) {
        return new LocalEvaluationContract.Identity(
                source.promptId(),
                source.promptVersion(),
                promptSha256,
                source.catalogId(),
                source.catalogVersion(),
                catalogSha256,
                source.reviewId(),
                source.reviewVersion(),
                reviewStatus,
                reviewSha256,
                source.reviewedCatalogId(),
                source.reviewedCatalogVersion(),
                catalogSha256,
                confirmedFixtureIds,
                source.catalogFixtureIds());
    }

    private static LocalEvaluationPreflight.JudgeSession session(LocalEvaluationModelIdentity identity) {
        return new LocalEvaluationPreflight.JudgeSession() {
            @Override
            public LocalEvaluationModelIdentity requireInstalled(String requestedModel) {
                return identity;
            }

            @Override
            public LocalFactCheckJudgeResult evaluate(
                    LocalFactCheckFixture fixture,
                    LocalFactCheckJudgeSettings settings,
                    LocalFactCheckPromptDefinition prompt
            ) {
                throw new AssertionError("Preflight test must not invoke the judge");
            }
        };
    }

    private static final class TrackingSessionFactory implements LocalEvaluationPreflight.JudgeSessionFactory {
        private final LocalEvaluationPreflight.JudgeSession session;
        private int created;

        private TrackingSessionFactory(LocalEvaluationPreflight.JudgeSession session) {
            this.session = session;
        }

        @Override
        public LocalEvaluationPreflight.JudgeSession create(String baseUrl, Duration timeout) {
            created++;
            return session;
        }
    }
}
