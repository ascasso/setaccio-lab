package com.setaccio.lab.tool;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkToolsTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void arithmeticToolsReturnDeterministicDecimalResults() {
        ArithmeticBenchmarkTools tools = new ArithmeticBenchmarkTools();

        ArithmeticBenchmarkTools.ArithmeticResult sum = tools.addNumbers(
                new BigDecimal("2.50"),
                new BigDecimal("4.25")
        );
        ArithmeticBenchmarkTools.ArithmeticResult product = tools.multiplyNumbers(
                new BigDecimal("3.5"),
                new BigDecimal("2")
        );

        assertThat(sum.operation()).isEqualTo("add");
        assertThat(sum.result()).isEqualByComparingTo("6.75");
        assertThat(product.operation()).isEqualTo("multiply");
        assertThat(product.result()).isEqualByComparingTo("7.0");
    }

    @Test
    void timeToolsUseFixedFixtureClock() {
        FixtureTimeTools tools = new FixtureTimeTools(FIXED_CLOCK);

        FixtureTimeTools.TimeSnapshot utc = tools.fixedUtcNow();
        FixtureTimeTools.TimeSnapshot losAngeles = tools.fixedTimeForZone("America/Los_Angeles");

        assertThat(utc.instant()).isEqualTo("2026-01-15T12:00:00Z");
        assertThat(utc.zoneId()).isEqualTo("UTC");
        assertThat(utc.localDate()).isEqualTo("2026-01-15");
        assertThat(utc.localTime()).isEqualTo("12:00:00");
        assertThat(losAngeles.zoneId()).isEqualTo("America/Los_Angeles");
        assertThat(losAngeles.localDate()).isEqualTo("2026-01-15");
        assertThat(losAngeles.localTime()).isEqualTo("04:00:00");
    }

    @Test
    void catalogToolsReturnPublicSafeFixtures() {
        FixtureCatalogTools tools = new FixtureCatalogTools();

        FixtureCatalogTools.CatalogLookupResult lookup = tools.lookupCatalogItem(" fixture-image-landscape ");
        FixtureCatalogTools.CatalogLookupResult missing = tools.lookupCatalogItem("missing");
        FixtureCatalogTools.CatalogListResult documents = tools.listCatalogItems("document");

        assertThat(lookup.found()).isTrue();
        assertThat(lookup.item()).isNotNull();
        assertThat(lookup.item().tags()).contains("image", "classification");
        assertThat(missing.found()).isFalse();
        assertThat(documents.items())
                .extracting(FixtureCatalogTools.CatalogItem::id)
                .containsExactly("fixture-invoice-sample");
    }

    @Test
    void methodToolCallbackProviderPublishesStableToolDefinitions() {
        Map<String, ToolCallback> callbacks = callbacksByName();

        assertThat(callbacks.keySet()).containsExactlyInAnyOrder(
                ArithmeticBenchmarkTools.ADD_TOOL_NAME,
                ArithmeticBenchmarkTools.MULTIPLY_TOOL_NAME,
                FixtureTimeTools.FIXED_UTC_NOW_TOOL_NAME,
                FixtureTimeTools.FIXED_TIME_FOR_ZONE_TOOL_NAME,
                FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME,
                FixtureCatalogTools.LIST_ITEMS_TOOL_NAME
        );
        assertThat(callbacks.get(ArithmeticBenchmarkTools.ADD_TOOL_NAME).getToolDefinition().inputSchema())
                .contains("\"left\"", "\"right\"");
        assertThat(callbacks.get(FixtureCatalogTools.LOOKUP_ITEM_TOOL_NAME).getToolDefinition().description())
                .contains("public-safe");
    }

    @Test
    void springAiToolCallbackCanExecuteDeterministicToolFromJsonInput() {
        ToolCallback add = callbacksByName().get(ArithmeticBenchmarkTools.ADD_TOOL_NAME);

        String result = add.call("""
                {"left": "2.50", "right": "4.25"}
                """);

        assertThat(result).contains("\"operation\":\"add\"");
        assertThat(result).contains("\"result\":6.75");
    }

    private Map<String, ToolCallback> callbacksByName() {
        return Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(
                                new ArithmeticBenchmarkTools(),
                                new FixtureTimeTools(FIXED_CLOCK),
                                new FixtureCatalogTools()
                        )
                        .build()
                        .getToolCallbacks())
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));
    }
}
