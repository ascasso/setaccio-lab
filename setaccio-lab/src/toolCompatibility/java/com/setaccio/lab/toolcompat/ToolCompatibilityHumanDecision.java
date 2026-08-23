package com.setaccio.lab.toolcompat;

/** Owner-only T2.5 decision supplied to the deterministic Phase 3 prompt-policy mapping. */
record ToolCompatibilityHumanDecision(
        Decision decision,
        ToolCompatibilityHumanDecisionBinding binding
) {

    ToolCompatibilityHumanDecision {
        if (decision == null || binding == null) {
            throw new IllegalArgumentException("human decision and evidence binding are required");
        }
    }

    enum Decision {
        ADOPT,
        REVISE,
        REJECT,
        INCONCLUSIVE
    }
}
