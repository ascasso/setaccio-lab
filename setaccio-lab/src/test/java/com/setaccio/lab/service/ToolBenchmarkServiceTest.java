package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.config.BenchmarkToolConfig;
import com.setaccio.lab.model.AdvisorMode;
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
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1",
                                "function",
                                ArithmeticBenchmarkTools.ADD_TOOL_NAME,
                                "{\"left\":2,\"right\":3}")))
                        .build())));
            }
            assertThat(prompt.getInstructions())
                    .anySatisfy(message -> assertThat(message).isInstanceOf(ToolResponseMessage.class));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("The result is 5."))));
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

    private ToolBenchmarkService newService(Path outputDir, OllamaChatModel ollamaChatModel) {
        return new ToolBenchmarkService(
                singletonProvider(ollamaChatModel),
                toolCallbackProvider(),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("tool-benchmark-results"),
                Executors.newSingleThreadExecutor(),
                "http://localhost:11434"
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

    @SuppressWarnings("unchecked")
    private ObjectProvider<OllamaChatModel> singletonProvider(OllamaChatModel model) {
        ObjectProvider<OllamaChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }
}
