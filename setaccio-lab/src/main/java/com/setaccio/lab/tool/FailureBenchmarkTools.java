package com.setaccio.lab.tool;

import org.springframework.ai.tool.annotation.Tool;

public class FailureBenchmarkTools {

    public static final String FAIL_TOOL_NAME = "lab_fail_fixture";
    public static final String FAILURE_MARKER = "fixture-tool-failure";

    @Tool(
            name = FAIL_TOOL_NAME,
            description = "Fail deterministically with a public-safe marker for tool error benchmark prompts."
    )
    public String failFixture() {
        throw new IllegalStateException(FAILURE_MARKER);
    }
}
