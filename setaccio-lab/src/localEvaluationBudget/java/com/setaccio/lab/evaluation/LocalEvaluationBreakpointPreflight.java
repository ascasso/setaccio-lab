package com.setaccio.lab.evaluation;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates every fresh breakpoint arm before any output directory is allocated. */
final class LocalEvaluationBreakpointPreflight {

    Prepared prepare(
            Input input,
            LocalEvaluationBudgetPreflight.ContractLoader contractLoader,
            LocalEvaluationBudgetPreflight.RepositoryState repositoryState,
            LocalEvaluationPreflight.JudgeSessionFactory sessionFactory
    ) {
        if (input == null || contractLoader == null || repositoryState == null || sessionFactory == null) {
            throw new IllegalArgumentException("Breakpoint preflight dependencies must not be null");
        }
        Path projectDirectory = requireProjectDirectory(input.projectDirectory());
        LocalFactCheckJudgeModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        String judgeModel = requireOption(input.judgeModel(), "--judge-model");
        Map<Integer, Path> outputDirectories = resolveOutputs(projectDirectory, input.outputDirectories());
        LocalEvaluationContract contract = contractLoader.load();
        if (contract == null) {
            throw new IllegalArgumentException("Breakpoint preflight failed: fact-check contract is absent");
        }
        contract.requireLockedAndConfirmed();
        EvidenceCodeBaseline baseline = LocalEvaluationBudgetPreflight.requireCleanBaseline(
                repositoryState.capture(projectDirectory));
        LocalEvaluationPreflight.JudgeSession session = sessionFactory.create(
                input.ollamaBaseUrl(), LocalEvaluationBreakpointProtocol.TIMEOUT);
        if (session == null) {
            throw new IllegalStateException("Breakpoint judge session factory returned no session");
        }
        LocalEvaluationModelIdentity identity = session.requireInstalled(judgeModel);
        if (identity == null || !LocalEvaluationProtocol.normalizeModelTag(judgeModel)
                .equals(identity.normalizedInstalledName())) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model identity does not match the requested tag");
        }
        Map<Integer, LocalEvaluationRunSettings> settings = new LinkedHashMap<>();
        for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            settings.put(maxTokens, LocalEvaluationBreakpointProtocol.settings(judgeModel, maxTokens));
        }
        LocalEvaluationBreakpointProtocol.requireStudySettings(List.copyOf(settings.values()));
        return new Prepared(projectDirectory, outputDirectories, settings, identity, contract, baseline,
                repositoryState, session);
    }

    private static Path requireProjectDirectory(Path projectDirectory) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        return projectDirectory.toAbsolutePath().normalize();
    }

    private static Map<Integer, Path> resolveOutputs(Path projectDirectory, Map<Integer, String> requested) {
        if (requested == null || !requested.keySet().equals(new java.util.LinkedHashSet<>(
                LocalEvaluationBreakpointProtocol.MAX_TOKENS))) {
            throw new IllegalArgumentException("Breakpoint requires one output directory for every locked token arm");
        }
        Map<Integer, Path> resolved = new LinkedHashMap<>();
        for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
            resolved.put(maxTokens, LocalEvaluationPreflight.resolveNewOutputDirectory(
                    projectDirectory, requested.get(maxTokens)));
        }
        if (resolved.values().stream().distinct().count() != resolved.size()) {
            throw new LocalEvaluationBudgetProtocolIntegrityException(
                    "Breakpoint token-arm output directories must all differ");
        }
        return Map.copyOf(resolved);
    }

    private static String requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("localEvaluationBreakpoint requires " + option + "=<value>");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(option + " must not have surrounding whitespace");
        }
        return value;
    }

    record Input(Path projectDirectory, String ollamaBaseUrl, String judgeModel, Map<Integer, String> outputDirectories) {}

    record Prepared(
            Path projectDirectory,
            Map<Integer, Path> outputDirectories,
            Map<Integer, LocalEvaluationRunSettings> settings,
            LocalEvaluationModelIdentity modelIdentity,
            LocalEvaluationContract contract,
            EvidenceCodeBaseline codeBaseline,
            LocalEvaluationBudgetPreflight.RepositoryState repositoryState,
            LocalEvaluationPreflight.JudgeSession session
    ) {
        Prepared {
            if (projectDirectory == null || outputDirectories == null || settings == null || modelIdentity == null
                    || contract == null || codeBaseline == null || repositoryState == null || session == null) {
                throw new IllegalArgumentException("Breakpoint prepared protocol is incomplete");
            }
            LocalEvaluationBreakpointProtocol.requireStudySettings(List.copyOf(settings.values()));
            LocalEvaluationBudgetPreflight.requireCleanBaseline(codeBaseline);
        }

        void requireRepositoryUnchanged() {
            EvidenceCodeBaseline current = LocalEvaluationBudgetPreflight.requireCleanBaseline(
                    repositoryState.capture(projectDirectory));
            if (!codeBaseline.gitCommit().equals(current.gitCommit())) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Git commit drifted during breakpoint execution");
            }
        }

        void requireModelIdentityUnchanged() {
            LocalEvaluationModelIdentity current = session.requireInstalled(settings.get(64).requestedModel());
            if (!modelIdentity.equals(current)) {
                throw new LocalEvaluationBudgetProtocolIntegrityException(
                        "Installed judge model identity drifted during breakpoint execution");
            }
        }

        Map<Integer, Path> allocateAll() {
            Map<Integer, Path> allocated = new LinkedHashMap<>();
            for (int maxTokens : LocalEvaluationBreakpointProtocol.MAX_TOKENS) {
                requireRepositoryUnchanged();
                Path output = outputDirectories.get(maxTokens);
                allocated.put(maxTokens, EvidenceRunDirectory.createNamed(
                        output.getParent(), output.getFileName().toString()));
            }
            requireRepositoryUnchanged();
            return Map.copyOf(allocated);
        }

        LocalEvaluationPreflight.Prepared arm(int maxTokens, Path outputDirectory) {
            LocalEvaluationBreakpointProtocol.requireArm(maxTokens);
            if (!outputDirectories.get(maxTokens).equals(outputDirectory)) {
                throw new IllegalArgumentException("Breakpoint output directory does not match its token arm");
            }
            return new LocalEvaluationPreflight.Prepared(outputDirectory, settings.get(maxTokens), modelIdentity,
                    contract, session);
        }
    }
}
