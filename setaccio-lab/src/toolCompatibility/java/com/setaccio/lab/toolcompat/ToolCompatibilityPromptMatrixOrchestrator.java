package com.setaccio.lab.toolcompat;

/** Keeps both condition runs incomplete unless the shared execution finalizes cleanly. */
final class ToolCompatibilityPromptMatrixOrchestrator {

    private final ToolCompatibilityPromptMatrixExecutor executor;

    ToolCompatibilityPromptMatrixOrchestrator() {
        this(new ToolCompatibilityPromptMatrixExecutor());
    }

    ToolCompatibilityPromptMatrixOrchestrator(ToolCompatibilityPromptMatrixExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("prompt-matrix executor must not be null");
        }
        this.executor = executor;
    }

    void executeAndWrite(
            ToolCompatibilityPromptMatrixPreflight.Prepared prepared,
            ToolCompatibilityPromptMatrixPreflight.AllocatedOutputs outputs,
            ToolCompatibilityPromptMatrixEvidence evidence
    ) {
        if (prepared == null || outputs == null || evidence == null) {
            throw new IllegalArgumentException("prompt-matrix orchestration inputs are required");
        }
        ToolCompatibilityPromptMatrixExecutor.Execution execution = executor.execute(prepared);
        ToolCompatibilityPromptMatrixEvidence.StagedCondition baseline = null;
        ToolCompatibilityPromptMatrixEvidence.StagedCondition candidate = null;
        try {
            baseline = evidence.stage(outputs.baseline(), execution.untreated(), prepared.codeBaseline());
            candidate = evidence.stage(outputs.candidate(), execution.prompted(), prepared.codeBaseline());
            prepared.requireRepositoryUnchanged();
            evidence.finalize(baseline);
            prepared.requireRepositoryUnchanged();
            evidence.finalize(candidate);
            prepared.requireRepositoryUnchanged();
        } catch (RuntimeException exception) {
            evidence.invalidate(candidate);
            evidence.invalidate(baseline);
            throw exception;
        }
    }
}
