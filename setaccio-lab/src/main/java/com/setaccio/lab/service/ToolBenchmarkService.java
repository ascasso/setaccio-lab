package com.setaccio.lab.service;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.tool.ArithmeticBenchmarkTools;
import com.setaccio.lab.tool.FixtureCatalogTools;
import com.setaccio.lab.tool.FixtureTimeTools;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class ToolBenchmarkService {

    public static final String SUITE = "tool-calling";
    private static final String PROVIDER = "ollama";

    private static final Logger logger = LoggerFactory.getLogger(ToolBenchmarkService.class);

    private static final List<ToolBenchmarkPrompt> DEFAULT_PROMPTS = List.of(
            new ToolBenchmarkPrompt(
                    "arithmetic-add",
                    "Use the available tools to add 17.25 and 4.75, then answer with the result."),
            new ToolBenchmarkPrompt(
                    "fixed-utc-time",
                    "Use the available tools to report the fixed benchmark UTC timestamp."),
            new ToolBenchmarkPrompt(
                    "fixed-zone-time",
                    "Use the available tools to convert the fixed benchmark timestamp to America/Los_Angeles."),
            new ToolBenchmarkPrompt(
                    "catalog-lookup",
                    "Use the available tools to look up the catalog fixture fixture-policy-faq and summarize it."),
            new ToolBenchmarkPrompt(
                    "catalog-list",
                    "Use the available tools to list catalog fixtures in the document category.")
    );

    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final ToolCallbackProvider toolCallbackProvider;
    private final LabResultWriter labResultWriter;
    private final CacheManager cacheManager;
    private final ExecutorService executorService;
    private final String ollamaBaseUrl;

    public ToolBenchmarkService(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            ToolCallbackProvider toolCallbackProvider,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("toolBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.toolCallbackProvider = toolCallbackProvider;
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public ToolBenchmarkResult run(List<String> models, AdvisorMode advisorMode,
                                   List<ToolBenchmarkPrompt> prompts, List<String> requestedTools) {
        if (advisorMode != AdvisorMode.STANDARD) {
            throw new IllegalArgumentException("Only advisorMode=standard is implemented in this slice");
        }

        Instant startedAt = Instant.now();
        List<ToolCallback> availableCallbacks = Arrays.asList(toolCallbackProvider.getToolCallbacks());
        List<String> availableTools = availableCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
        List<ToolCallback> selectedCallbacks = selectCallbacks(availableCallbacks, requestedTools);
        List<String> selectedTools = selectedCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        List<CompletableFuture<ToolBenchmarkRow>> futures = new ArrayList<>();
        for (ToolBenchmarkPrompt prompt : prompts) {
            for (String model : models) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> runOne(model, advisorMode, prompt, selectedTools, selectedCallbacks),
                        executorService));
            }
        }

        List<ToolBenchmarkRow> runs = futures.stream().map(CompletableFuture::join).toList();
        ToolBenchmarkResult result = new ToolBenchmarkResult(
                SUITE,
                PROVIDER,
                advisorMode,
                startedAt,
                Instant.now(),
                hostName(),
                ollamaBaseUrl,
                selectedTools,
                availableTools,
                runs
        );

        labResultWriter.write(SUITE, result.startedAt(), result);
        Cache cache = cacheManager.getCache("tool-benchmark-results");
        if (cache != null) {
            cache.put(result.startedAt().toString(), result);
        }
        return result;
    }

    public static List<ToolBenchmarkPrompt> defaultPrompts() {
        return DEFAULT_PROMPTS;
    }

    public static List<String> defaultToolNames() {
        return List.of(
                ArithmeticBenchmarkTools.ADD_TOOL_NAME,
                ArithmeticBenchmarkTools.MULTIPLY_TOOL_NAME,
                FixtureTimeTools.FIXED_UTC_NOW_TOOL_NAME,
                FixtureTimeTools.FIXED_TIME_FOR_ZONE_TOOL_NAME,
                FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME,
                FixtureCatalogTools.LIST_ITEMS_TOOL_NAME
        );
    }

    private ToolBenchmarkRow runOne(String model, AdvisorMode advisorMode, ToolBenchmarkPrompt prompt,
                                    List<String> selectedTools, List<ToolCallback> selectedCallbacks) {
        long started = System.nanoTime();
        RecordingToolCallingAdvisor advisor = new RecordingToolCallingAdvisor(ToolCallingManager.builder().build());
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return failed(model, prompt, advisorMode, selectedTools, advisor, started,
                        "Ollama chat model is not available");
            }

            ChatResponse response = invoke(ollamaChatModel, model, prompt, selectedCallbacks, advisor);
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return ToolBenchmarkRow.ok(PROVIDER, model, prompt, advisorMode, selectedTools,
                    advisor.selectedToolCalls(), advisor.executedToolResponses(), elapsedMillis(started),
                    advisor.promptTokens(), advisor.completionTokens(), text);
        } catch (Exception e) {
            logger.warn("Tool benchmark failed for model={} prompt={}: {}", model, prompt.id(), e.getMessage());
            return failed(model, prompt, advisorMode, selectedTools, advisor, started, e.getMessage());
        }
    }

    ChatResponse invoke(OllamaChatModel ollamaChatModel, String model, ToolBenchmarkPrompt prompt,
                        List<ToolCallback> selectedCallbacks, RecordingToolCallingAdvisor advisor) {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(advisor)
                .build()
                .prompt(prompt.text())
                .options(ChatOptions.builder().model(model))
                .tools(selectedCallbacks)
                .call()
                .chatResponse();
    }

    private ToolBenchmarkRow failed(String model, ToolBenchmarkPrompt prompt, AdvisorMode advisorMode,
                                    List<String> selectedTools, RecordingToolCallingAdvisor advisor,
                                    long started, String error) {
        List<String> toolErrors = error == null
                ? List.of()
                : List.of(error);
        return ToolBenchmarkRow.fail(PROVIDER, model, prompt, advisorMode, selectedTools,
                advisor.selectedToolCalls(), advisor.executedToolResponses(), toolErrors,
                elapsedMillis(started), error);
    }

    private List<ToolCallback> selectCallbacks(List<ToolCallback> availableCallbacks, List<String> requestedTools) {
        if (requestedTools == null || requestedTools.isEmpty()) {
            return availableCallbacks;
        }
        Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
        for (ToolCallback callback : availableCallbacks) {
            callbacksByName.put(callback.getToolDefinition().name(), callback);
        }

        List<ToolCallback> selected = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String requestedTool : requestedTools) {
            ToolCallback callback = callbacksByName.get(requestedTool);
            if (callback == null) {
                unknown.add(requestedTool);
            } else {
                selected.add(callback);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown requested tools: " + String.join(", ", unknown));
        }
        return selected;
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
