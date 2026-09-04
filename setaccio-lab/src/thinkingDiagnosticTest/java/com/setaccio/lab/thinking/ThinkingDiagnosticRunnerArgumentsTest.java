package com.setaccio.lab.thinking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The diagnostic reaches a provider only through explicit arguments and an injected model
 * factory. Nothing here has an endpoint, model, or credential default, so the default build and
 * every provider-free test task stay isolated from Ollama.
 */
class ThinkingDiagnosticRunnerArgumentsTest {

    @Test
    void requiresEveryExplicitOptionBeforeAnyProviderContact() {
        ThinkingDiagnosticRunner.Arguments parsed = ThinkingDiagnosticRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--subject-model", "gemma4:e2b",
                "--control-model", "granite4.1:3b",
                "--ollama-version", "0.33.2",
                "--output-dir", "local/evidence/thinking-diagnostic/2026-09-03-thinking"
        });

        assertThat(parsed.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(parsed.subjectModel()).isEqualTo("gemma4:e2b");
        assertThat(parsed.controlModel()).isEqualTo("granite4.1:3b");
        assertThat(parsed.ollamaVersion()).isEqualTo("0.33.2");
        assertThat(parsed.outputDirectory())
                .isEqualTo("local/evidence/thinking-diagnostic/2026-09-03-thinking");
    }

    @Test
    void rejectsMissingBlankAndUntrimmedOptions() {
        assertThatThrownBy(() -> ThinkingDiagnosticRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThinkingDiagnosticRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--subject-model", " ",
                "--control-model", "granite4.1:3b",
                "--ollama-version", "0.33.2",
                "--output-dir", "local/evidence/thinking-diagnostic/2026-09-03-thinking"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThinkingDiagnosticRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--subject-model", " gemma4:e2b ",
                "--control-model", "granite4.1:3b",
                "--ollama-version", "0.33.2",
                "--output-dir", "local/evidence/thinking-diagnostic/2026-09-03-thinking"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAnInjectedJudgeFactorySoNothingConstructsAProviderClientImplicitly() {
        assertThatThrownBy(() -> new ThinkingDiagnosticExecutor(
                null, new ThinkingDiagnosticTestSupport.PolicyAwareChatFactory(),
                ThinkingDiagnosticTestSupport.prompt()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("judgeFactory");
    }

    @Test
    void restrictsOfflineInspectionToTheDurableSuiteRootWhileAcceptingLegacyReads() {
        assertThatThrownBy(() -> ThinkingDiagnosticOfflineRunner.resolveRunDirectory(
                "local/evidence/elsewhere/2026-09-03-thinking"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local/evidence/thinking-diagnostic");
        assertThatThrownBy(() -> ThinkingDiagnosticOfflineRunner.resolveRunDirectory(
                "local/evidence/thinking-diagnostic/absent-run"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist or is unsafe");
    }
}
