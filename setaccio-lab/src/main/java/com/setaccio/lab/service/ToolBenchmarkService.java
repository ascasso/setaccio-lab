package com.setaccio.lab.service;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.toolsearch.ToolSearchTool;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class ToolBenchmarkService {

    public static final String SUITE = "tool-calling";
    public static final String COMPARISON_SUITE = "tool-calling-comparison";
    private static final String PROVIDER = "ollama";
    private static final String TOOL_SEARCH_INDEX_REGEX = "regex";
    private static final int TOOL_SEARCH_MAX_RESULTS = 5;

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
    private final boolean toolSearchEnabled;
    private final String toolSearchIndexType;

    public ToolBenchmarkService(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            ToolCallbackProvider toolCallbackProvider,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("toolBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.chat.client.tool-search-advisor.enabled:false}") boolean toolSearchEnabled,
            @Value("${spring.ai.chat.client.tool-search-advisor.tool-index-type:regex}") String toolSearchIndexType) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.toolCallbackProvider = toolCallbackProvider;
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.toolSearchEnabled = toolSearchEnabled;
        this.toolSearchIndexType = toolSearchIndexType;
    }

    public ToolBenchmarkResult run(List<String> models, AdvisorMode advisorMode,
                                   List<ToolBenchmarkPrompt> prompts, List<String> requestedTools) {
        if (advisorMode != AdvisorMode.STANDARD) {
            throw new IllegalArgumentException("Use advisorMode=compare for Tool Search comparison runs");
        }

        ToolSelection selection = selectToolCallbacks(requestedTools);
        ToolBenchmarkResult result = runMode(models, AdvisorMode.STANDARD, prompts, selection);
        writeAndCache(SUITE, result.startedAt(), result, "tool-benchmark-results");
        return result;
    }

    public ToolBenchmarkComparisonResult compare(List<String> models, List<ToolBenchmarkPrompt> prompts,
                                                  List<String> requestedTools) {
        validateToolSearchConfiguration();

        Instant startedAt = Instant.now();
        ToolSelection selection = selectToolCallbacks(requestedTools);
        ToolBenchmarkResult standard = runMode(models, AdvisorMode.STANDARD, prompts, selection);
        ToolBenchmarkResult toolSearch = runMode(models, AdvisorMode.TOOL_SEARCH, prompts, selection);
        ToolBenchmarkComparisonResult result = new ToolBenchmarkComparisonResult(
                COMPARISON_SUITE,
                PROVIDER,
                TOOL_SEARCH_INDEX_REGEX,
                startedAt,
                Instant.now(),
                hostName(),
                ollamaBaseUrl,
                selection.selectedTools(),
                selection.availableTools(),
                standard,
                toolSearch
        );
        writeAndCache(COMPARISON_SUITE, result.startedAt(), result, "tool-benchmark-comparison-results");
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

    private ToolBenchmarkResult runMode(List<String> models, AdvisorMode advisorMode,
                                        List<ToolBenchmarkPrompt> prompts, ToolSelection selection) {
        Instant startedAt = Instant.now();
        List<CompletableFuture<ToolBenchmarkRow>> futures = new ArrayList<>();
        for (ToolBenchmarkPrompt prompt : prompts) {
            for (String model : models) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> runOne(model, advisorMode, prompt, selection.selectedTools(), selection.selectedCallbacks()),
                        executorService));
            }
        }

        List<ToolBenchmarkRow> runs = futures.stream().map(CompletableFuture::join).toList();
        return new ToolBenchmarkResult(
                SUITE,
                PROVIDER,
                advisorMode,
                startedAt,
                Instant.now(),
                hostName(),
                ollamaBaseUrl,
                selection.selectedTools(),
                selection.availableTools(),
                runs
        );
    }

    private ToolBenchmarkRow runOne(String model, AdvisorMode advisorMode, ToolBenchmarkPrompt prompt,
                                    List<String> selectedTools, List<ToolCallback> selectedCallbacks) {
        long started = System.nanoTime();
        RecordingToolCallAdvisor recorder = new RecordingToolCallAdvisor();
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return failed(model, prompt, advisorMode, selectedTools, recorder, started,
                        "Ollama chat model is not available");
            }

            Advisor toolAdvisor = toolAdvisor(advisorMode);
            ChatResponse response = invoke(ollamaChatModel, model, prompt, selectedCallbacks, toolAdvisor, recorder);
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return ToolBenchmarkRow.ok(PROVIDER, model, prompt, advisorMode, selectedTools,
                    recorder.selectedToolCalls(), recorder.executedToolResponses(), elapsedMillis(started),
                    recorder.promptTokens(), recorder.completionTokens(), text);
        } catch (Exception e) {
            logger.warn("Tool benchmark failed for model={} promptId={}: {}", model, prompt.id(), e.getMessage());
            return failed(model, prompt, advisorMode, selectedTools, recorder, started, e.getMessage());
        }
    }

    ChatResponse invoke(OllamaChatModel ollamaChatModel, String model, ToolBenchmarkPrompt prompt,
                        List<ToolCallback> selectedCallbacks, Advisor toolAdvisor,
                        RecordingToolCallAdvisor recorder) {
        ChatClient.ChatClientRequestSpec request = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(toolAdvisor, recorder)
                .build()
                .prompt(prompt.text())
                .options(ChatOptions.builder().model(model))
                .tools(selectedCallbacks);
        if (toolAdvisor instanceof ToolSearchToolCallingAdvisor) {
            String sessionId = UUID.randomUUID().toString();
            request = request.advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .toolContext(Map.of(ToolSearchTool.TOOL_SEARCH_TOOL_SESSION_ID_KEY, sessionId));
        }
        return request.call().chatResponse();
    }

    private ToolBenchmarkRow failed(String model, ToolBenchmarkPrompt prompt, AdvisorMode advisorMode,
                                    List<String> selectedTools, RecordingToolCallAdvisor recorder,
                                    long started, String error) {
        List<String> toolErrors = error == null
                ? List.of()
                : List.of(error);
        return ToolBenchmarkRow.fail(PROVIDER, model, prompt, advisorMode, selectedTools,
                recorder.selectedToolCalls(), recorder.executedToolResponses(), toolErrors,
                elapsedMillis(started), error);
    }

    private Advisor toolAdvisor(AdvisorMode advisorMode) {
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return switch (advisorMode) {
            case STANDARD -> ToolCallingAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .build();
            case TOOL_SEARCH -> ToolSearchToolCallingAdvisor.builder()
                    .toolCallingManager(toolCallingManager)
                    .toolIndex(new RegexToolIndex())
                    .maxResults(TOOL_SEARCH_MAX_RESULTS)
                    .build();
            case COMPARE -> throw new IllegalArgumentException("Comparison mode does not map to one advisor");
        };
    }

    private ToolSelection selectToolCallbacks(List<String> requestedTools) {
        List<ToolCallback> availableCallbacks = Arrays.asList(toolCallbackProvider.getToolCallbacks());
        List<String> availableTools = availableCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
        List<ToolCallback> selectedCallbacks = selectCallbacks(availableCallbacks, requestedTools);
        List<String> selectedTools = selectedCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .toList();
        return new ToolSelection(availableTools, selectedTools, selectedCallbacks);
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

    private void validateToolSearchConfiguration() {
        if (!toolSearchEnabled) {
            throw new IllegalArgumentException(
                    "Tool Search comparison requires SETACCIO_LAB_TOOL_SEARCH_ENABLED=true");
        }
        if (!TOOL_SEARCH_INDEX_REGEX.equals(normalizeToolSearchIndexType())) {
            throw new IllegalArgumentException("Only SETACCIO_LAB_TOOL_SEARCH_INDEX_TYPE=regex is implemented in this slice");
        }
    }

    private void writeAndCache(String suite, Instant startedAt, Object result, String cacheName) {
        labResultWriter.write(suite, startedAt, result);
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(startedAt.toString(), result);
        }
    }

    private String normalizeToolSearchIndexType() {
        return toolSearchIndexType == null ? "" : toolSearchIndexType.trim().toLowerCase(Locale.ROOT);
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

    private record ToolSelection(
            List<String> availableTools,
            List<String> selectedTools,
            List<ToolCallback> selectedCallbacks
    ) {}
}
