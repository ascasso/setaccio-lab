package com.setaccio.lab.toolcompat;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortPromptPolicyTest {

    private static final ToolCompatibilityHumanDecisionBinding BINDING =
            new ToolCompatibilityHumanDecisionBinding(
                    "baseline-run",
                    "candidate-run",
                    ToolCompatibilitySystemPromptCatalog.SHA256,
                    "e".repeat(64),
                    LocalDate.parse("2026-08-23"));

    @ParameterizedTest
    @MethodSource("decisions")
    void mapsEveryOwnerDecisionToOneDeterministicCohortPolicy(
            ToolCompatibilityHumanDecision.Decision decision,
            ToolCompatibilityCohortPromptPolicy.ExecutionState expectedState,
            ToolCompatibilityPromptCondition expectedCondition,
            String expectedLimitation
    ) {
        ToolCompatibilityCohortPromptPolicy.Selection selection =
                ToolCompatibilityCohortPromptPolicy.resolve(
                        new ToolCompatibilityHumanDecision(decision, BINDING),
                        BINDING);

        assertThat(selection.executionState()).isEqualTo(expectedState);
        assertThat(selection.promptCondition()).isEqualTo(expectedCondition);
        assertThat(selection.limitation()).isEqualTo(expectedLimitation);
    }

    @Test
    void resolvesExecutablePoliciesAgainstTheLockedCatalog() {
        ToolCompatibilitySystemPromptCatalog catalog =
                ToolCompatibilitySystemPromptCatalog.loadLocked();
        ToolCompatibilityCohortPromptPolicy.Selection adopted =
                ToolCompatibilityCohortPromptPolicy.resolve(
                        new ToolCompatibilityHumanDecision(
                                ToolCompatibilityHumanDecision.Decision.ADOPT,
                                BINDING),
                        BINDING);
        ToolCompatibilityCohortPromptPolicy.Selection rejected =
                ToolCompatibilityCohortPromptPolicy.resolve(
                        new ToolCompatibilityHumanDecision(
                                ToolCompatibilityHumanDecision.Decision.REJECT,
                                BINDING),
                        BINDING);

        assertThat(adopted.prompt(catalog)).isEqualTo(catalog.toolDiscipline());
        assertThat(rejected.prompt(catalog)).isEqualTo(catalog.untreated());
    }

    @Test
    void blocksReviseWithoutSelectingAnExecutablePrompt() {
        ToolCompatibilityCohortPromptPolicy.Selection revised =
                ToolCompatibilityCohortPromptPolicy.resolve(
                        new ToolCompatibilityHumanDecision(
                                ToolCompatibilityHumanDecision.Decision.REVISE,
                                BINDING),
                        BINDING);

        assertThatThrownBy(() -> revised.prompt(ToolCompatibilitySystemPromptCatalog.loadLocked()))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("no executable prompt");
    }

    @Test
    void rejectsPromptCatalogAndComparisonDigestBindingDrift() {
        ToolCompatibilityHumanDecision decision = new ToolCompatibilityHumanDecision(
                ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE,
                BINDING);
        ToolCompatibilityHumanDecisionBinding catalogDrift = binding(
                "f".repeat(64),
                BINDING.comparisonReportDigest());
        ToolCompatibilityHumanDecisionBinding comparisonDrift = binding(
                BINDING.promptCatalogDigest(),
                "d".repeat(64));

        assertThatThrownBy(() -> ToolCompatibilityCohortPromptPolicy.resolve(
                decision,
                catalogDrift))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("evidence binding");
        assertThatThrownBy(() -> ToolCompatibilityCohortPromptPolicy.resolve(
                decision,
                comparisonDrift))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("evidence binding");
    }

    private static Stream<Arguments> decisions() {
        return Stream.of(
                Arguments.of(
                        ToolCompatibilityHumanDecision.Decision.ADOPT,
                        ToolCompatibilityCohortPromptPolicy.ExecutionState.EXECUTABLE,
                        ToolCompatibilityPromptCondition.PROMPTED,
                        null),
                Arguments.of(
                        ToolCompatibilityHumanDecision.Decision.REJECT,
                        ToolCompatibilityCohortPromptPolicy.ExecutionState.EXECUTABLE,
                        ToolCompatibilityPromptCondition.UNTREATED,
                        null),
                Arguments.of(
                        ToolCompatibilityHumanDecision.Decision.REVISE,
                        ToolCompatibilityCohortPromptPolicy.ExecutionState.BLOCKED,
                        null,
                        ToolCompatibilityCohortPromptPolicy.REVISE_LIMITATION),
                Arguments.of(
                        ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE,
                        ToolCompatibilityCohortPromptPolicy.ExecutionState.EXECUTABLE,
                        ToolCompatibilityPromptCondition.UNTREATED,
                        ToolCompatibilityCohortPromptPolicy.INCONCLUSIVE_LIMITATION));
    }

    private static ToolCompatibilityHumanDecisionBinding binding(
            String promptCatalogDigest,
            String comparisonReportDigest
    ) {
        return new ToolCompatibilityHumanDecisionBinding(
                BINDING.baselineRunId(),
                BINDING.candidateRunId(),
                promptCatalogDigest,
                comparisonReportDigest,
                BINDING.reviewDate());
    }
}
