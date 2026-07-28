package com.setaccio.lab.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.fixture.ToolBenchmarkCases;
import com.setaccio.lab.model.ToolBenchmarkComparisonOrder;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRunSettings;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ToolSearchMatrixProtocol {

    static final String SUITE = "tool-search-matrix";
    static final String EXECUTION_ENGINE = "spring-ai-direct";
    static final String EXECUTION_STRATEGY = "paired_sequential";
    static final String INDEX_TYPE = "regex";
    static final String PULL_MODEL_STRATEGY = "never";
    static final List<String> MODELS = List.of("gemma4:e2b", "granite4.1:3b", "qwen3.5:0.8b");
    static final List<String> CASE_IDS = List.of(
            "arithmetic-add",
            "catalog-lookup",
            "catalog-multi-step",
            "no-applicable-domain-tool",
            "deterministic-tool-failure");
    static final ToolBenchmarkRunSettings SETTINGS = new ToolBenchmarkRunSettings(
            2, 0.0, 42, null, ToolBenchmarkComparisonOrder.ALTERNATE);
    static final List<Map<String, Object>> ISSUES = List.of(
            Map.of("number", 20, "effect", "ToolSearchResponse.toolReferences normalization fix"),
            Map.of("number", 21, "effect", "chat no-result correctness fix; not a direct tool scoring change"));

    private ToolSearchMatrixProtocol() {}

    static List<ToolBenchmarkPrompt> canonicalPrompts() {
        Map<String, ToolBenchmarkPrompt> byId = new LinkedHashMap<>();
        ToolBenchmarkCases.defaults().forEach(prompt -> byId.put(prompt.id(), prompt));
        List<ToolBenchmarkPrompt> prompts = new ArrayList<>();
        for (String caseId : CASE_IDS) {
            ToolBenchmarkPrompt prompt = byId.get(caseId);
            if (prompt == null) {
                throw new IllegalStateException("Canonical case is missing: " + caseId);
            }
            prompts.add(prompt);
        }
        validateCanonicalFailureMarker(prompts);
        return List.copyOf(prompts);
    }

    static List<String> toolNames() {
        return ToolBenchmarkCases.toolNames();
    }

    static String canonicalExpectationSha256(
            ObjectMapper objectMapper,
            List<ToolBenchmarkPrompt> prompts) {
        try {
            return EvidenceIntegrity.sha256(objectMapper.writeValueAsBytes(prompts));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fingerprint canonical Tool Search expectations", e);
        }
    }

    static Map<String, Object> manifestSettings(
            ObjectMapper objectMapper,
            List<ToolBenchmarkPrompt> prompts,
            List<String> tools,
            ToolBenchmarkComparisonResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("issues", ISSUES);
        settings.put("models", MODELS);
        settings.put("caseIds", CASE_IDS);
        settings.put("prompts", prompts);
        settings.put("toolNames", tools);
        settings.put("runSettings", SETTINGS);
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("toolSearchIndexType", result.toolSearchIndexType());
        settings.put("ollamaBaseUrl", result.ollamaBaseUrl());
        settings.put("pullModelStrategy", PULL_MODEL_STRATEGY);
        settings.put("canonicalExpectationSha256", canonicalExpectationSha256(objectMapper, prompts));
        return Collections.unmodifiableMap(settings);
    }

    private static void validateCanonicalFailureMarker(List<ToolBenchmarkPrompt> prompts) {
        ToolBenchmarkPrompt failure = prompts.stream()
                .filter(prompt -> "deterministic-tool-failure".equals(prompt.id()))
                .findFirst()
                .orElseThrow();
        if (!failure.expectation().requiredToolResponseTerms().equals(List.of(FailureBenchmarkTools.FAILURE_MARKER))) {
            throw new IllegalStateException("Deterministic failure expectation is not canonical.");
        }
    }
}
