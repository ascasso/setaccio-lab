package com.setaccio.lab.toolcompat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPromptMatrixRunnerArgumentsTest {

    @Test
    void requiresEveryPairedLiveOptionExactlyOnceWithoutPromptSelection() {
        ToolCompatibilityPromptMatrixRunner.Arguments arguments =
                ToolCompatibilityPromptMatrixRunner.Arguments.parse(new String[] {
                        "--candidate-output-dir", "local/evidence/tool-compatibility/2026-08-21-prompted",
                        "--timeout", "PT2M",
                        "--model", ToolCompatibilityProtocol.INITIAL_MODEL,
                        "--baseline-output-dir", "local/evidence/tool-compatibility/2026-08-21-baseline",
                        "--ollama-base-url", "http://localhost:11434",
                        "--max-tokens", "512"
                });

        assertThat(arguments).isEqualTo(new ToolCompatibilityPromptMatrixRunner.Arguments(
                "http://localhost:11434",
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "512",
                "PT2M",
                "local/evidence/tool-compatibility/2026-08-21-baseline",
                "local/evidence/tool-compatibility/2026-08-21-prompted"));
        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--model", ToolCompatibilityProtocol.INITIAL_MODEL,
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--baseline-output-dir", "local/evidence/tool-compatibility/2026-08-21-baseline",
                "--prompt", "tool-system-discipline"
        })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolCompatibilityPromptMatrixRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--model", ToolCompatibilityProtocol.INITIAL_MODEL,
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--baseline-output-dir", "local/evidence/tool-compatibility/2026-08-21-baseline",
                "--candidate-output-dir", "local/evidence/tool-compatibility/2026-08-21-prompted",
                "--candidate-output-dir", "local/evidence/tool-compatibility/2026-08-21-other"
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
