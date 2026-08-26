package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalEvaluationBudgetComparisonRunnerArgumentsTest {

    @Test
    void parsesBothSavedArmOptionsInEitherOrder() {
        LocalEvaluationBudgetComparisonRunner.Arguments parsed =
                LocalEvaluationBudgetComparisonRunner.Arguments.parse(new String[] {
                        "--budget-256-run-dir", "build/evaluation-matrix/2026-08-26-budget-256",
                        "--budget-64-run-dir", "build/evaluation-matrix/2026-08-26-budget-64"
                });

        assertThat(parsed.budget64RunDirectory())
                .isEqualTo("build/evaluation-matrix/2026-08-26-budget-64");
        assertThat(parsed.budget256RunDirectory())
                .isEqualTo("build/evaluation-matrix/2026-08-26-budget-256");
    }

    @Test
    void rejectsMissingDuplicateAndUnsupportedOptions() {
        assertThatThrownBy(() -> LocalEvaluationBudgetComparisonRunner.Arguments.parse(new String[] {
                "--budget-64-run-dir", "build/evaluation-matrix/64",
                "--budget-64-run-dir", "build/evaluation-matrix/256"
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LocalEvaluationBudgetComparisonRunner.Arguments.parse(new String[] {
                "--budget-64-run-dir", "build/evaluation-matrix/64",
                "--budget-256-run-dir", ""
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LocalEvaluationBudgetComparisonRunner.Arguments.parse(new String[] {
                "--mode", "compare",
                "--budget-64-run-dir", "build/evaluation-matrix/64"
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
