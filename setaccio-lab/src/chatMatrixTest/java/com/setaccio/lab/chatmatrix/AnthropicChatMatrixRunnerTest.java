package com.setaccio.lab.chatmatrix;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChatMatrixRunnerTest {

    @TempDir
    Path projectDirectory;

    @Test
    void acceptsOnlyTheFiveExplicitRemoteOptions() {
        AnthropicChatMatrixRunner.Arguments arguments = AnthropicChatMatrixRunner.Arguments.parse(new String[] {
                "--max-tokens", "128",
                "--timeout", "PT2M",
                "--max-cost-usd", "3.00",
                "--output-dir", "build/anthropic-chat-matrix/2026-08-05-haiku-o3",
                "--ollama-run-dir", "build/chat-matrix/2026-08-05-gemma-s3"
        });

        assertThat(arguments.maxTokens()).isEqualTo("128");
        assertThat(arguments.maximumCostUsd()).isEqualTo("3.00");
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.Arguments.parse(new String[] {
                "--max-tokens", "128"
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Expected --max-tokens");
    }

    @Test
    void boundsTokenTimeoutAndAuthorizedCostBeforeRemoteExecution() {
        assertThat(AnthropicChatMatrixRunner.parseMaxTokens("128")).isEqualTo(128);
        assertThat(AnthropicChatMatrixRunner.parseTimeout("PT2M")).isEqualTo(Duration.ofMinutes(2));
        assertThat(AnthropicChatMatrixRunner.parseMaximumCost("3.00"))
                .isEqualByComparingTo(new BigDecimal("3.00"));

        assertThatThrownBy(() -> AnthropicChatMatrixRunner.parseMaxTokens("0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.parseTimeout("PT11M"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.parseMaximumCost("3.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no more than 3.00");
    }

    @Test
    void confinesInputAndOutputDirectoriesToTheirDedicatedIgnoredRoots() throws Exception {
        Path output = AnthropicChatMatrixRunner.resolveNewOutputDirectory(
                projectDirectory, "build/anthropic-chat-matrix/2026-08-05-haiku-o3");
        assertThat(output).isEqualTo(projectDirectory.resolve("build/anthropic-chat-matrix/2026-08-05-haiku-o3"));
        Files.createDirectories(output);
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.resolveNewOutputDirectory(
                projectDirectory, "build/anthropic-chat-matrix/2026-08-05-haiku-o3"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.resolveNewOutputDirectory(
                projectDirectory, "build/chat-matrix/2026-08-05-wrong-root"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directly under");

        Path ollama = projectDirectory.resolve("build/chat-matrix/2026-08-05-gemma-s3");
        Files.createDirectories(ollama);
        assertThat(AnthropicChatMatrixRunner.resolveOllamaRunDirectory(
                projectDirectory, "build/chat-matrix/2026-08-05-gemma-s3")).isEqualTo(ollama);
        assertThatThrownBy(() -> AnthropicChatMatrixRunner.resolveOllamaRunDirectory(
                projectDirectory, "build/anthropic-chat-matrix/2026-08-05-haiku-o3"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directly under");
    }
}
