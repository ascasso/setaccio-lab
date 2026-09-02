package com.setaccio.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkExpectation;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.model.ToolBenchmarkRow;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
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
    private static final String EXECUTION_PARALLEL = "parallel";
    private static final String EXECUTION_PAIRED_SEQUENTIAL = "paired_sequential";

    private static final Logger logger = LoggerFactory.getLogger(ToolBenchmarkService.class);

    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final ToolCallbackProvider toolCallbackProvider;
    private final LabResultWriter labResultWriter;
    private final CacheManager cacheManager;
    private final ExecutorService executorService;
    private final String ollamaBaseUrl;
    private final boolean toolSearchEnabled;
    private final String toolSearchIndexType;
    private final ToolBenchmarkTraceEvaluator traceEvaluator;

    public ToolBenchmarkService(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            ToolCallbackProvider toolCallbackProvider,
            ObjectMapper objectMapper,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("toolBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.chat.client.tool-search-advisor.enabled:false}") boolean toolSearchEnabled,
            @Value("${spring.ai.chat.client.tool-search-advisor.tool-index-type:regex}") String toolSearchIndexType) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.toolCallbackProvider = toolCallbackProvider;
        this.traceEvaluator = new ToolBenchmarkTraceEvaluator(objectMapper);
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.toolSearchEnabled = toolSearchEnabled;
        this.toolSearchIndexType = toolSearchIndexType;
    }

    public ToolBenchmarkResult run(List<String> models, AdvisorMode advisorMode,
                                   List<ToolBenchmarkPrompt> prompts, List<String> requestedTools) {
        return run(models, advisorMode, prompts, requestedTools, ToolBenchmarkRunSettings.standardDefaults());
    }

    public ToolBenchmarkResult run(List<String> models, AdvisorMode advisorMode,
                                   List<ToolBenchmarkPrompt> prompts, List<String> requestedTools,
                                   ToolBenchmarkRunSettings runSettings) {
        if (advisorMode != AdvisorMode.STANDARD) {
            throw new IllegalArgumentException("Use advisorMode=compare for Tool Search comparison runs");
        }

        ToolSelection selection = selectToolCallbacks(requestedTools);
        validateExpectations(prompts, selection);
        ToolBenchmarkResult result = runMode(models, AdvisorMode.STANDARD, prompts, selection, runSettings);
        writeAndCache(SUITE, result.startedAt(), result, "tool-benchmark-results");
        return result;
    }

    public ToolBenchmarkComparisonResult compare(List<String> models, List<ToolBenchmarkPrompt> prompts,
                                                  List<String> requestedTools) {
        return compare(models, prompts, requestedTools, ToolBenchmarkRunSettings.comparisonDefaults());
    }

    public ToolBenchmarkComparisonResult compare(List<String> models, List<ToolBenchmarkPrompt> prompts,
                                                  List<String> requestedTools,
                                                  ToolBenchmarkRunSettings runSettings) {
        validateToolSearchConfiguration();

        Instant startedAt = Instant.now();
        ToolSelection selection = selectToolCallbacks(requestedTools);
        validateExpectations(prompts, selection);
        List<ToolBenchmarkRow> standardRows = new ArrayList<>();
        List<ToolBenchmarkRow> toolSearchRows = new ArrayList<>();
        int pairSequence = 1;
        for (ToolBenchmarkPrompt prompt : prompts) {
            for (String model : models) {
                for (int repetition = 1; repetition <= runSettings.repetitions(); repetition++) {
                    String pairId = String.format(Locale.ROOT, "pair-%04d", pairSequence++);
                    List<AdvisorMode> modes = runSettings.comparisonOrder().modesFor(repetition);
                    for (int index = 0; index < modes.size(); index++) {
                        AdvisorMode mode = modes.get(index);
                        ToolBenchmarkRow row = runOne(
                                model,
                                mode,
                                prompt,
                                selection.selectedTools(),
                                selection.selectedCallbacks(),
                                runSettings,
                                repetition,
                                index + 1,
                                pairId
                        );
                        if (mode == AdvisorMode.STANDARD) {
                            standardRows.add(row);
                        } else {
                            toolSearchRows.add(row);
                        }
                    }
                }
            }
        }
        Instant finishedAt = Instant.now();
        ToolBenchmarkResult standard = result(
                AdvisorMode.STANDARD, startedAt, finishedAt, selection, runSettings,
                EXECUTION_PAIRED_SEQUENTIAL, standardRows);
        ToolBenchmarkResult toolSearch = result(
                AdvisorMode.TOOL_SEARCH, startedAt, finishedAt, selection, runSettings,
                EXECUTION_PAIRED_SEQUENTIAL, toolSearchRows);
        ToolBenchmarkComparisonResult result = new ToolBenchmarkComparisonResult(
                COMPARISON_SUITE,
                PROVIDER,
                TOOL_SEARCH_INDEX_REGEX,
                startedAt,
                finishedAt,
                hostName(),
                ollamaBaseUrl,
                runSettings,
                EXECUTION_PAIRED_SEQUENTIAL,
                selection.selectedTools(),
                selection.availableTools(),
                standard,
                toolSearch
        );
        writeAndCache(COMPARISON_SUITE, result.startedAt(), result, "tool-benchmark-comparison-results");
        return result;
    }

    public static List<ToolBenchmarkPrompt> defaultPrompts() {
        return ToolBenchmarkCases.defaults();
    }

    public static List<String> defaultToolNames() {
        return ToolBenchmarkCases.toolNames();
    }

    private ToolBenchmarkResult runMode(List<String> models, AdvisorMode advisorMode,
                                        List<ToolBenchmarkPrompt> prompts, ToolSelection selection,
                                        ToolBenchmarkRunSettings runSettings) {
        Instant startedAt = Instant.now();
        List<CompletableFuture<ToolBenchmarkRow>> futures = new ArrayList<>();
        for (ToolBenchmarkPrompt prompt : prompts) {
            for (String model : models) {
                for (int repetition = 1; repetition <= runSettings.repetitions(); repetition++) {
                    int currentRepetition = repetition;
                    futures.add(CompletableFuture.supplyAsync(
                            () -> runOne(
                                    model,
                                    advisorMode,
                                    prompt,
                                    selection.selectedTools(),
                                    selection.selectedCallbacks(),
                                    runSettings,
                                    currentRepetition,
                                    null,
                                    null),
                            executorService));
                }
            }
        }

        List<ToolBenchmarkRow> runs = futures.stream().map(CompletableFuture::join).toList();
        return result(advisorMode, startedAt, Instant.now(), selection, runSettings, EXECUTION_PARALLEL, runs);
    }

    private ToolBenchmarkResult result(
            AdvisorMode advisorMode,
            Instant startedAt,
            Instant finishedAt,
            ToolSelection selection,
            ToolBenchmarkRunSettings runSettings,
            String executionStrategy,
            List<ToolBenchmarkRow> runs) {
        return new ToolBenchmarkResult(
                SUITE,
                PROVIDER,
                advisorMode,
                startedAt,
                finishedAt,
                hostName(),
                ollamaBaseUrl,
                runSettings,
                executionStrategy,
                selection.selectedTools(),
                selection.availableTools(),
                runs
        );
    }

    private ToolBenchmarkRow runOne(String model, AdvisorMode advisorMode, ToolBenchmarkPrompt prompt,
                                    List<String> selectedTools, List<ToolCallback> selectedCallbacks,
                                    ToolBenchmarkRunSettings runSettings, int repetition,
                                    Integer pairExecutionOrder, String comparisonPairId) {
        long started = System.nanoTime();
        RecordingToolCallAdvisor recorder = new RecordingToolCallAdvisor();
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return row(model, prompt, advisorMode, selectedTools, recorder, started, runSettings,
                        repetition, pairExecutionOrder, comparisonPairId, false, null,
                        "Ollama chat model is not available");
            }

            Advisor toolAdvisor = toolAdvisor(advisorMode);
            ChatResponse response = invoke(
                    ollamaChatModel,
                    model,
                    prompt,
                    selectedCallbacks,
                    toolAdvisor,
                    recorder,
                    runSettings,
                    repetition
            );
            if (response == null || response.getResult() == null) {
                return row(model, prompt, advisorMode, selectedTools, recorder, started, runSettings,
                        repetition, pairExecutionOrder, comparisonPairId, false, null,
                        "Ollama returned no chat result");
            }
            String text = response.getResult().getOutput().getText();
            return row(model, prompt, advisorMode, selectedTools, recorder, started, runSettings,
                    repetition, pairExecutionOrder, comparisonPairId, true, text, null);
        } catch (Exception e) {
            logger.warn("Tool benchmark failed for model={} promptId={}: {}", model, prompt.id(), e.getMessage());
            return row(model, prompt, advisorMode, selectedTools, recorder, started, runSettings,
                    repetition, pairExecutionOrder, comparisonPairId, false, null, e.getMessage());
        }
    }

    ChatResponse invoke(OllamaChatModel ollamaChatModel, String model, ToolBenchmarkPrompt prompt,
                        List<ToolCallback> selectedCallbacks, Advisor toolAdvisor,
                        RecordingToolCallAdvisor recorder, ToolBenchmarkRunSettings runSettings,
                        int repetition) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                .model(model)
                .temperature(runSettings.temperature())
                .seed(runSettings.seedFor(repetition));
        if (runSettings.maxTokens() != null) {
            options.numPredict(runSettings.maxTokens());
        }
        ChatClient.ChatClientRequestSpec request = ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(toolAdvisor, recorder)
                .build()
                .prompt(prompt.text())
                .options(options)
                .tools(selectedCallbacks);
        if (toolAdvisor instanceof ToolSearchToolCallingAdvisor) {
            String sessionId = UUID.randomUUID().toString();
            request = request.advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .toolContext(Map.of(ToolSearchTool.TOOL_SEARCH_TOOL_SESSION_ID_KEY, sessionId));
        }
        return request.call().chatResponse();
    }

    private ToolBenchmarkRow row(
            String model,
            ToolBenchmarkPrompt prompt,
            AdvisorMode advisorMode,
            List<String> selectedTools,
            RecordingToolCallAdvisor recorder,
            long started,
            ToolBenchmarkRunSettings runSettings,
            int repetition,
            Integer pairExecutionOrder,
            String comparisonPairId,
            boolean success,
            String outputText,
            String error) {
        ToolBenchmarkTraceEvaluator.Assessment assessment = traceEvaluator.assess(
                prompt,
                advisorMode,
                success,
                outputText,
                recorder.selectedToolCalls(),
                recorder.executedToolResponses()
        );
        List<String> toolErrors = new ArrayList<>(assessment.toolErrors());
        if (error != null && !error.isBlank()) {
            toolErrors.add(error);
        }
        return new ToolBenchmarkRow(
                PROVIDER,
                model,
                prompt.id(),
                prompt.text(),
                prompt.expectation(),
                advisorMode,
                repetition,
                pairExecutionOrder,
                comparisonPairId,
                runSettings.seedFor(repetition),
                selectedTools,
                recorder.selectedToolCalls(),
                recorder.executedToolResponses(),
                assessment.toolSearchObservations(),
                List.copyOf(toolErrors),
                assessment.assertions(),
                assessment.contractPassed(),
                elapsedMillis(started),
                recorder.promptTokens(),
                recorder.completionTokens(),
                outputText,
                success,
                error
        );
    }

    private Advisor toolAdvisor(AdvisorMode advisorMode) {
        ToolCallingManager toolCallingManager = ToolCallLimitPolicy.toolCallingManager();
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

    private void validateExpectations(List<ToolBenchmarkPrompt> prompts, ToolSelection selection) {
        Set<String> available = new LinkedHashSet<>(selection.availableTools());
        Set<String> selected = new LinkedHashSet<>(selection.selectedTools());
        Set<String> unknown = new LinkedHashSet<>();
        Set<String> requiredButNotSelected = new LinkedHashSet<>();
        for (ToolBenchmarkPrompt prompt : prompts) {
            ToolBenchmarkExpectation expectation = prompt.expectation();
            for (String tool : expectation.requiredExecutedTools()) {
                if (!available.contains(tool)) {
                    unknown.add(tool);
                } else if (!selected.contains(tool)) {
                    requiredButNotSelected.add(tool);
                }
            }
            for (String tool : expectation.forbiddenExecutedTools()) {
                if (!available.contains(tool)) {
                    unknown.add(tool);
                }
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown tools in prompt expectations: " + String.join(", ", unknown));
        }
        if (!requiredButNotSelected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prompt expectations require tools not selected for this run: "
                            + String.join(", ", requiredButNotSelected));
        }
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
