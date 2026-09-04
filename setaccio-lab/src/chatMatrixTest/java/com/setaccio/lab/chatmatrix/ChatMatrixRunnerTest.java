package com.setaccio.lab.chatmatrix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMatrixRunnerTest {

    @Test
    void parsesOnlyTheFiveExplicitLiveOptions() {
        ChatMatrixRunner.Arguments arguments = ChatMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--model", "model-a",
                "--max-tokens", "128",
                "--timeout", "PT30S",
                "--output-dir", "local/evidence/chat-matrix/2026-08-04-test"
        });

        assertThat(arguments.model()).isEqualTo("model-a");
        assertThat(arguments.maxTokens()).isEqualTo("128");
        assertThatThrownBy(() -> ChatMatrixRunner.Arguments.parse(new String[] {
                "--model", "model-a"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected --ollama-base-url");
    }
}
