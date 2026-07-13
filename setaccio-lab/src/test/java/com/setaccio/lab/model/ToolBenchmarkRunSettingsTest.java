package com.setaccio.lab.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolBenchmarkRunSettingsTest {

    @Test
    void comparisonDefaultsAreCounterbalancedAndDeterministic() {
        ToolBenchmarkRunSettings settings = ToolBenchmarkRunSettings.comparisonDefaults();

        assertThat(settings.repetitions()).isEqualTo(2);
        assertThat(settings.temperature()).isZero();
        assertThat(settings.seedFor(1)).isEqualTo(42);
        assertThat(settings.seedFor(2)).isEqualTo(43);
        assertThat(settings.comparisonOrder().modesFor(1))
                .containsExactly(AdvisorMode.STANDARD, AdvisorMode.TOOL_SEARCH);
        assertThat(settings.comparisonOrder().modesFor(2))
                .containsExactly(AdvisorMode.TOOL_SEARCH, AdvisorMode.STANDARD);
    }

    @Test
    void rejectsSettingsThatCannotProduceControlledRuns() {
        assertThatThrownBy(() -> new ToolBenchmarkRunSettings(
                0, 0.0, 42, null, ToolBenchmarkComparisonOrder.ALTERNATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetitions");
        assertThatThrownBy(() -> new ToolBenchmarkRunSettings(
                1, 2.1, 42, null, ToolBenchmarkComparisonOrder.ALTERNATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature");
        assertThatThrownBy(() -> new ToolBenchmarkRunSettings(
                1, 0.0, -1, null, ToolBenchmarkComparisonOrder.ALTERNATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseSeed");
    }
}
