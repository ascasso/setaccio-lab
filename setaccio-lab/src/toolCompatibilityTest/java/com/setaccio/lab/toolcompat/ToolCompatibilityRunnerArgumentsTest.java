package com.setaccio.lab.toolcompat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityRunnerArgumentsTest {

    @Test
    void requiresEveryExplicitLiveOptionExactlyOnce() {
        ToolCompatibilityMatrixRunner.Arguments arguments =
                ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                        "--timeout", "PT2M",
                        "--model", ToolCompatibilityProtocol.INITIAL_MODEL,
                        "--output-dir", "build/tool-compatibility/2026-08-18-test",
                        "--ollama-base-url", "http://localhost:11434",
                        "--max-tokens", "512"
                });

        assertThat(arguments).isEqualTo(new ToolCompatibilityMatrixRunner.Arguments(
                "http://localhost:11434",
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "512",
                "PT2M",
                "build/tool-compatibility/2026-08-18-test"));
        assertThatThrownBy(() -> ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--output-dir", "build/tool-compatibility/2026-08-18-test",
                "--unknown", "value"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                "--model", ToolCompatibilityProtocol.INITIAL_MODEL
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--output-dir", "build/tool-compatibility/2026-08-18-test"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--output-dir", "build/tool-compatibility/2026-08-18-test",
                "--ollama-base-url", "http://localhost:11434"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--model", ToolCompatibilityProtocol.INITIAL_MODEL,
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--model", ToolCompatibilityProtocol.INITIAL_MODEL
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
