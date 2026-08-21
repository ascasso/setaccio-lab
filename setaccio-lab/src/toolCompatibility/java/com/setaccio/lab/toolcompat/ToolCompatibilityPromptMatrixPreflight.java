package com.setaccio.lab.toolcompat;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;

/** Validates both prompt-condition outputs and the complete paired protocol before allocation. */
final class ToolCompatibilityPromptMatrixPreflight {

    Prepared prepare(
            Input input,
            RepositoryState repositoryState,
            ToolCompatibilityPreflight.SessionFactory sessionFactory
    ) {
        if (input == null || repositoryState == null || sessionFactory == null) {
            throw new IllegalArgumentException("Prompt-matrix preflight dependencies are required");
        }
        if (input.projectDirectory() == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        Path projectDirectory = input.projectDirectory().toAbsolutePath().normalize();
        OllamaChatModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        String model = requireOption(input.model(), "--model");
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(model)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "--model must equal the locked Phase 1 model tag");
        }
        int maxTokens = ToolCompatibilityPreflight.parseMaxTokens(input.maxTokens());
        Duration timeout = ToolCompatibilityPreflight.parseTimeout(input.timeout());
        Path baselineOutputDirectory = ToolCompatibilityPreflight.resolveNewOutputDirectory(
                projectDirectory, input.baselineOutputDirectory());
        Path candidateOutputDirectory = ToolCompatibilityPreflight.resolveNewOutputDirectory(
                projectDirectory, input.candidateOutputDirectory());
        if (baselineOutputDirectory.equals(candidateOutputDirectory)) {
            throw new IllegalArgumentException("Baseline and candidate output directories must differ");
        }

        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        if (maxTokens != settings.maxOutputTokensPerProviderTurn()
                || timeout.toMillis() != settings.rowTimeoutMillis()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Explicit options must equal the locked Phase 2 settings");
        }
        ToolCompatibilitySystemPromptCatalog catalog = ToolCompatibilityProtocol.systemPromptCatalog();
        ToolCompatibilityPairedSchedule pairedSchedule = ToolCompatibilityPairedSchedule.locked();
        ToolCompatibilityProtocol.caseSelection().requireBoundTo(ToolCompatibilityProtocol.caseOracle());
        List<ToolCallback> callbacks = ToolCompatibilityCallbackCatalog.canonicalCallbacks();
        ToolCompatibilityToolDefinitionIdentity.canonical();

        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(repositoryState.capture(projectDirectory));
        ToolCompatibilityPreflight.Session session = sessionFactory.create(input.ollamaBaseUrl(), timeout);
        if (session == null) {
            throw new IllegalStateException("Prompt-matrix session factory returned no session");
        }
        ToolCompatibilityModelIdentity modelIdentity = session.requireInstalled(model);
        if (modelIdentity == null
                || !model.equals(modelIdentity.requestedModel())
                || !model.equals(modelIdentity.effectiveModel())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Installed model identity does not match the locked Phase 2 model");
        }
        return new Prepared(
                projectDirectory,
                baselineOutputDirectory,
                candidateOutputDirectory,
                settings,
                modelIdentity,
                callbacks,
                catalog,
                pairedSchedule,
                codeBaseline,
                repositoryState,
                session);
    }

    static AllocatedOutputs allocateBoth(Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared prompt matrix must not be null");
        }
        prepared.requireRepositoryUnchanged();
        Path baseline = ToolCompatibilityPreflight.allocate(prepared.baselineOutputDirectory());
        prepared.requireRepositoryUnchanged();
        Path candidate = ToolCompatibilityPreflight.allocate(prepared.candidateOutputDirectory());
        return new AllocatedOutputs(baseline, candidate);
    }

    private static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline baseline) {
        if (baseline == null
                || baseline.workingTreeDirty()
                || baseline.gitCommit() == null
                || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Prompt-matrix execution requires one clean Git commit before allocation");
        }
        return baseline;
    }

    private static String requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "toolCompatibilityPromptMatrix requires " + option + "=<value>");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(option + " must not have surrounding whitespace");
        }
        return value;
    }

    record Input(
            Path projectDirectory,
            String ollamaBaseUrl,
            String model,
            String maxTokens,
            String timeout,
            String baselineOutputDirectory,
            String candidateOutputDirectory
    ) {}

    record Prepared(
            Path projectDirectory,
            Path baselineOutputDirectory,
            Path candidateOutputDirectory,
            ToolCompatibilityRunSettings settings,
            ToolCompatibilityModelIdentity modelIdentity,
            List<ToolCallback> callbacks,
            ToolCompatibilitySystemPromptCatalog catalog,
            ToolCompatibilityPairedSchedule pairedSchedule,
            EvidenceCodeBaseline codeBaseline,
            RepositoryState repositoryState,
            ToolCompatibilityPreflight.Session session
    ) {

        Prepared {
            if (projectDirectory == null
                    || baselineOutputDirectory == null
                    || candidateOutputDirectory == null
                    || settings == null
                    || modelIdentity == null
                    || catalog == null
                    || pairedSchedule == null
                    || codeBaseline == null
                    || repositoryState == null
                    || session == null) {
                throw new IllegalArgumentException("Prepared prompt matrix is incomplete");
            }
            if (!ToolCompatibilityProtocol.runSettings().equals(settings)) {
                throw new IllegalArgumentException("Prepared prompt-matrix settings drifted from the locked protocol");
            }
            catalog.requirePrompt(ToolCompatibilityPromptCondition.UNTREATED.prompt(catalog));
            catalog.requirePrompt(ToolCompatibilityPromptCondition.PROMPTED.prompt(catalog));
            pairedSchedule.requireLocked();
            callbacks = List.copyOf(callbacks == null ? List.of() : callbacks);
            ToolCompatibilityCallbackCatalog.requireExactCallbacks(callbacks);
            requireCleanBaseline(codeBaseline);
        }

        void requireRepositoryUnchanged() {
            EvidenceCodeBaseline current = requireCleanBaseline(repositoryState.capture(projectDirectory));
            if (!codeBaseline.gitCommit().equals(current.gitCommit())) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Git commit drifted during paired prompt-matrix execution");
            }
        }
    }

    record AllocatedOutputs(Path baseline, Path candidate) {

        AllocatedOutputs {
            if (baseline == null || candidate == null || baseline.equals(candidate)) {
                throw new IllegalArgumentException("Both distinct allocated prompt-matrix outputs are required");
            }
        }
    }

    @FunctionalInterface
    interface RepositoryState {

        EvidenceCodeBaseline capture(Path projectDirectory);
    }
}
