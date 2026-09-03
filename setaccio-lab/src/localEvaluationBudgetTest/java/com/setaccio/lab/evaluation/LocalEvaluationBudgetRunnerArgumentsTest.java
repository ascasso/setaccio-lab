package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalEvaluationBudgetRunnerArgumentsTest {

    @Test
    void requiresBothFreshArmDirectoriesAndDoesNotAcceptAFreeTokenLimit() {
        LocalEvaluationBudgetRunner.Arguments parsed = LocalEvaluationBudgetRunner.Arguments.parse(new String[] {
                "--judge-model", "judge-model",
                "--output-dir-256", "local/evidence/evaluation-matrix/2026-08-25-budget-256",
                "--ollama-base-url", "http://localhost:11434",
                "--output-dir-64", "local/evidence/evaluation-matrix/2026-08-25-budget-64"
        });

        assertThat(parsed.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(parsed.judgeModel()).isEqualTo("judge-model");
        assertThat(parsed.outputDirectory64())
                .isEqualTo("local/evidence/evaluation-matrix/2026-08-25-budget-64");
        assertThat(parsed.outputDirectory256())
                .isEqualTo("local/evidence/evaluation-matrix/2026-08-25-budget-256");

        assertThatThrownBy(() -> LocalEvaluationBudgetRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--judge-model", "judge-model",
                "--max-tokens", "128",
                "--output-dir-64", "local/evidence/evaluation-matrix/2026-08-25-budget-64",
                "--output-dir-256", "local/evidence/evaluation-matrix/2026-08-25-budget-256"
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOrDuplicatePairOptions() {
        assertThatThrownBy(() -> LocalEvaluationBudgetRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--judge-model", "judge-model",
                "--output-dir-64", "local/evidence/evaluation-matrix/2026-08-25-budget-64",
                "--output-dir-64", "local/evidence/evaluation-matrix/2026-08-25-budget-256"
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LocalEvaluationBudgetRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--judge-model", "judge-model",
                "--output-dir-64", "local/evidence/evaluation-matrix/2026-08-25-budget-64",
                "--output-dir-256", ""
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
