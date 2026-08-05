package com.setaccio.lab.chatmatrix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.chat.AnthropicChatModelFactory;
import com.setaccio.lab.chat.AnthropicChatModelIdentity;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceManifest;
import com.setaccio.lab.evidence.EvidenceManifestStore;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit entry point for the one authorized, six-call Anthropic portability proof. */
public final class AnthropicChatMatrixRunner {

    private static final BigDecimal AUTHORIZED_USD_MAXIMUM = new BigDecimal("3.00");
    private static final Pattern DATE = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");

    private AnthropicChatMatrixRunner() {}

    public static void main(String[] args) {
        Arguments arguments = Arguments.parse(args);
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ChatPromptCatalog catalog = ChatPromptCatalog.load(mapper);
        ChatPortabilityRunSettings settings = AnthropicChatMatrixProtocol.settings(catalog);
        requireLockedOptions(arguments, settings);
        BigDecimal maximumCost = parseMaximumCost(arguments.maximumCostUsd());
        ChatEstimatedCost estimate = AnthropicChatMatrixProtocol.costEstimate(Instant.now());
        if (estimate.estimatedUsd().compareTo(maximumCost) > 0) {
            throw new IllegalArgumentException("authorized maximum USD does not cover the locked preflight estimate");
        }
        Path outputDirectory = resolveNewOutputDirectory(Path.of(""), arguments.outputDirectory());
        Path ollamaRunDirectory = resolveOllamaRunDirectory(Path.of(""), arguments.ollamaRunDirectory());
        ChatPortabilitySnapshot baseline = verifiedOllamaSnapshot(mapper, ollamaRunDirectory);
        String apiKey = requireLocalCredential();
        AnthropicChatModelIdentity modelIdentity = AnthropicChatMatrixProtocol.modelIdentity();
        EvidenceCodeBaseline codeBaseline = EvidenceProvenance.captureCodeBaseline(Path.of(""));
        printPreflight(modelIdentity, estimate, maximumCost, outputDirectory, codeBaseline);

        Path allocated = EvidenceRunDirectory.createNamed(outputDirectory.getParent(), outputDirectory.getFileName().toString());
        try {
            LiveSession session = new LiveSession(apiKey);
            AnthropicChatMatrixResult result = new AnthropicChatMatrixExecutor().execute(
                    new AnthropicChatMatrixExecutor.Prepared(
                            catalog, settings, modelIdentity, estimate, maximumCost, session));
            AnthropicChatMatrixEvidence evidence = new AnthropicChatMatrixEvidence(mapper);
            evidence.write(allocated, result, codeBaseline);
            AnthropicChatMatrixEvidence.OfflineResult verification = evidence.verify(allocated);
            if (!verification.valid()) {
                throw new IllegalStateException("Anthropic evidence verification failed: "
                        + String.join(" ", verification.failures()));
            }
            AnthropicChatMatrixEvidence.OfflineResult reanalysis = evidence.reanalyze(allocated);
            if (!reanalysis.valid()) {
                throw new IllegalStateException("Anthropic evidence reanalysis failed: "
                        + String.join(" ", reanalysis.failures()));
            }
            ChatPortabilitySnapshot candidate = AnthropicChatMatrixSnapshotFactory.fromResult(
                    allocated.getFileName().toString(), result, EvidenceProvenance.detectFrameworkVersions(), codeBaseline);
            System.out.println(new ChatPortabilityReport().render(baseline, candidate));
            System.out.println("Anthropic chat matrix complete: "
                    + allocated.resolve(AnthropicChatMatrixProtocol.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Anthropic chat matrix failed after output allocation; retained diagnostic directory: " + allocated,
                    exception);
        }
    }

    private static void requireLockedOptions(Arguments arguments, ChatPortabilityRunSettings settings) {
        if (parseMaxTokens(arguments.maxTokens()) != settings.maxOutputTokens()) {
            throw new IllegalArgumentException("Anthropic O3 requires --max-tokens 128");
        }
        if (!parseTimeout(arguments.timeout()).equals(Duration.ofMillis(settings.timeoutMillis()))) {
            throw new IllegalArgumentException("Anthropic O3 requires --timeout PT2M");
        }
    }

    static int parseMaxTokens(String value) {
        try {
            int tokens = Integer.parseInt(require(value, "--max-tokens"));
            if (tokens < 1 || tokens > 32768) {
                throw new IllegalArgumentException("--max-tokens must be between 1 and 32768");
            }
            return tokens;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--max-tokens must be an integer", exception);
        }
    }

    static Duration parseTimeout(String value) {
        try {
            Duration timeout = Duration.parse(require(value, "--timeout"));
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(10)) > 0) {
                throw new IllegalArgumentException("--timeout must be positive and no greater than PT10M");
            }
            return timeout;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("--timeout must be an ISO-8601 duration", exception);
        }
    }

    static BigDecimal parseMaximumCost(String value) {
        try {
            BigDecimal cost = new BigDecimal(require(value, "--max-cost-usd"));
            if (cost.signum() <= 0 || cost.compareTo(AUTHORIZED_USD_MAXIMUM) > 0) {
                throw new IllegalArgumentException("--max-cost-usd must be greater than 0 and no more than 3.00");
            }
            return cost;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--max-cost-usd must be a decimal USD amount", exception);
        }
    }

    static Path resolveNewOutputDirectory(Path projectDirectory, String value) {
        Path output = resolveDirectChild(projectDirectory, value, "build/anthropic-chat-matrix", "Output");
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Output directory already exists: " + output);
        }
        return output;
    }

    static Path resolveOllamaRunDirectory(Path projectDirectory, String value) {
        Path run = resolveDirectChild(projectDirectory, value, "build/chat-matrix", "Ollama run");
        if (!Files.isDirectory(run) || Files.isSymbolicLink(run)) {
            throw new IllegalArgumentException("Ollama run directory does not exist or is unsafe");
        }
        return run;
    }

    private static Path resolveDirectChild(Path projectDirectory, String value, String relativeRoot, String label) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("projectDirectory must not be null");
        }
        Path project = projectDirectory.toAbsolutePath().normalize();
        Path root = project.resolve(relativeRoot).normalize();
        Path target = project.resolve(require(value, "--" + (label.equals("Output") ? "output-dir" : "ollama-run-dir"))).normalize();
        if (!root.equals(target.getParent())) {
            throw new IllegalArgumentException(label + " directory must be directly under " + relativeRoot + "/");
        }
        String name = target.getFileName().toString();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(label + " directory name must be one safe path segment");
        }
        Matcher matcher = DATE.matcher(name);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(label + " directory name must contain a YYYY-MM-DD date");
        }
        try {
            LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + " directory name must contain a valid YYYY-MM-DD date", exception);
        }
        return target;
    }

    private static ChatPortabilitySnapshot verifiedOllamaSnapshot(ObjectMapper mapper, Path runDirectory) {
        ChatMatrixEvidence evidence = new ChatMatrixEvidence(mapper, ChatPromptCatalog.load(mapper));
        ChatMatrixEvidence.OfflineResult verification = evidence.verify(runDirectory);
        if (!verification.valid()) {
            throw new IllegalArgumentException("Saved Ollama evidence is not valid: " + String.join(" ", verification.failures()));
        }
        try {
            ChatMatrixResult result = mapper.readerFor(ChatMatrixResult.class).readValue(
                    runDirectory.resolve(ChatMatrixProtocol.RAW_FILENAME).toFile());
            EvidenceManifest manifest = new EvidenceManifestStore(mapper).read(runDirectory);
            return ChatPortabilitySnapshotFactory.fromVerifiedOllama(
                    runDirectory.getFileName().toString(), result, manifest,
                    new ChatEstimatedCost("USD", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), "https://ollama.com"));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Saved Ollama evidence could not be loaded after verification", exception);
        }
    }

    private static String requireLocalCredential() {
        String credential = System.getenv("ANTHROPIC_API_KEY");
        if (credential == null || credential.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY must be supplied through local environment configuration");
        }
        return credential;
    }

    private static void printPreflight(
            AnthropicChatModelIdentity identity,
            ChatEstimatedCost estimate,
            BigDecimal maximumCost,
            Path outputDirectory,
            EvidenceCodeBaseline codeBaseline
    ) {
        System.out.println("Starting authorized Anthropic chat matrix");
        System.out.println("  Provider/model: " + identity.providerId() + " / " + identity.requestedModel());
        System.out.println("  Model identity: versioned hosted ID; no local digest");
        System.out.println("  Rows: 6 sequential (3 prompts x 2 unseeded repetitions)");
        System.out.println("  Temperature: 0.0; seed: unsupported and not simulated");
        System.out.println("  Max output tokens: 128; timeout: PT2M; attempts: 1");
        System.out.println("  Official price source: " + estimate.officialPriceSource());
        System.out.println("  Preflight estimate USD: " + estimate.estimatedUsd());
        System.out.println("  Authorized maximum USD: " + maximumCost);
        System.out.println("  Credentials: supplied locally and excluded from output/evidence");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Output: " + outputDirectory);
    }

    private static String require(String value, String option) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("anthropicChatMatrix requires " + option + "=<value>");
        }
        return value;
    }

    private static final class LiveSession implements AnthropicChatMatrixExecutor.Session {

        private final AnthropicChatModelFactory modelFactory = new AnthropicChatModelFactory();
        private final String apiKey;
        private final Map<ChatGenerationSettings, ChatInvocation> invocations = new LinkedHashMap<>();

        private LiveSession(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public ChatInvocationOutcome invoke(
                ChatPromptCase prompt,
                AnthropicChatModelIdentity modelIdentity,
                ChatGenerationSettings settings
        ) {
            ChatInvocation invocation = invocations.computeIfAbsent(
                    settings, key -> modelFactory.createInvocation(apiKey, modelIdentity, key));
            return invocation.invoke(new com.setaccio.lab.chat.ChatInvocationRequest(
                    modelIdentity, prompt.invocationPrompt(), settings));
        }
    }

    record Arguments(String maxTokens, String timeout, String maximumCostUsd, String outputDirectory, String ollamaRunDirectory) {
        static Arguments parse(String[] args) {
            if (args == null || args.length != 10) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of("--max-tokens", "--timeout", "--max-cost-usd", "--output-dir", "--ollama-run-dir");
            if (supported.stream().anyMatch(option -> values.stream().filter(option::equals).count() != 1)
                    || values.stream().filter(value -> value.startsWith("--")).anyMatch(value -> !supported.contains(value))) {
                throw usage();
            }
            return new Arguments(value(values, "--max-tokens"), value(values, "--timeout"),
                    value(values, "--max-cost-usd"), value(values, "--output-dir"), value(values, "--ollama-run-dir"));
        }

        private static String value(List<String> values, String option) {
            int index = values.indexOf(option);
            if (index < 0 || index == values.size() - 1) {
                throw usage();
            }
            return values.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException("Expected --max-tokens 128 --timeout PT2M --max-cost-usd <0..3.00> "
                    + "--output-dir <new-dated-anthropic-build-directory> --ollama-run-dir <verified-saved-ollama-run>");
        }
    }
}
