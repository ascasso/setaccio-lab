package com.setaccio.lab.config;

import com.setaccio.lab.tool.ArithmeticBenchmarkTools;
import com.setaccio.lab.tool.FailureBenchmarkTools;
import com.setaccio.lab.tool.FixtureCatalogTools;
import com.setaccio.lab.tool.FixtureTimeTools;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BenchmarkToolConfig {

    @Bean
    public ArithmeticBenchmarkTools arithmeticBenchmarkTools() {
        return new ArithmeticBenchmarkTools();
    }

    @Bean
    public FixtureTimeTools fixtureTimeTools(
            @Value("${setaccio.lab.tool-fixtures.fixed-instant:2026-01-15T12:00:00Z}") String fixedInstant) {
        return new FixtureTimeTools(Clock.fixed(Instant.parse(fixedInstant), ZoneOffset.UTC));
    }

    @Bean
    public FixtureCatalogTools fixtureCatalogTools() {
        return new FixtureCatalogTools();
    }

    @Bean
    public FailureBenchmarkTools failureBenchmarkTools() {
        return new FailureBenchmarkTools();
    }

    @Bean
    public ToolCallbackProvider benchmarkToolCallbackProvider(
            ArithmeticBenchmarkTools arithmeticBenchmarkTools,
            FixtureTimeTools fixtureTimeTools,
            FixtureCatalogTools fixtureCatalogTools,
            FailureBenchmarkTools failureBenchmarkTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(arithmeticBenchmarkTools, fixtureTimeTools, fixtureCatalogTools, failureBenchmarkTools)
                .build();
    }
}
