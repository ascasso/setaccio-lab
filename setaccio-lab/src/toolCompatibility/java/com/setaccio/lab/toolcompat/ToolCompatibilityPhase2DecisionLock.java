package com.setaccio.lab.toolcompat;

import java.time.LocalDate;

/** Public-safe suite binding for the owner-completed T2.5 decision. */
final class ToolCompatibilityPhase2DecisionLock {

    private static final ToolCompatibilityHumanDecisionBinding BINDING =
            new ToolCompatibilityHumanDecisionBinding(
                    "2026-08-21-lfm-prompt-untreated",
                    "2026-08-21-lfm-prompted",
                    "d55122cd60ac056c8f5cc3e35a2661e497bc1468cff6a593f4cf666b1eb7e06d",
                    "e22289df2fbfdd6bddda8ac0776395681bed0d85939f69b955f777274d05a7b9",
                    LocalDate.parse("2026-08-23"));

    private static final ToolCompatibilityHumanDecision DECISION =
            new ToolCompatibilityHumanDecision(
                    ToolCompatibilityHumanDecision.Decision.INCONCLUSIVE,
                    BINDING);

    private ToolCompatibilityPhase2DecisionLock() {}

    static ToolCompatibilityHumanDecisionBinding binding() {
        return BINDING;
    }

    static ToolCompatibilityHumanDecision decision() {
        return DECISION;
    }

    static void requireMatches(ToolCompatibilityHumanDecision decision) {
        if (!DECISION.equals(decision)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort execution requires the exact owner-completed T2.5 decision binding");
        }
    }
}
