package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolBenchmarkTraceEvaluatorTest {

    @Test
    void assessExtractsToolNamesFromToolSearchResponse() {
        ToolBenchmarkTraceEvaluator evaluator = new ToolBenchmarkTraceEvaluator(new ObjectMapper());
        ToolBenchmarkPrompt prompt = new ToolBenchmarkPrompt(
                "add",
                "Add two numbers.",
                new ToolBenchmarkExpectation(List.of("addNumbers"), List.of(), List.of(), List.of()));
        ToolCallObservation searchCall = new ToolCallObservation(
                "search-1", "function", ToolBenchmarkTraceEvaluator.TOOL_SEARCH_TOOL_NAME,
                "{\"query\":\"add numbers\"}");
        ToolExecutionObservation searchResponse = new ToolExecutionObservation(
                "search-1", ToolBenchmarkTraceEvaluator.TOOL_SEARCH_TOOL_NAME,
                "{\"toolReferences\":[{\"toolName\":\"addNumbers\",\"relevanceScore\":1.0,\"summary\":\"Adds numbers\"}],\"totalMatches\":1}");
        ToolExecutionObservation toolResponse = new ToolExecutionObservation(
                "tool-1", "addNumbers", "5");

        ToolBenchmarkTraceEvaluator.Assessment assessment = evaluator.assess(
                prompt,
                AdvisorMode.TOOL_SEARCH,
                true,
                "5",
                List.of(searchCall),
                List.of(searchResponse, toolResponse));

        assertThat(assessment.toolSearchObservations()).singleElement().satisfies(observation -> {
            assertThat(observation.completed()).isTrue();
            assertThat(observation.discoveredTools()).containsExactly("addNumbers");
        });
        assertThat(assessment.assertions())
                .filteredOn(assertion -> assertion.check().equals("required_tool_discovered"))
                .singleElement()
                .satisfies(assertion -> assertThat(assertion.passed()).isTrue());
        assertThat(assessment.contractPassed()).isTrue();
    }

    @Test
    void assessAcceptsArgZeroAndRecordsACompletedZeroMatchSearch() {
        ToolBenchmarkTraceEvaluator evaluator = new ToolBenchmarkTraceEvaluator(new ObjectMapper());
        ToolBenchmarkPrompt prompt = new ToolBenchmarkPrompt(
                "no-match",
                "Find a tool.",
                new ToolBenchmarkExpectation(List.of(), List.of(), List.of(), List.of()));
        ToolCallObservation searchCall = new ToolCallObservation(
                "search-1", "function", ToolBenchmarkTraceEvaluator.TOOL_SEARCH_TOOL_NAME,
                "{\"arg0\":\"missing domain tool\"}");
        ToolExecutionObservation searchResponse = new ToolExecutionObservation(
                "search-1", ToolBenchmarkTraceEvaluator.TOOL_SEARCH_TOOL_NAME,
                "{\"toolReferences\":[],\"totalMatches\":0}");

        ToolBenchmarkTraceEvaluator.Assessment assessment = evaluator.assess(
                prompt,
                AdvisorMode.TOOL_SEARCH,
                true,
                "No matching tool.",
                List.of(searchCall),
                List.of(searchResponse));

        assertThat(assessment.toolSearchObservations()).singleElement().satisfies(observation -> {
            assertThat(observation.query()).isEqualTo("missing domain tool");
            assertThat(observation.completed()).isTrue();
            assertThat(observation.discoveredTools()).isEmpty();
        });
    }
}
