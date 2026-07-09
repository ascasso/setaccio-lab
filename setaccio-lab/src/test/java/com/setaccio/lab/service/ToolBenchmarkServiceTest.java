package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.config.BenchmarkToolConfig;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.tool.ArithmeticBenchmarkTools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolBenchmarkServiceTest {

    @Test
    void runBuildsRowsForEachPromptAndModelAndWritesJson() throws Exception {
        Path outputDir = Files.createTempDirectory("tool-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isIn("model-a", "model-b");
            assertThat(options.getToolCallbacks())
                    .extracting(callback -> callback.getToolDefinition().name())
                    .contains(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("tool benchmark answer"))));
        });

        ToolBenchmarkService service = newService(outputDir, ollamaChatModel);

        ToolBenchmarkResult result = service.run(
                List.of("model-a", "model-b"),
                AdvisorMode.STANDARD,
                List.of(new ToolBenchmarkPrompt("p1", "Use a tool.")),
                List.of()
        );

        assertThat(result.suite()).isEqualTo("tool-calling");
        assertThat(result.provider()).isEqualTo("ollama");
        assertThat(result.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(result.availableTools()).contains(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
        assertThat(result.requestedTools()).isEqualTo(result.availableTools());
        assertThat(result.runs()).hasSize(2);
        assertThat(result.runs()).allSatisfy(row -> {
            assertThat(row.provider()).isEqualTo("ollama");
            assertThat(row.promptId()).isEqualTo("p1");
            assertThat(row.advisorMode()).isEqualTo(AdvisorMode.STANDARD);
            assertThat(row.outputText()).isEqualTo("tool benchmark answer");
            assertThat(row.success()).isTrue();
            assertThat(row.requestedTools()).contains(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
        });
        assertThat(Files.list(outputDir))
                .anySatisfy(path -> assertThat(path.getFileName().toString()).endsWith("-tool-calling.json"));
    }

    @Test
    void runRecordsSelectedAndExecutedToolsWhenModelRequestsToolCall() throws Exception {
        Path outputDir = Files.createTempDirectory("tool-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        AtomicInteger calls = new AtomicInteger();
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            if (calls.getAndIncrement() == 0) {
                assertThat(((OllamaChatOptions) prompt.getOptions()).getModel()).isEqualTo("model-a");
                assertThat(((OllamaChatOptions) prompt.getOptions()).getToolCallbacks())
                        .extracting(callback -> callback.getToolDefinition().name())
                        .containsExactly(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
                return chatResponse(AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1",
                                        "function",
                                        ArithmeticBenchmarkTools.ADD_TOOL_NAME,
                                        "{\"left\":2,\"right\":3}")))
                                .build(),
                        10,
                        2);
            }
            assertThat(prompt.getInstructions())
                    .anySatisfy(message -> assertThat(message).isInstanceOf(ToolResponseMessage.class));
            return chatResponse(new AssistantMessage("The result is 5."), 20, 5);
        });

        ToolBenchmarkService service = newService(outputDir, ollamaChatModel);

        ToolBenchmarkResult result = service.run(
                List.of("model-a"),
                AdvisorMode.STANDARD,
                List.of(new ToolBenchmarkPrompt("add", "Add 2 and 3 with a tool.")),
                List.of(ArithmeticBenchmarkTools.ADD_TOOL_NAME)
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.selectedToolCalls()).singleElement().satisfies(call -> {
                assertThat(call.id()).isEqualTo("call-1");
                assertThat(call.name()).isEqualTo(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
                assertThat(call.arguments()).contains("\"left\":2");
            });
            assertThat(row.executedToolResponses()).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo("call-1");
                assertThat(response.name()).isEqualTo(ArithmeticBenchmarkTools.ADD_TOOL_NAME);
                assertThat(response.responseData()).contains("\"result\":5");
            });
            assertThat(row.tokensIn()).isEqualTo(30);
            assertThat(row.tokensOut()).isEqualTo(7);
            assertThat(row.outputText()).isEqualTo("The result is 5.");
        });
    }

    @Test
    void runReturnsFailureRowsWhenOllamaModelIsUnavailable() throws Exception {
        Path outputDir = Files.createTempDirectory("tool-results-");
        ToolBenchmarkService service = newService(outputDir, null);

        ToolBenchmarkResult result = service.run(
                List.of("model-a"),
                AdvisorMode.STANDARD,
                List.of(new ToolBenchmarkPrompt("p1", "Use a tool.")),
                List.of()
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isFalse();
            assertThat(row.error()).contains("Ollama chat model is not available");
        });
    }

    @Test
    void compareRunsStandardAndToolSearchWithTheSameFixtureTool() throws Exception {
        Path outputDir = Files.createTempDirectory("tool-comparison-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        AtomicInteger calls = new AtomicInteger();
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            return switch (calls.getAndIncrement()) {
                case 0 -> chatResponse(new AssistantMessage("standard answer"), 4, 2);
                case 1 -> {
                    assertThat(options.getToolCallbacks())
                            .extracting(callback -> callback.getToolDefinition().name())
                            .containsExactly("toolSearchTool");
                    assertThat(options.getToolCallbacks().getFirst().getToolDefinition().inputSchema())
                            .contains("\"arg0\"");
                    yield chatResponse(AssistantMessage.builder()
                                    .content("")
                                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "search-1",
                                        "function",
                                        "toolSearchTool",
                                        "{\"arg0\":\"add numbers\"}")))
                                    .build(),
                            10,
                            2);
                }
                case 2 -> {
                    assertThat(options.getToolCallbacks())
                            .extracting(callback -> callback.getToolDefinition().name())
                            .contains("toolSearchTool", ArithmeticBenchmarkTools.ADD_TOOL_NAME);
                    assertThat(prompt.getInstructions())
                            .anySatisfy(message -> assertThat(message).isInstanceOf(ToolResponseMessage.class));
                    yield chatResponse(AssistantMessage.builder()
                                    .content("")
                                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                                            "add-1",
                                            "function",
                                            ArithmeticBenchmarkTools.ADD_TOOL_NAME,
                                            "{\"left\":2,\"right\":3}")))
                                    .build(),
                            20,
                            4);
                }
                case 3 -> {
                    assertThat(prompt.getInstructions())
                            .anySatisfy(message -> assertThat(message).isInstanceOf(ToolResponseMessage.class));
                    yield chatResponse(new AssistantMessage("The result is 5."), 30, 6);
                }
                default -> throw new AssertionError("Unexpected model invocation");
            };
        });

        ToolBenchmarkComparisonResult result = newService(outputDir, ollamaChatModel, true, "regex")
                .compare(
                        List.of("model-a"),
                        List.of(new ToolBenchmarkPrompt("add", "Add 2 and 3 with a tool.")),
                        List.of(ArithmeticBenchmarkTools.ADD_TOOL_NAME)
                );

        assertThat(result.suite()).isEqualTo(ToolBenchmarkService.COMPARISON_SUITE);
        assertThat(result.toolSearchIndexType()).isEqualTo("regex");
        assertThat(result.standard().advisorMode()).isEqualTo(AdvisorMode.STANDARD);
        assertThat(result.toolSearch().advisorMode()).isEqualTo(AdvisorMode.TOOL_SEARCH);
        assertThat(result.standard().runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.outputText()).isEqualTo("standard answer");
        });
        assertThat(result.toolSearch().runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.selectedToolCalls()).extracting(call -> call.name())
                    .containsExactly("toolSearchTool", ArithmeticBenchmarkTools.ADD_TOOL_NAME);
            assertThat(row.executedToolResponses()).extracting(response -> response.name())
                    .containsExactly("toolSearchTool", ArithmeticBenchmarkTools.ADD_TOOL_NAME);
            assertThat(row.tokensIn()).isEqualTo(60);
            assertThat(row.tokensOut()).isEqualTo(12);
            assertThat(row.outputText()).isEqualTo("The result is 5.");
        });
        assertThat(Files.list(outputDir))
                .anySatisfy(path -> assertThat(path.getFileName().toString())
                        .endsWith("-tool-calling-comparison.json"));
    }

    @Test
    void compareRejectsDisabledOrUnsupportedToolSearchConfiguration() throws Exception {
        Path outputDir = Files.createTempDirectory("tool-comparison-results-");

        ToolBenchmarkService disabled = newService(outputDir, null, false, "regex");
        ToolBenchmarkService lucene = newService(outputDir, null, true, "lucene");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> disabled.compare(
                List.of("model-a"), List.of(new ToolBenchmarkPrompt("p1", "Use a tool.")), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SETACCIO_LAB_TOOL_SEARCH_ENABLED=true");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> lucene.compare(
                List.of("model-a"), List.of(new ToolBenchmarkPrompt("p1", "Use a tool.")), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOOL_SEARCH_INDEX_TYPE=regex");
    }

    private ToolBenchmarkService newService(Path outputDir, OllamaChatModel ollamaChatModel) {
        return newService(outputDir, ollamaChatModel, false, "regex");
    }

    private ToolBenchmarkService newService(Path outputDir, OllamaChatModel ollamaChatModel,
                                            boolean toolSearchEnabled, String toolSearchIndexType) {
        return new ToolBenchmarkService(
                singletonProvider(ollamaChatModel),
                toolCallbackProvider(),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("tool-benchmark-results", "tool-benchmark-comparison-results"),
                Executors.newSingleThreadExecutor(),
                "http://localhost:11434",
                toolSearchEnabled,
                toolSearchIndexType
        );
    }

    private ToolCallbackProvider toolCallbackProvider() {
        BenchmarkToolConfig config = new BenchmarkToolConfig();
        return config.benchmarkToolCallbackProvider(
                config.arithmeticBenchmarkTools(),
                config.fixtureTimeTools("2026-01-15T12:00:00Z"),
                config.fixtureCatalogTools()
        );
    }

    private ChatResponse chatResponse(AssistantMessage assistantMessage, Integer promptTokens, Integer completionTokens) {
        return new ChatResponse(
                List.of(new Generation(assistantMessage)),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build()
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OllamaChatModel> singletonProvider(OllamaChatModel model) {
        ObjectProvider<OllamaChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }
}
