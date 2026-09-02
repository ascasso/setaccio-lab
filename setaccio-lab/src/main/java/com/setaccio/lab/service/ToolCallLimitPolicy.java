package com.setaccio.lab.service;

import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * Locked tool-call limits for the tool benchmark and tool-compatibility protocols.
 *
 * <p>Spring AI 2.0.1 made these limits configurable and applies 40 calls per tool, 150 total
 * tool calls, and {@link ToolCallLimitBehavior#THROW} by default. Exceeding either limit aborts
 * the invocation rather than truncating it, so the values are part of the observable protocol.
 * Pinning the current defaults here keeps the protocol stable if a future framework release
 * changes them, without altering present behaviour.
 *
 * <p>These values are intentionally not written into saved evidence. Tool Search matrix
 * verification pins the exact manifest settings key set, so adding a key would invalidate every
 * retained manifest. The effective limits are recorded in the tracked protocol documentation
 * instead.
 */
public final class ToolCallLimitPolicy {

    public static final int MAX_CALLS_PER_TOOL = 40;
    public static final int MAX_TOTAL_TOOL_CALLS = 150;
    public static final ToolCallLimitBehavior ON_LIMIT_EXCEEDED = ToolCallLimitBehavior.THROW;

    private ToolCallLimitPolicy() {}

    /** Creates a manager with the locked limits applied explicitly rather than inherited. */
    public static ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder()
                .maxCallsPerTool(MAX_CALLS_PER_TOOL)
                .maxTotalToolCalls(MAX_TOTAL_TOOL_CALLS)
                .onLimitExceeded(ON_LIMIT_EXCEEDED)
                .build();
    }
}
