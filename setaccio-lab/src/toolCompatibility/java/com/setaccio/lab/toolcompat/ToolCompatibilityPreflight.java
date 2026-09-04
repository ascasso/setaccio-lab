package com.setaccio.lab.toolcompat;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.tool.ToolCallback;

/** Validates the complete locked local protocol before evidence allocation. */
final class ToolCompatibilityPreflight {

    private static final Pattern DATE = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");

    Prepared prepare(Input input, SessionFactory sessionFactory) {
        if (input == null || sessionFactory == null) {
            throw new IllegalArgumentException("Tool compatibility preflight dependencies are required");
        }
        OllamaChatModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        String model = requireOption(input.model(), "--model");
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(model)) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "--model must equal the locked Phase 1 model tag");
        }
        int maxTokens = parseMaxTokens(input.maxTokens());
        Duration timeout = parseTimeout(input.timeout());
        Path outputDirectory = resolveNewOutputDirectory(
                input.projectDirectory(), input.outputDirectory());

        ToolCompatibilityRunSettings settings = ToolCompatibilityProtocol.runSettings();
        if (maxTokens != settings.maxOutputTokensPerProviderTurn()
                || timeout.toMillis() != settings.rowTimeoutMillis()) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Explicit options must equal the locked Phase 1 settings");
        }
        ToolCompatibilityProtocol.caseSelection().requireBoundTo(
                ToolCompatibilityProtocol.caseOracle());
        List<ToolCallback> callbacks = ToolCompatibilityCallbackCatalog.canonicalCallbacks();
        ToolCompatibilityToolDefinitionIdentity.canonical();

        Session session = sessionFactory.create(input.ollamaBaseUrl(), timeout);
        if (session == null) {
            throw new IllegalStateException("Tool compatibility session factory returned no session");
        }
        ToolCompatibilityModelIdentity modelIdentity = session.requireInstalled(model);
        if (modelIdentity == null
                || !model.equals(modelIdentity.requestedModel())
                || !model.equals(modelIdentity.effectiveModel())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Installed model identity does not match the locked Phase 1 model");
        }
        requireNewOutputDirectory(outputDirectory);
        return new Prepared(
                outputDirectory,
                settings,
                modelIdentity,
                callbacks,
                session);
    }

    static int parseMaxTokens(String value) {
        String normalized = requireOption(value, "--max-tokens");
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed != ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN) {
                throw new IllegalArgumentException(
                        "--max-tokens must equal the locked value 512");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--max-tokens must be the integer 512", exception);
        }
    }

    static Duration parseTimeout(String value) {
        String normalized = requireOption(value, "--timeout");
        try {
            Duration parsed = Duration.parse(normalized);
            if (!ToolCompatibilityProtocol.ROW_TIMEOUT.equals(parsed)) {
                throw new IllegalArgumentException("--timeout must equal the locked PT2M row deadline");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("--timeout must be the ISO-8601 duration PT2M", exception);
        }
    }

    static Path resolveNewOutputDirectory(Path projectDirectory, String value) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        String requested = requireOption(value, "--output-dir");
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path root = ToolCompatibilityProtocol.EVIDENCE_ROOT.durableRoot(project);
        Path output = ToolCompatibilityProtocol.EVIDENCE_ROOT.resolveNewRunDirectory(
                project, requested, "Output");
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
        requireNoSymbolicLinks(project, output);
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "Tool compatibility output root is not a regular directory");
        }
        requireNewOutputDirectory(output);
        return output;
    }

    static Path allocate(Path outputDirectory) {
        if (outputDirectory == null || outputDirectory.getParent() == null) {
            throw new IllegalArgumentException("outputDirectory must identify a named run directory");
        }
        Path output = outputDirectory.toAbsolutePath().normalize();
        requireNoSymbolicLinks(
                ToolCompatibilityProtocol.EVIDENCE_ROOT.projectDirectoryOfDurableRun(output),
                output);
        requireNewOutputDirectory(output);
        return EvidenceRunDirectory.createNamed(output.getParent(), output.getFileName().toString());
    }

    private static void requireNewOutputDirectory(Path output) {
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException("Output directory already exists or is unsafe: " + output);
        }
    }

    static void requireNoSymbolicLinks(Path root, Path target) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes the project directory");
        }
        Path current = normalizedRoot;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException("Project directory must not be a symbolic link");
        }
        for (Path segment : normalizedRoot.relativize(normalizedTarget)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(
                        "Tool compatibility path must not contain symbolic links: " + current);
            }
        }
    }

    private static String requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "toolCompatibilityMatrix requires " + option + "=<value>");
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
            String outputDirectory
    ) {}

    record Prepared(
            Path outputDirectory,
            ToolCompatibilityRunSettings settings,
            ToolCompatibilityModelIdentity modelIdentity,
            List<ToolCallback> callbacks,
            Session session
    ) {

        Prepared {
            if (outputDirectory == null
                    || settings == null
                    || modelIdentity == null
                    || session == null) {
                throw new IllegalArgumentException("Prepared tool compatibility run is incomplete");
            }
            callbacks = List.copyOf(callbacks == null ? List.of() : callbacks);
            ToolCompatibilityCallbackCatalog.requireExactCallbacks(callbacks);
        }
    }

    @FunctionalInterface
    interface SessionFactory {
        Session create(String baseUrl, Duration timeout);
    }

    interface Session {
        ToolCompatibilityModelIdentity requireInstalled(String requestedModel);

        ToolCompatibilityControlledOllamaModel controlledModel(int seed);
    }
}
