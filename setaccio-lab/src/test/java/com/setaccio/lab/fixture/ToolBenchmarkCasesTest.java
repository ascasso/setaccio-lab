package com.setaccio.lab.fixture;

import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBenchmarkCasesTest {

    @Test
    void defaultCasesCoverExpectedExecutionShapes() {
        assertThat(ToolBenchmarkCases.defaults())
                .extracting(ToolBenchmarkPrompt::id)
                .containsExactly(
                        "arithmetic-add",
                        "fixed-utc-time",
                        "fixed-zone-time",
                        "catalog-lookup",
                        "catalog-multi-step",
                        "catalog-no-match",
                        "no-applicable-domain-tool",
                        "deterministic-tool-failure"
                );
        assertThat(ToolBenchmarkCases.defaults())
                .allSatisfy(prompt -> assertThat(prompt.expectation().hasChecks()).isTrue());

        ToolBenchmarkPrompt noTool = caseById("no-applicable-domain-tool");
        assertThat(noTool.expectation().forbiddenExecutedTools())
                .containsExactlyElementsOf(ToolBenchmarkCases.toolNames());

        ToolBenchmarkPrompt failure = caseById("deterministic-tool-failure");
        assertThat(failure.expectation().requiredExecutedTools())
                .containsExactly(FailureBenchmarkTools.FAIL_TOOL_NAME);
        assertThat(failure.expectation().requiredToolResponseTerms())
                .containsExactly(FailureBenchmarkTools.FAILURE_MARKER);
    }

    private ToolBenchmarkPrompt caseById(String id) {
        return ToolBenchmarkCases.defaults().stream()
                .filter(prompt -> prompt.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
