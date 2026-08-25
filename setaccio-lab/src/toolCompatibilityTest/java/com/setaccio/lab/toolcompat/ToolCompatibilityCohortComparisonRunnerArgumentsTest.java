package com.setaccio.lab.toolcompat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortComparisonRunnerArgumentsTest {

    @Test
    void requiresExactlyOneTrimmedSavedCohortDirectory() {
        assertThat(ToolCompatibilityCohortComparisonRunner.Arguments.parse(new String[] {
                "--run-dir", "build/tool-compatibility/2026-08-24-approved-cohort"
        }).runDirectory())
                .isEqualTo("build/tool-compatibility/2026-08-24-approved-cohort");

        assertThatThrownBy(() -> ToolCompatibilityCohortComparisonRunner.Arguments.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--run-dir");
        assertThatThrownBy(() -> ToolCompatibilityCohortComparisonRunner.Arguments.parse(
                new String[] {"--run-dir"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortComparisonRunner.Arguments.parse(
                new String[] {"--unknown", "value"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortComparisonRunner.Arguments.parse(
                new String[] {"--run-dir", " value "}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortComparisonRunner.Arguments.parse(
                new String[] {"--run-dir", "one", "--run-dir", "two"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
