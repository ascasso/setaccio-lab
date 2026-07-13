package com.setaccio.lab.controller;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ChatBenchmarkPrompt;
import com.setaccio.lab.model.ChatBenchmarkResult;
import com.setaccio.lab.model.ChatBenchmarkRow;
import com.setaccio.lab.service.ChatBenchmarkService;
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

class ChatBenchmarkControllerTest {

    private MockMvc mockMvc;

    private ChatBenchmarkService chatBenchmarkService;

    @BeforeEach
    void setUp() {
        chatBenchmarkService = mock(ChatBenchmarkService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatBenchmarkController(chatBenchmarkService)).build();
    }

    @Test
    void runDelegatesToServiceWithParsedModelsAndDefaultPrompts() throws Exception {
        when(chatBenchmarkService.run(anyList(), any(AdvisorMode.class), anyList()))
                .thenAnswer(invocation -> {
                    List<String> models = invocation.getArgument(0);
                    AdvisorMode advisorMode = invocation.getArgument(1);
                    List<ChatBenchmarkPrompt> prompts = invocation.getArgument(2);
                    assertThat(models).containsExactly("model-a", "model-b");
                    assertThat(advisorMode).isEqualTo(AdvisorMode.STANDARD);
                    assertThat(prompts).isNotEmpty();
                    return result();
                });

        mockMvc.perform(post("/api/lab/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a, model-b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("chat"))
                .andExpect(jsonPath("$.runs[0].outputText").value("answer"));
    }

    @Test
    void runDelegatesToServiceWithExplicitPrompts() throws Exception {
        when(chatBenchmarkService.run(anyList(), any(AdvisorMode.class), anyList()))
                .thenAnswer(invocation -> {
                    List<ChatBenchmarkPrompt> prompts = invocation.getArgument(2);
                    assertThat(prompts).singleElement().satisfies(prompt -> {
                        assertThat(prompt.id()).isEqualTo("custom");
                        assertThat(prompt.text()).isEqualTo("Answer this.");
                    });
                    return result();
                });

        mockMvc.perform(post("/api/lab/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "useDefaultPrompts": false,
                                  "prompts": [
                                    {"id": " custom ", "text": " Answer this. "}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void runRejectsMissingModels() throws Exception {
        mockMvc.perform(post("/api/lab/chat")
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
        mockMvc.perform(post("/api/lab/chat")
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
        mockMvc.perform(post("/api/lab/chat")
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
        mockMvc.perform(post("/api/lab/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": "model-a",
                                  "advisorMode": "tool_search"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private ChatBenchmarkResult result() {
        return new ChatBenchmarkResult(
                "chat",
                "ollama",
                AdvisorMode.STANDARD,
                Instant.parse("2026-07-05T00:00:00Z"),
                Instant.parse("2026-07-05T00:00:01Z"),
                "test-host",
                "http://localhost:11434",
                List.of(ChatBenchmarkRow.ok(
                        "ollama",
                        "model-a",
                        new ChatBenchmarkPrompt("p1", "prompt"),
                        AdvisorMode.STANDARD,
                        10,
                        null,
                        null,
                        "answer"))
        );
    }
}
