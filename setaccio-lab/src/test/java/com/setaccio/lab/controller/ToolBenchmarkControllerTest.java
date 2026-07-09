package com.setaccio.lab.controller;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.service.ToolBenchmarkService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ToolBenchmarkControllerTest {

    private MockMvc mockMvc;

    private ToolBenchmarkService toolBenchmarkService;

    @BeforeEach
    void setUp() {
        toolBenchmarkService = mock(ToolBenchmarkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolBenchmarkController(toolBenchmarkService)).build();
    }

    @Test
    void runDelegatesToServiceWithParsedModelsAndDefaultPrompts() throws Exception {
        when(toolBenchmarkService.run(anyList(), any(AdvisorMode.class), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<String> models = invocation.getArgument(0);
                    AdvisorMode advisorMode = invocation.getArgument(1);
                    List<ToolBenchmarkPrompt> prompts = invocation.getArgument(2);
                    assertThat(models).containsExactly("model-a", "model-b");
                    assertThat(advisorMode).isEqualTo(AdvisorMode.STANDARD);
                    assertThat(prompts).isNotEmpty();
                    return result();
                });

        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a, model-b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("tool-calling"))
                .andExpect(jsonPath("$.runs[0].outputText").value("answer"));
    }

    @Test
    void runRejectsMissingModels() throws Exception {
        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": " "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runRejectsMissingPromptsWhenDefaultsAreDisabled() throws Exception {
        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "useDefaultPrompts": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runRejectsBlankPromptText() throws Exception {
        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "useDefaultPrompts": false,
                                  "prompts": [
                                    {"id": "p1", "text": " "}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runRejectsUnsupportedAdvisorModeForThisSlice() throws Exception {
        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "advisorMode": "tool_search"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runDelegatesComparisonRequestsToTheComparisonService() throws Exception {
        when(toolBenchmarkService.compare(anyList(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    assertThat(invocation.<List<String>>getArgument(0)).containsExactly("model-a");
                    assertThat(invocation.<List<ToolBenchmarkPrompt>>getArgument(1)).isNotEmpty();
                    assertThat(invocation.<List<String>>getArgument(2)).isEmpty();
                    return comparisonResult();
                });

        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "advisorMode": "compare"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("tool-calling-comparison"))
                .andExpect(jsonPath("$.toolSearchIndexType").value("regex"))
                .andExpect(jsonPath("$.standard.advisorMode").value("standard"))
                .andExpect(jsonPath("$.toolSearch.advisorMode").value("tool_search"));
    }

    private ToolBenchmarkResult result() {
        return new ToolBenchmarkResult(
                "tool-calling",
                "ollama",
                AdvisorMode.STANDARD,
                Instant.parse("2026-07-04T00:00:00Z"),
                Instant.parse("2026-07-04T00:00:01Z"),
                "test-host",
                "http://localhost:11434",
                List.of("lab_add_numbers"),
                List.of("lab_add_numbers"),
                List.of(ToolBenchmarkRow.ok(
                        "ollama",
                        "model-a",
                        new ToolBenchmarkPrompt("p1", "prompt"),
                        AdvisorMode.STANDARD,
                        List.of("lab_add_numbers"),
                        List.of(),
                        List.of(),
                        10,
                        null,
                        null,
                        "answer"))
        );
    }

    private ToolBenchmarkComparisonResult comparisonResult() {
        ToolBenchmarkResult standard = result();
        ToolBenchmarkResult toolSearch = new ToolBenchmarkResult(
                standard.suite(),
                standard.provider(),
                AdvisorMode.TOOL_SEARCH,
                standard.startedAt(),
                standard.finishedAt(),
                standard.host(),
                standard.ollamaBaseUrl(),
                standard.requestedTools(),
                standard.availableTools(),
                standard.runs().stream()
                        .map(row -> ToolBenchmarkRow.ok(
                                row.provider(),
                                row.model(),
                                new ToolBenchmarkPrompt(row.promptId(), row.promptText()),
                                AdvisorMode.TOOL_SEARCH,
                                row.requestedTools(),
                                row.selectedToolCalls(),
                                row.executedToolResponses(),
                                row.latencyMs(),
                                row.tokensIn(),
                                row.tokensOut(),
                                row.outputText()))
                        .toList()
        );
        return new ToolBenchmarkComparisonResult(
                "tool-calling-comparison",
                "ollama",
                "regex",
                standard.startedAt(),
                standard.finishedAt(),
                standard.host(),
                standard.ollamaBaseUrl(),
                standard.requestedTools(),
                standard.availableTools(),
                standard,
                toolSearch
        );
    }
}
