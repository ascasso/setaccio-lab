package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ChatBenchmarkPrompt;
import com.setaccio.lab.model.ChatBenchmarkResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatBenchmarkServiceTest {

    @Test
    void runBuildsRowsForEachPromptAndModelAndWritesJson() throws Exception {
        Path outputDir = Files.createTempDirectory("chat-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isIn("model-a", "model-b");
            return chatResponse("chat benchmark answer", 12, 7);
        });

        ChatBenchmarkService service = newService(outputDir, ollamaChatModel);

        ChatBenchmarkResult result = service.run(
                List.of("model-a", "model-b"),
                AdvisorMode.STANDARD,
                List.of(new ChatBenchmarkPrompt("p1", "Answer directly."))
        );

        assertThat(result.suite()).isEqualTo("chat");
        assertThat(result.provider()).isEqualTo("ollama");
        assertThat(result.advisorMode()).isEqualTo(AdvisorMode.STANDARD);
        assertThat(result.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(result.runs()).hasSize(2);
        assertThat(result.runs()).allSatisfy(row -> {
            assertThat(row.provider()).isEqualTo("ollama");
            assertThat(row.promptId()).isEqualTo("p1");
            assertThat(row.promptText()).isEqualTo("Answer directly.");
            assertThat(row.advisorMode()).isEqualTo(AdvisorMode.STANDARD);
            assertThat(row.outputText()).isEqualTo("chat benchmark answer");
            assertThat(row.tokensIn()).isEqualTo(12);
            assertThat(row.tokensOut()).isEqualTo(7);
            assertThat(row.success()).isTrue();
        });
        assertThat(Files.list(outputDir))
                .anySatisfy(path -> assertThat(path.getFileName().toString()).endsWith("-chat.json"));
    }

    @Test
    void runLeavesTokenFieldsNullWhenUsageIsUnavailable() throws Exception {
        Path outputDir = Files.createTempDirectory("chat-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(new Generation(new AssistantMessage("answer")));
        when(response.getMetadata()).thenReturn(null);
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(response);

        ChatBenchmarkService service = newService(outputDir, ollamaChatModel);

        ChatBenchmarkResult result = service.run(
                List.of("model-a"),
                AdvisorMode.STANDARD,
                List.of(new ChatBenchmarkPrompt("p1", "Answer directly."))
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isTrue();
            assertThat(row.tokensIn()).isNull();
            assertThat(row.tokensOut()).isNull();
        });
    }

    @Test
    void runReturnsFailureRowsWhenOllamaModelIsUnavailable() throws Exception {
        Path outputDir = Files.createTempDirectory("chat-results-");
        ChatBenchmarkService service = newService(outputDir, null);

        ChatBenchmarkResult result = service.run(
                List.of("model-a"),
                AdvisorMode.STANDARD,
                List.of(new ChatBenchmarkPrompt("p1", "Answer directly."))
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isFalse();
            assertThat(row.error()).contains("Ollama chat model is not available");
            assertThat(row.promptId()).isEqualTo("p1");
        });
    }

    @Test
    void runReturnsFailureRowsWhenOllamaReturnsNoChatResult() throws Exception {
        Path outputDir = Files.createTempDirectory("chat-results-");
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("stub-model").build());
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        ChatBenchmarkResult result = newService(outputDir, ollamaChatModel).run(
                List.of("model-a"),
                AdvisorMode.STANDARD,
                List.of(new ChatBenchmarkPrompt("p1", "Answer directly."))
        );

        assertThat(result.runs()).singleElement().satisfies(row -> {
            assertThat(row.success()).isFalse();
            assertThat(row.outputText()).isNull();
            assertThat(row.error()).isEqualTo("Ollama returned no chat result");
        });
    }

    private ChatBenchmarkService newService(Path outputDir, OllamaChatModel ollamaChatModel) {
        return new ChatBenchmarkService(
                singletonProvider(ollamaChatModel),
                new LabResultWriter(new ObjectMapper().findAndRegisterModules(), outputDir.toString()),
                new ConcurrentMapCacheManager("chat-benchmark-results"),
                Executors.newSingleThreadExecutor(),
                "http://localhost:11434"
        );
    }

    private ChatResponse chatResponse(String text, Integer promptTokens, Integer completionTokens) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
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
