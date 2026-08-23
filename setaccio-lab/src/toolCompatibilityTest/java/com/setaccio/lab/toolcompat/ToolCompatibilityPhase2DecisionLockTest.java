package com.setaccio.lab.toolcompat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPhase2DecisionLockTest {

    @Test
    void bindsTheOwnerDecisionToTheExactPublicSafePhase2Identities() {
        ToolCompatibilityHumanDecision decision = ToolCompatibilityPhase2DecisionLock.decision();
        ToolCompatibilityHumanDecisionBinding binding = ToolCompatibilityPhase2DecisionLock.binding();

        assertThat(decision.decision())
                .isEqualTo(ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE);
        assertThat(decision.binding()).isEqualTo(binding);
        assertThat(binding.baselineRunId()).isEqualTo("2026-08-21-lfm-prompt-untreated");
        assertThat(binding.candidateRunId()).isEqualTo("2026-08-21-lfm-prompted");
        assertThat(binding.promptCatalogDigest()).isEqualTo(
                "d55122cd60ac056c8f5cc3e35a2661e497bc1468cff6a593f4cf666b1eb7e06d");
        assertThat(binding.comparisonReportDigest()).isEqualTo(
                "e22289df2fbfdd6bddda8ac0776395681bed0d85939f69b955f777274d05a7b9");
        assertThat(binding.reviewDate()).isEqualTo(LocalDate.parse("2026-08-23"));

        ToolCompatibilityCohortPromptPolicy.Selection selection =
                ToolCompatibilityCohortPromptPolicy.resolve(decision, binding);
        assertThat(selection.promptCondition())
                .isEqualTo(ToolCompatibilityPromptCondition.UNTREATED);
        assertThat(selection.limitation())
                .isEqualTo(ToolCompatibilityCohortPromptPolicy.INCONCLUSIVE_LIMITATION);
    }

    @Test
    void rejectsAnyDifferentDecisionOrEvidenceBinding() {
        ToolCompatibilityHumanDecision differentDecision = new ToolCompatibilityHumanDecision(
                ToolCompatibilityHumanDecision.Decision.REJECT,
                ToolCompatibilityPhase2DecisionLock.binding());

        assertThatThrownBy(() ->
                ToolCompatibilityPhase2DecisionLock.requireMatches(differentDecision))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("exact owner-completed T2.5 decision");
    }
}
