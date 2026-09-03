package com.setaccio.lab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvaluationBudgetOfflineRunnerArgumentsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesBothPairDirectoriesAndRejectsDirectoriesOutsideTheEvidenceRoot() {
        LocalEvaluationBudgetOfflineRunner.Arguments parsed =
                LocalEvaluationBudgetOfflineRunner.Arguments.parse(new String[] {
                        "--budget-256-run-dir", "local/evidence/evaluation-matrix/2026-08-25-budget-256",
                        "--mode", "verify",
                        "--budget-64-run-dir", "local/evidence/evaluation-matrix/2026-08-25-budget-64"
                });

        assertThat(parsed.budget64RunDirectory())
                .isEqualTo("local/evidence/evaluation-matrix/2026-08-25-budget-64");
        assertThat(parsed.budget256RunDirectory())
                .isEqualTo("local/evidence/evaluation-matrix/2026-08-25-budget-256");
        assertThatThrownBy(() -> LocalEvaluationBudgetOfflineRunner.resolveRunDirectory(
                temporaryDirectory.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("F1 budget run directory must be directly under local/evidence/evaluation-matrix/ (or legacy build/evaluation-matrix/ for already-saved evidence).");
    }

    @Test
    void rejectsMissingDuplicateAndUnsupportedPairOptions() {
        assertThatThrownBy(() -> LocalEvaluationBudgetOfflineRunner.Arguments.parse(new String[] {
                "--mode", "verify",
                "--budget-64-run-dir", "local/evidence/evaluation-matrix/64",
                "--budget-64-run-dir", "local/evidence/evaluation-matrix/256"
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LocalEvaluationBudgetOfflineRunner.Arguments.parse(new String[] {
                "--mode", "verify",
                "--budget-64-run-dir", "local/evidence/evaluation-matrix/64",
                "--budget-256-run-dir", ""
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LocalEvaluationBudgetOfflineRunner.Arguments.parse(new String[] {
                "--mode", "provider",
                "--budget-64-run-dir", "local/evidence/evaluation-matrix/64",
                "--budget-256-run-dir", "local/evidence/evaluation-matrix/256"
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
