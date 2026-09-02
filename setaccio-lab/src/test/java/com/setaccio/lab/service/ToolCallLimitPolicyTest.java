package com.setaccio.lab.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallLimitPolicyTest {

    @Test
    void locksTheToolCallLimitsAppliedByBothToolPaths() {
        assertThat(ToolCallLimitPolicy.MAX_CALLS_PER_TOOL).isEqualTo(40);
        assertThat(ToolCallLimitPolicy.MAX_TOTAL_TOOL_CALLS).isEqualTo(150);
        assertThat(ToolCallLimitPolicy.ON_LIMIT_EXCEEDED).isEqualTo(ToolCallLimitBehavior.THROW);
    }

    @Test
    void buildsAManagerRatherThanInheritingFrameworkDefaults() {
        ToolCallingManager manager = ToolCallLimitPolicy.toolCallingManager();

        assertThat(manager).isNotNull().isInstanceOf(DefaultToolCallingManager.class);
        assertThat(ToolCallLimitPolicy.toolCallingManager()).isNotSameAs(manager);
    }

    /**
     * Drift detector. The locked values currently match Spring AI's defaults, so pinning them
     * changes no behaviour. If a framework upgrade moves a default, this fails deliberately so
     * the locked protocol value is reconsidered explicitly instead of shifting underneath a run.
     */
    @Test
    void recordsThatTheLockedLimitsStillMatchTheFrameworkDefaults() {
        assertThat(ToolCallLimitPolicy.MAX_CALLS_PER_TOOL)
                .isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL);
        assertThat(ToolCallLimitPolicy.MAX_TOTAL_TOOL_CALLS)
                .isEqualTo(DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS);
    }
}
