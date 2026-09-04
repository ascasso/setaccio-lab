package com.setaccio.lab.toolcompat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortFrontierRunnerArgumentsTest {

    @Test
    void requiresExactlyOneTrimmedSavedCohortDirectory() {
        assertThat(ToolCompatibilityCohortFrontierRunner.Arguments.parse(new String[] {
                "--run-dir", "local/evidence/tool-compatibility/2026-08-24-approved-cohort"
        }).runDirectory())
                .isEqualTo("local/evidence/tool-compatibility/2026-08-24-approved-cohort");

        assertThatThrownBy(() -> ToolCompatibilityCohortFrontierRunner.Arguments.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--run-dir");
        assertThatThrownBy(() -> ToolCompatibilityCohortFrontierRunner.Arguments.parse(
                new String[] {"--run-dir"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortFrontierRunner.Arguments.parse(
                new String[] {"--unknown", "value"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortFrontierRunner.Arguments.parse(
                new String[] {"--run-dir", " value "}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityCohortFrontierRunner.Arguments.parse(
                new String[] {"--run-dir", "one", "--run-dir", "two"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
