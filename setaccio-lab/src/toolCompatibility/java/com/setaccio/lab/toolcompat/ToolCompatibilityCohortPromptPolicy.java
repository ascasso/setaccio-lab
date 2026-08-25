package com.setaccio.lab.toolcompat;

/** Maps one bound owner decision to the single prompt policy permitted for the full cohort. */
final class ToolCompatibilityCohortPromptPolicy {

    static final String INCONCLUSIVE_LIMITATION =
            "Phase 2 did not establish a prompt effect; the cohort uses untreated operation.";
    static final String REVISE_LIMITATION =
            "The owner selected revise; a new prompt experiment must complete before cohort execution.";

    private ToolCompatibilityCohortPromptPolicy() {}

    static Selection resolve(
            ToolCompatibilityHumanDecision humanDecision,
            ToolCompatibilityHumanDecisionBinding expectedBinding
    ) {
        if (humanDecision == null || expectedBinding == null) {
            throw new IllegalArgumentException(
                    "human decision and expected evidence binding are required");
        }
        if (!expectedBinding.equals(humanDecision.binding())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "T2.5 human decision does not match the expected evidence binding");
        }
        return switch (humanDecision.decision()) {
            case ADOPT -> Selection.executable(ToolCompatibilityPromptCondition.PROMPTED, null);
            case REJECT -> Selection.executable(ToolCompatibilityPromptCondition.UNTREATED, null);
            case INCONCLUSIVE -> Selection.executable(
                    ToolCompatibilityPromptCondition.UNTREATED,
                    INCONCLUSIVE_LIMITATION);
            case REVISE -> Selection.blocked(REVISE_LIMITATION);
        };
    }

    record Selection(
            ExecutionState executionState,
            ToolCompatibilityPromptCondition promptCondition,
            String limitation
    ) {

        Selection {
            if (executionState == null) {
                throw new IllegalArgumentException("executionState is required");
            }
            if (executionState == ExecutionState.EXECUTABLE && promptCondition == null) {
                throw new IllegalArgumentException("executable policy requires one prompt condition");
            }
            if (executionState == ExecutionState.BLOCKED && promptCondition != null) {
                throw new IllegalArgumentException("blocked policy must not select a prompt condition");
            }
            if (limitation != null
                    && (limitation.isBlank() || !limitation.equals(limitation.strip()))) {
                throw new IllegalArgumentException("limitation must be nonblank and trimmed when present");
            }
        }

        static Selection executable(
                ToolCompatibilityPromptCondition promptCondition,
                String limitation
        ) {
            return new Selection(ExecutionState.EXECUTABLE, promptCondition, limitation);
        }

        static Selection blocked(String limitation) {
            return new Selection(ExecutionState.BLOCKED, null, limitation);
        }

        ToolCompatibilitySystemPromptIdentity prompt(
                ToolCompatibilitySystemPromptCatalog catalog
        ) {
            if (executionState != ExecutionState.EXECUTABLE) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Blocked cohort prompt policy has no executable prompt");
            }
            return promptCondition.prompt(catalog);
        }
    }

    enum ExecutionState {
        EXECUTABLE,
        BLOCKED
    }
}
