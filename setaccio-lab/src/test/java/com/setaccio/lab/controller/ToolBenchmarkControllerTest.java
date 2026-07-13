package com.setaccio.lab.controller;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
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
        when(toolBenchmarkService.run(
                anyList(), any(AdvisorMode.class), anyList(), anyList(), any(ToolBenchmarkRunSettings.class)))
                .thenAnswer(invocation -> {
                    List<String> models = invocation.getArgument(0);
                    AdvisorMode advisorMode = invocation.getArgument(1);
                    List<ToolBenchmarkPrompt> prompts = invocation.getArgument(2);
                    ToolBenchmarkRunSettings runSettings = invocation.getArgument(4);
                    assertThat(models).containsExactly("model-a", "model-b");
                    assertThat(advisorMode).isEqualTo(AdvisorMode.STANDARD);
                    assertThat(prompts).isNotEmpty();
                    assertThat(runSettings.repetitions()).isEqualTo(1);
                    assertThat(runSettings.temperature()).isZero();
                    assertThat(runSettings.baseSeed()).isEqualTo(42);
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
        when(toolBenchmarkService.compare(
                anyList(), anyList(), anyList(), any(ToolBenchmarkRunSettings.class)))
                .thenAnswer(invocation -> {
                    assertThat(invocation.<List<String>>getArgument(0)).containsExactly("model-a");
                    assertThat(invocation.<List<ToolBenchmarkPrompt>>getArgument(1)).isNotEmpty();
                    assertThat(invocation.<List<String>>getArgument(2)).isEmpty();
                    ToolBenchmarkRunSettings runSettings = invocation.getArgument(3);
                    assertThat(runSettings.repetitions()).isEqualTo(2);
                    assertThat(runSettings.comparisonOrder()).isEqualTo(ToolBenchmarkComparisonOrder.ALTERNATE);
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

    @Test
    void runPassesExplicitExpectationsAndComparisonSettings() throws Exception {
        when(toolBenchmarkService.compare(
                anyList(), anyList(), anyList(), any(ToolBenchmarkRunSettings.class)))
                .thenAnswer(invocation -> {
                    List<ToolBenchmarkPrompt> prompts = invocation.getArgument(1);
                    assertThat(prompts).singleElement().satisfies(prompt -> {
                        assertThat(prompt.id()).isEqualTo("opaque-lookup");
                        assertThat(prompt.expectation().requiredExecutedTools())
                                .containsExactly("lab_lookup_catalog_item");
                        assertThat(prompt.expectation().requiredOutputTerms())
                                .containsExactly("Policy FAQ");
                    });
                    ToolBenchmarkRunSettings runSettings = invocation.getArgument(3);
                    assertThat(runSettings.repetitions()).isEqualTo(4);
                    assertThat(runSettings.temperature()).isEqualTo(0.2);
                    assertThat(runSettings.baseSeed()).isEqualTo(100);
                    assertThat(runSettings.maxTokens()).isEqualTo(256);
                    assertThat(runSettings.comparisonOrder())
                            .isEqualTo(ToolBenchmarkComparisonOrder.TOOL_SEARCH_FIRST);
                    return comparisonResult();
                });

        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "advisorMode": "compare",
                                  "useDefaultPrompts": false,
                                  "repetitions": 4,
                                  "temperature": 0.2,
                                  "baseSeed": 100,
                                  "maxTokens": 256,
                                  "comparisonOrder": "tool_search_first",
                                  "prompts": [{
                                    "id": "opaque-lookup",
                                    "text": "Look up the fixture.",
                                    "expectation": {
                                      "requiredExecutedTools": ["lab_lookup_catalog_item"],
                                      "requiredOutputTerms": ["Policy FAQ"]
                                    }
                                  }]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void runRejectsInvalidComparisonSettings() throws Exception {
        mockMvc.perform(post("/api/lab/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "advisorMode": "compare",
                                  "repetitions": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
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
                ToolBenchmarkRunSettings.standardDefaults(),
                "parallel",
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
                ToolBenchmarkRunSettings.comparisonDefaults(),
                "paired_sequential",
                standard.requestedTools(),
                standard.availableTools(),
                standard.runs().stream()
                        .map(row -> ToolBenchmarkRow.ok(
                                row.provider(),
                                row.model(),
                                new ToolBenchmarkPrompt(row.promptId(), row.promptText(), row.expectation()),
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
                ToolBenchmarkRunSettings.comparisonDefaults(),
                "paired_sequential",
                standard.requestedTools(),
                standard.availableTools(),
                standard,
                toolSearch
        );
    }
}
