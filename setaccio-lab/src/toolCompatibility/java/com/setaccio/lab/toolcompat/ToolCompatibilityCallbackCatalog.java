package com.setaccio.lab.toolcompat;

import com.setaccio.lab.tool.ArithmeticBenchmarkTools;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import com.setaccio.lab.tool.FixtureCatalogTools;
import com.setaccio.lab.tool.FixtureTimeTools;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/** Builds the existing public fixture callbacks without starting Spring or a provider. */
final class ToolCompatibilityCallbackCatalog {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);

    private ToolCompatibilityCallbackCatalog() {}

    static List<ToolCallback> canonicalCallbacks() {
        List<ToolCallback> callbacks = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(
                        new ArithmeticBenchmarkTools(),
                        new FixtureTimeTools(FIXED_CLOCK),
                        new FixtureCatalogTools(),
                        new FailureBenchmarkTools())
                .build()
                .getToolCallbacks());
        return requireExactCallbacks(callbacks);
    }

    static List<ToolCallback> requireExactCallbacks(List<ToolCallback> callbacks) {
        if (callbacks == null) {
            throw new ToolCompatibilityProtocolIntegrityException("Tool callbacks must not be null");
        }
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        for (ToolCallback callback : callbacks) {
            if (callback == null || callback.getToolDefinition() == null) {
                throw new ToolCompatibilityProtocolIntegrityException("Tool callbacks must have definitions");
            }
            String name = callback.getToolDefinition().name();
            if (byName.put(name, callback) != null) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Tool callbacks must not have duplicate names: " + name);
            }
        }
        List<String> expectedNames = ToolCompatibilityProtocol.caseSelection().toolNames();
        if (!byName.keySet().equals(java.util.Set.copyOf(expectedNames))) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Tool callbacks must exactly match the canonical tool names");
        }
        ToolCompatibilitySchemaValidator validator = new ToolCompatibilitySchemaValidator();
        List<ToolCallback> orderedCallbacks = expectedNames.stream().map(byName::get).toList();
        orderedCallbacks.forEach(callback -> validator.requireSupportedDefinition(callback.getToolDefinition()));
        return orderedCallbacks;
    }
}
