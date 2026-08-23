package com.setaccio.lab.toolcompat;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;

/** Complete provider-free execution contract assembled before cohort evidence allocation. */
record ToolCompatibilityCohortExecutionPlan(
        ToolCompatibilityCohortPreflight.Prepared preflight,
        ToolCompatibilityHumanDecision humanDecision,
        ToolCompatibilityCohortPromptPolicy.Selection promptPolicy,
        ToolCompatibilityCohortSchedule schedule,
        List<ToolCallback> callbacks
) {

    ToolCompatibilityCohortExecutionPlan {
        if (preflight == null
                || humanDecision == null
                || promptPolicy == null
                || schedule == null) {
            throw new IllegalArgumentException("cohort execution plan is incomplete");
        }
        if (promptPolicy.executionState()
                != ToolCompatibilityCohortPromptPolicy.ExecutionState.EXECUTABLE) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Blocked prompt policy cannot create a cohort execution plan");
        }
        schedule.requireBoundTo(preflight);
        callbacks = List.copyOf(callbacks == null ? List.of() : callbacks);
        ToolCompatibilityCallbackCatalog.requireExactCallbacks(callbacks);
    }

    static ToolCompatibilityCohortExecutionPlan create(
            ToolCompatibilityCohortPreflight.Prepared preflight,
            ToolCompatibilityHumanDecision humanDecision,
            ToolCompatibilityHumanDecisionBinding expectedBinding
    ) {
        ToolCompatibilityCohortPromptPolicy.Selection policy =
                ToolCompatibilityCohortPromptPolicy.resolve(humanDecision, expectedBinding);
        return new ToolCompatibilityCohortExecutionPlan(
                preflight,
                humanDecision,
                policy,
                ToolCompatibilityCohortSchedule.create(
                        preflight.ollamaRuntimeVersion(),
                        preflight.orderedModels()),
                ToolCompatibilityCallbackCatalog.canonicalCallbacks());
    }

    static ToolCompatibilityCohortExecutionPlan createApproved(
            ToolCompatibilityCohortPreflight.Prepared preflight
    ) {
        return create(
                preflight,
                ToolCompatibilityPhase2DecisionLock.decision(),
                ToolCompatibilityPhase2DecisionLock.binding());
    }

    ToolCompatibilitySystemPromptIdentity systemPrompt() {
        return promptPolicy.prompt(ToolCompatibilityProtocol.systemPromptCatalog());
    }

    void requireRuntimeUnchanged(String runtimeVersion) {
        if (!preflight.ollamaRuntimeVersion().equals(runtimeVersion)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Ollama runtime version drifted after cohort preflight");
        }
    }
}
