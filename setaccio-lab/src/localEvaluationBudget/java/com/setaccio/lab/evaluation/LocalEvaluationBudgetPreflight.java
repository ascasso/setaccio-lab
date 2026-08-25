package com.setaccio.lab.evaluation;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Path;

/** Validates both fresh F1 arms before either output directory is allocated. */
final class LocalEvaluationBudgetPreflight {

    Prepared prepare(
            Input input,
            ContractLoader contractLoader,
            RepositoryState repositoryState,
            LocalEvaluationPreflight.JudgeSessionFactory sessionFactory
    ) {
        if (input == null || contractLoader == null || repositoryState == null || sessionFactory == null) {
            throw new IllegalArgumentException("F1 preflight dependencies must not be null");
        }
        if (input.projectDirectory() == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }

        Path projectDirectory = input.projectDirectory().toAbsolutePath().normalize();
        LocalFactCheckJudgeModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        String judgeModel = requireOption(input.judgeModel(), "--judge-model");
        Path output64 = LocalEvaluationPreflight.resolveNewOutputDirectory(
                projectDirectory,
                input.outputDirectory64());
        Path output256 = LocalEvaluationPreflight.resolveNewOutputDirectory(
                projectDirectory,
                input.outputDirectory256());
        if (output64.equals(output256)) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 64-token and 256-token output directories must differ");
        }

        LocalEvaluationContract contract = loadContract(contractLoader);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                repositoryState.capture(projectDirectory));
        LocalEvaluationPreflight.JudgeSession session = sessionFactory.create(
                input.ollamaBaseUrl(),
                LocalEvaluationBudgetProtocol.TIMEOUT);
        if (session == null) {
            throw new IllegalStateException("F1 judge session factory returned no session");
        }
        LocalEvaluationModelIdentity modelIdentity = session.requireInstalled(judgeModel);
        if (modelIdentity == null) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model identity was not resolved");
        }
        if (!LocalEvaluationProtocol.normalizeModelTag(judgeModel)
                .equals(modelIdentity.normalizedInstalledName())) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model identity does not match the requested tag");
        }

        LocalEvaluationRunSettings budget64 = LocalEvaluationBudgetProtocol.settings(judgeModel, 64);
        LocalEvaluationRunSettings budget256 = LocalEvaluationBudgetProtocol.settings(judgeModel, 256);
        LocalEvaluationBudgetProtocol.requirePairSettings(budget64, budget256);
        return new Prepared(
                projectDirectory,
                output64,
                output256,
                budget64,
                budget256,
                modelIdentity,
                contract,
                codeBaseline,
                repositoryState,
                session);
    }

    private static LocalEvaluationContract loadContract(ContractLoader contractLoader) {
        LocalEvaluationContract contract = contractLoader.load();
        if (contract == null) {
            throw new IllegalArgumentException("F1 preflight failed: fact-check contract is absent");
        }
        contract.requireLockedAndConfirmed();
        return contract;
    }

    static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline baseline) {
        if (baseline == null
                || baseline.workingTreeDirty()
                || baseline.gitCommit() == null
                || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "F1 execution requires one clean Git commit before allocation");
        }
        return baseline;
    }

    private static String requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("localEvaluationBudget requires " + option + "=<value>");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(option + " must not have surrounding whitespace");
        }
        return value;
    }

    record Input(
            Path projectDirectory,
            String ollamaBaseUrl,
            String judgeModel,
            String outputDirectory64,
            String outputDirectory256
    ) {}

    record Prepared(
            Path projectDirectory,
            Path outputDirectory64,
            Path outputDirectory256,
            LocalEvaluationRunSettings budget64,
            LocalEvaluationRunSettings budget256,
            LocalEvaluationModelIdentity modelIdentity,
            LocalEvaluationContract contract,
            EvidenceCodeBaseline codeBaseline,
            RepositoryState repositoryState,
            LocalEvaluationPreflight.JudgeSession session
    ) {

        Prepared {
            if (projectDirectory == null
                    || outputDirectory64 == null
                    || outputDirectory256 == null
                    || budget64 == null
                    || budget256 == null
                    || modelIdentity == null
                    || contract == null
                    || codeBaseline == null
                    || repositoryState == null
                    || session == null) {
                throw new IllegalArgumentException("F1 prepared protocol is incomplete");
            }
            if (outputDirectory64.equals(outputDirectory256)) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "F1 prepared output directories must differ");
            }
            LocalEvaluationBudgetProtocol.requirePairSettings(budget64, budget256);
            requireCleanBaseline(codeBaseline);
        }

        void requireRepositoryUnchanged() {
            EvidenceCodeBaseline current = requireCleanBaseline(repositoryState.capture(projectDirectory));
            if (!codeBaseline.gitCommit().equals(current.gitCommit())) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Git commit drifted during F1 paired execution");
            }
        }

        void requireModelIdentityUnchanged() {
            LocalEvaluationModelIdentity current = session.requireInstalled(budget64.requestedModel());
            if (current == null || !modelIdentity.equals(current)) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Installed judge model identity drifted between F1 checks");
            }
        }

        LocalEvaluationPreflight.Prepared arm(int maxTokens, Path outputDirectory) {
            LocalEvaluationBudgetProtocol.requireArm(maxTokens);
            if (outputDirectory == null) {
                throw new IllegalArgumentException("outputDirectory must not be null");
            }
            LocalEvaluationRunSettings settings = maxTokens == 64 ? budget64 : budget256;
            return new LocalEvaluationPreflight.Prepared(
                    outputDirectory,
                    settings,
                    modelIdentity,
                    contract,
                    session);
        }

        AllocatedOutputs allocateBoth() {
            requireRepositoryUnchanged();
            Path allocated64 = EvidenceRunDirectory.createNamed(
                    outputDirectory64.getParent(),
                    outputDirectory64.getFileName().toString());
            requireRepositoryUnchanged();
            Path allocated256 = EvidenceRunDirectory.createNamed(
                    outputDirectory256.getParent(),
                    outputDirectory256.getFileName().toString());
            requireRepositoryUnchanged();
            return new AllocatedOutputs(allocated64, allocated256);
        }
    }

    record AllocatedOutputs(Path budget64, Path budget256) {

        AllocatedOutputs {
            if (budget64 == null || budget256 == null || budget64.equals(budget256)) {
                throw new IllegalArgumentException("F1 must allocate two distinct arm directories");
            }
        }
    }

    @FunctionalInterface
    interface ContractLoader {
        LocalEvaluationContract load();
    }

    @FunctionalInterface
    interface RepositoryState {
        EvidenceCodeBaseline capture(Path projectDirectory);
    }
}
