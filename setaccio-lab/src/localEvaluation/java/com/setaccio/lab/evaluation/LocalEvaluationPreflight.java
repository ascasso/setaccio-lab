package com.setaccio.lab.evaluation;

import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalEvaluationPreflight {

    private static final int MAX_TOKENS = 32768;
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(10);
    private static final Pattern DATE = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");

    Prepared prepare(
            Input input,
            ContractLoader contractLoader,
            JudgeSessionFactory sessionFactory
    ) {
        if (input == null || contractLoader == null || sessionFactory == null) {
            throw new IllegalArgumentException("Local evaluation preflight dependencies must not be null");
        }
        LocalFactCheckJudgeModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        int maxTokens = parseMaxTokens(input.maxTokens());
        Duration timeout = parseTimeout(input.timeout());
        Path outputDirectory = resolveNewOutputDirectory(input.projectDirectory(), input.outputDirectory());
        LocalEvaluationRunSettings settings = LocalEvaluationProtocol.settings(
                requireOption(input.judgeModel(), "--judge-model"),
                maxTokens,
                timeout);

        LocalEvaluationContract contract = contractLoader.load();
        if (contract == null) {
            throw new IllegalArgumentException("Local evaluation preflight failed: fixture review contract is absent");
        }
        contract.requireLockedAndConfirmed();

        JudgeSession session = sessionFactory.create(input.ollamaBaseUrl(), timeout);
        if (session == null) {
            throw new IllegalStateException("Local evaluation judge session factory returned no session");
        }
        LocalEvaluationModelIdentity modelIdentity = session.requireInstalled(settings.requestedModel());
        if (modelIdentity == null) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model identity was not resolved");
        }
        if (!LocalEvaluationProtocol.normalizeModelTag(settings.requestedModel())
                .equals(modelIdentity.normalizedInstalledName())) {
            throw new LocalFactCheckJudgeModelUnavailableException(
                    "Installed Ollama judge model identity does not match the requested tag");
        }
        if (Files.exists(outputDirectory)) {
            throw new IllegalArgumentException("Output directory already exists: " + outputDirectory);
        }
        return new Prepared(outputDirectory, settings, modelIdentity, contract, session);
    }

    static int parseMaxTokens(String value) {
        String normalized = requireOption(value, "--max-tokens");
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < 1 || parsed > MAX_TOKENS) {
                throw new IllegalArgumentException("--max-tokens must be between 1 and " + MAX_TOKENS);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--max-tokens must be an integer", exception);
        }
    }

    static Duration parseTimeout(String value) {
        String normalized = requireOption(value, "--timeout");
        try {
            Duration parsed = Duration.parse(normalized);
            if (parsed.isZero() || parsed.isNegative() || parsed.compareTo(MAX_TIMEOUT) > 0) {
                throw new IllegalArgumentException("--timeout must be positive and no greater than PT10M");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "--timeout must be an ISO-8601 duration such as PT30S", exception);
        }
    }

    static Path resolveNewOutputDirectory(Path projectDirectory, String value) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        String requested = requireOption(value, "--output-dir");
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path root = project.resolve("build/evaluation-matrix").normalize();
        Path output = project.resolve(requested).normalize();
        if (!root.equals(output.getParent())) {
            throw new IllegalArgumentException(
                    "Output must be one new directory directly under build/evaluation-matrix/");
        }
        String runId = output.getFileName().toString();
        if (!runId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Output directory name must be one safe path segment");
        }
        Matcher matcher = DATE.matcher(runId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Output directory name must contain a YYYY-MM-DD date");
        }
        try {
            LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Output directory name must contain a valid YYYY-MM-DD date", exception);
        }
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output directory already exists: " + output);
        }
        return output;
    }

    static Path allocate(Prepared prepared) {
        return EvidenceRunDirectory.createNamed(
                prepared.outputDirectory().getParent(),
                prepared.outputDirectory().getFileName().toString());
    }

    private static String requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("localEvaluation requires " + option + "=<value>");
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
            String maxTokens,
            String timeout,
            String outputDirectory
    ) {}

    record Prepared(
            Path outputDirectory,
            LocalEvaluationRunSettings settings,
            LocalEvaluationModelIdentity modelIdentity,
            LocalEvaluationContract contract,
            JudgeSession session
    ) {}

    @FunctionalInterface
    interface ContractLoader {
        LocalEvaluationContract load();
    }

    @FunctionalInterface
    interface JudgeSessionFactory {
        JudgeSession create(String baseUrl, Duration timeout);
    }

    interface JudgeSession {
        LocalEvaluationModelIdentity requireInstalled(String requestedModel);

        LocalFactCheckJudgeResult evaluate(
                LocalFactCheckFixture fixture,
                LocalFactCheckJudgeSettings settings,
                LocalFactCheckPromptDefinition prompt);
    }
}
