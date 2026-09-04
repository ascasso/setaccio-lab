package com.setaccio.lab.toolcompat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPromptMatrixComparisonRunnerArgumentsTest {

    @Test
    void requiresBothSavedRunsExactlyOnce() {
        assertThat(ToolCompatibilityPromptMatrixComparisonRunner.Arguments.parse(new String[] {
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-prompted",
                "--baseline-run", "local/evidence/tool-compatibility/2026-08-21-baseline"
        })).isEqualTo(new ToolCompatibilityPromptMatrixComparisonRunner.Arguments(
                "local/evidence/tool-compatibility/2026-08-21-baseline",
                "local/evidence/tool-compatibility/2026-08-21-prompted"));

        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixComparisonRunner.Arguments.parse(new String[] {
                "--baseline-run", "local/evidence/tool-compatibility/2026-08-21-baseline",
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-prompted",
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-other"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixComparisonRunner.Arguments.parse(new String[] {
                "--baseline-run", " local/evidence/tool-compatibility/2026-08-21-baseline ",
                "--candidate-run", "local/evidence/tool-compatibility/2026-08-21-prompted"
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
