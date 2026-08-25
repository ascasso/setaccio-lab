package com.setaccio.lab.toolcompat;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortRunnerArgumentsTest {

    @Test
    void parsesOnlyExplicitProtocolAndOutputOptionsBecauseModelIdentityIsSuiteOwned() {
        ToolCompatibilityCohortRunner.Arguments arguments =
                ToolCompatibilityCohortRunner.Arguments.parse(new String[] {
                        "--output-dir", "build/tool-compatibility/2026-08-23-cohort",
                        "--timeout", "PT2M",
                        "--ollama-base-url", "http://localhost:11434",
                        "--max-tokens", "512"
                });

        assertThat(arguments.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(arguments.maxTokens()).isEqualTo("512");
        assertThat(arguments.timeout()).isEqualTo("PT2M");
        assertThat(arguments.outputDirectory())
                .isEqualTo("build/tool-compatibility/2026-08-23-cohort");
    }

    @Test
    void rejectsMissingDuplicateUnknownAndCallerSuppliedModelOptions() {
        assertThatThrownBy(() -> ToolCompatibilityCohortRunner.Arguments.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cohort tags and digests are suite-owned");
        assertThatThrownBy(() -> ToolCompatibilityCohortRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--model", "replacement:latest"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suite-owned");
        assertThatThrownBy(() -> ToolCompatibilityCohortRunner.Arguments.parse(new String[] {
                "--ollama-base-url", "http://localhost:11434",
                "--max-tokens", "512",
                "--timeout", "PT2M",
                "--timeout", "PT2M"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suite-owned");
    }

    @Test
    void requiresOneUnchangedCleanCommit() {
        EvidenceCodeBaseline baseline = new EvidenceCodeBaseline("a".repeat(40), false);

        assertThat(ToolCompatibilityCohortRunner.requireCleanBaseline(baseline))
                .isEqualTo(baseline);
        ToolCompatibilityCohortRunner.requireSameCleanBaseline(
                baseline, new EvidenceCodeBaseline("a".repeat(40), false));

        assertThatThrownBy(() -> ToolCompatibilityCohortRunner.requireCleanBaseline(
                new EvidenceCodeBaseline("a".repeat(40), true)))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("clean Git commit");
        assertThatThrownBy(() -> ToolCompatibilityCohortRunner.requireSameCleanBaseline(
                baseline, new EvidenceCodeBaseline("b".repeat(40), false)))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("Git commit drifted");
    }
}
