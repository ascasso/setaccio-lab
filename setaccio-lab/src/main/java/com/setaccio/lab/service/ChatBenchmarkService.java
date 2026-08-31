package com.setaccio.lab.service;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ChatBenchmarkPrompt;
import com.setaccio.lab.model.ChatBenchmarkResult;
import com.setaccio.lab.model.ChatBenchmarkRow;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class ChatBenchmarkService {

    public static final String SUITE = "chat";
    private static final String PROVIDER = "ollama";

    private static final Logger logger = LoggerFactory.getLogger(ChatBenchmarkService.class);

    private static final List<ChatBenchmarkPrompt> DEFAULT_PROMPTS = List.of(
            new ChatBenchmarkPrompt("concise-summary",
                    "Summarize the purpose of a local AI benchmark harness in three concise bullet points."),
            new ChatBenchmarkPrompt("classification-policy",
                    "Classify this file-management request as organize, search, compare, or unknown: Find duplicate receipts from last quarter."),
            new ChatBenchmarkPrompt("json-shape",
                    "Return a compact JSON object with fields category and confidence for this text: warranty invoice for laptop repair.")
    );

    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final LabResultWriter labResultWriter;
    private final CacheManager cacheManager;
    private final ExecutorService executorService;
    private final String ollamaBaseUrl;

    public ChatBenchmarkService(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("chatBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public ChatBenchmarkResult run(List<String> models, AdvisorMode advisorMode, List<ChatBenchmarkPrompt> prompts) {
        if (advisorMode != AdvisorMode.STANDARD) {
            throw new IllegalArgumentException("Only advisorMode=standard is implemented in this slice");
        }

        Instant startedAt = Instant.now();
        List<CompletableFuture<ChatBenchmarkRow>> futures = new ArrayList<>();
        for (ChatBenchmarkPrompt prompt : prompts) {
            for (String model : models) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> runOne(model, advisorMode, prompt),
                        executorService));
            }
        }

        List<ChatBenchmarkRow> runs = futures.stream().map(CompletableFuture::join).toList();
        ChatBenchmarkResult result = new ChatBenchmarkResult(
                SUITE,
                PROVIDER,
                advisorMode,
                startedAt,
                Instant.now(),
                hostName(),
                ollamaBaseUrl,
                runs
        );

        labResultWriter.write(SUITE, result.startedAt(), result);
        Cache cache = cacheManager.getCache("chat-benchmark-results");
        if (cache != null) {
            cache.put(result.startedAt().toString(), result);
        }
        return result;
    }

    public static List<ChatBenchmarkPrompt> defaultPrompts() {
        return DEFAULT_PROMPTS;
    }

    private ChatBenchmarkRow runOne(String model, AdvisorMode advisorMode, ChatBenchmarkPrompt prompt) {
        long started = System.nanoTime();
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return failed(model, prompt, advisorMode, started, "Ollama chat model is not available");
            }

            ChatResponse response = invoke(ollamaChatModel, model, prompt);
            if (response == null || response.getResult() == null) {
                return failed(model, prompt, advisorMode, started, "Ollama returned no chat result");
            }
            String text = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null
                    ? null
                    : response.getMetadata().getUsage();
            if (usage instanceof EmptyUsage) {
                usage = null;
            }
            return ChatBenchmarkRow.ok(PROVIDER, model, prompt, advisorMode, elapsedMillis(started),
                    promptTokens(usage), completionTokens(usage), text);
        } catch (Exception e) {
            logger.warn("Chat benchmark failed for model={} promptId={}: {}", model, prompt.id(), e.getMessage());
            return failed(model, prompt, advisorMode, started, e.getMessage());
        }
    }

    ChatResponse invoke(OllamaChatModel ollamaChatModel, String model, ChatBenchmarkPrompt prompt) {
        return ChatClient.builder(ollamaChatModel)
                .build()
                .prompt(prompt.text())
                .options(ChatOptions.builder().model(model))
                .call()
                .chatResponse();
    }

    private ChatBenchmarkRow failed(String model, ChatBenchmarkPrompt prompt, AdvisorMode advisorMode,
                                    long started, String error) {
        return ChatBenchmarkRow.fail(PROVIDER, model, prompt, advisorMode, elapsedMillis(started), error);
    }

    private Integer promptTokens(Usage usage) {
        return usage == null ? null : usage.getPromptTokens();
    }

    private Integer completionTokens(Usage usage) {
        return usage == null ? null : usage.getCompletionTokens();
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
