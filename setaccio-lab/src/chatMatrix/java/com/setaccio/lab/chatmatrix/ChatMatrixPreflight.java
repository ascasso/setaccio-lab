package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatMatrixPreflight {

    private static final int MAX_TOKENS = 32768;
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(10);
    private static final Pattern DATE = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");

    Prepared prepare(Input input, CatalogLoader catalogLoader, SessionFactory sessionFactory) {
        if (input == null || catalogLoader == null || sessionFactory == null) {
            throw new IllegalArgumentException("Chat matrix preflight dependencies must not be null");
        }
        OllamaChatModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        int maxOutputTokens = parseMaxTokens(input.maxOutputTokens());
        Duration timeout = parseTimeout(input.timeout());
        Path outputDirectory = resolveNewOutputDirectory(input.projectDirectory(), input.outputDirectory());
        ChatMatrixRunSettings settings = ChatMatrixProtocol.settings(
                requireOption(input.model(), "--model"),
                maxOutputTokens,
                timeout);

        ChatPromptCatalog catalog = catalogLoader.load();
        if (catalog == null) {
            throw new IllegalArgumentException("Chat matrix preflight failed: prompt catalog is absent");
        }
        catalog.requireLocked();
        Session session = sessionFactory.create(input.ollamaBaseUrl(), timeout);
        if (session == null) {
            throw new IllegalStateException("Chat matrix session factory returned no session");
        }
        OllamaChatModelIdentity modelIdentity = session.requireInstalled(settings.requestedModel());
        if (modelIdentity == null) {
            throw new IllegalArgumentException("Installed Ollama chat model identity was not resolved");
        }
        if (!OllamaChatModelIdentity.OLLAMA_PROVIDER_ID.equals(modelIdentity.providerId())
                || !settings.requestedModel().equals(modelIdentity.requestedModel())
                || !ChatMatrixProtocol.normalizeModelTag(settings.requestedModel())
                        .equals(modelIdentity.effectiveModel())) {
            throw new IllegalArgumentException(
                    "Installed Ollama chat model identity does not match the requested tag");
        }
        if (Files.exists(outputDirectory)) {
            throw new IllegalArgumentException("Output directory already exists: " + outputDirectory);
        }
        return new Prepared(outputDirectory, settings, modelIdentity, catalog, session);
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
                    "--timeout must be an ISO-8601 duration such as PT30S",
                    exception);
        }
    }

    static Path resolveNewOutputDirectory(Path projectDirectory, String value) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        String requested = requireOption(value, "--output-dir");
        Path output = ChatMatrixProtocol.EVIDENCE_ROOT.resolveNewRunDirectory(
                projectDirectory, requested, "Output");
        String runId = output.getFileName().toString();
        Matcher matcher = DATE.matcher(runId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Output directory name must contain a YYYY-MM-DD date");
        }
        try {
            LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Output directory name must contain a valid YYYY-MM-DD date",
                    exception);
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
            throw new IllegalArgumentException("chatMatrix requires " + option + "=<value>");
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
            String maxOutputTokens,
            String timeout,
            String outputDirectory
    ) {}

    record Prepared(
            Path outputDirectory,
            ChatMatrixRunSettings settings,
            OllamaChatModelIdentity modelIdentity,
            ChatPromptCatalog catalog,
            Session session
    ) {}

    @FunctionalInterface
    interface CatalogLoader {
        ChatPromptCatalog load();
    }

    @FunctionalInterface
    interface SessionFactory {
        Session create(String baseUrl, Duration timeout);
    }

    interface Session {
        OllamaChatModelIdentity requireInstalled(String requestedModel);

        ChatInvocationOutcome invoke(
                ChatPromptCase prompt,
                OllamaChatModelIdentity modelIdentity,
                ChatGenerationSettings settings);
    }
}
