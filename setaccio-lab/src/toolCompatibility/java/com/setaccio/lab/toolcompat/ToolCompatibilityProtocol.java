package com.setaccio.lab.toolcompat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ToolCompatibilityProtocol {

    static final int VERSION = 1;
    static final String SUITE = "ollama-tool-compatibility";
    static final String PROVIDER = "ollama";
    static final String EXECUTION_ENGINE = "spring-ai-standard-tool-calling-advisor";
    static final String EXECUTION_STRATEGY = "sequential";
    static final String PULL_MODEL_STRATEGY = "never";
    static final String INITIAL_MODEL = "hf.co/ermiaazarkhalili/LFM2.5-2.6B-SFT-Fable5-Glint-GGUF:Q8_0";
    static final List<String> CASE_IDS = List.of(
            "arithmetic-add",
            "fixed-utc-time",
            "fixed-zone-time",
            "catalog-lookup",
            "catalog-multi-step",
            "catalog-no-match",
            "no-applicable-domain-tool",
            "deterministic-tool-failure");
    static final int REPETITIONS = 2;
    static final List<Integer> SEEDS = List.of(42, 43);
    static final double TEMPERATURE = 0.0;
    static final int MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN = 512;
    static final Duration ROW_TIMEOUT = Duration.ofMinutes(2);
    static final int LOGICAL_ROW_ATTEMPTS = 1;
    static final int ROW_COUNT = 16;
    static final String RAW_FILENAME = "tool-compatibility-results.json";

    private ToolCompatibilityProtocol() {}

    static ToolCompatibilityCaseSelection caseSelection() {
        ToolCompatibilityCaseSelection selection = ToolCompatibilityCaseSelection.fromCanonicalCases();
        selection.requireBoundTo(caseOracle());
        return selection;
    }

    static ToolCompatibilityRunSettings runSettings() {
        return new ToolCompatibilityRunSettings(
                INITIAL_MODEL,
                REPETITIONS,
                TEMPERATURE,
                SEEDS,
                MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN,
                ROW_TIMEOUT.toMillis(),
                LOGICAL_ROW_ATTEMPTS);
    }

    static ToolCompatibilityCaseOracle caseOracle() {
        return ToolCompatibilityCaseOracle.loadLocked();
    }

    static ToolCompatibilitySystemPromptIdentity systemPromptIdentity() {
        return ToolCompatibilitySystemPromptIdentity.untreated();
    }

    static ToolCompatibilitySystemPromptCatalog systemPromptCatalog() {
        return ToolCompatibilitySystemPromptCatalog.loadLocked();
    }

    static Map<String, Object> manifestSettings(ToolCompatibilityResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("provider", result.provider());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("modelIdentity", result.modelIdentity());
        settings.put("systemPromptIdentity", result.systemPromptIdentity());
        settings.put("caseOracleId", result.caseOracleId());
        settings.put("caseOracleVersion", result.caseOracleVersion());
        settings.put("caseOracleSha256", result.caseOracleSha256());
        settings.put("orderedCaseIds", result.orderedCaseIds());
        settings.put("canonicalCasesSha256", result.canonicalCasesSha256());
        settings.put("orderedToolNames", result.orderedToolNames());
        settings.put("toolNamesSha256", result.toolNamesSha256());
        settings.put("toolDefinitionsSha256", result.toolDefinitionsSha256());
        settings.put("orderedSchedule", result.orderedSchedule());
        return Collections.unmodifiableMap(settings);
    }

    static List<ToolCompatibilityCaseSelection.ScheduledCase> schedule(
            ToolCompatibilityCaseSelection selection,
            ToolCompatibilityRunSettings settings) {
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        selection.requireBoundTo(caseOracle());

        List<ToolCompatibilityCaseSelection.ScheduledCase> schedule = new ArrayList<>(ROW_COUNT);
        int sequence = 1;
        for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
            int seed = settings.seedFor(repetition);
            for (String caseId : selection.caseIds()) {
                schedule.add(new ToolCompatibilityCaseSelection.ScheduledCase(
                        sequence++, repetition, seed, caseId));
            }
        }
        if (schedule.size() != ROW_COUNT) {
            throw new IllegalStateException("Tool compatibility schedule must contain exactly 16 rows");
        }
        return List.copyOf(schedule);
    }
}
