package com.setaccio.lab.thinking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeModelFactory;
import com.setaccio.lab.evaluation.LocalFactCheckPromptDefinition;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.ollama.api.OllamaApi;

/**
 * Runs the one authorized reasoning diagnostic suite against a loopback Ollama endpoint.
 *
 * <p>Opt-in only. It is never attached to the default lifecycle, never pulls a model, and never
 * substitutes one. Every arm, budget, seed, and reasoning policy is locked in
 * {@link ThinkingDiagnosticProtocol} before any evidence directory is allocated.
 */
public final class ThinkingDiagnosticRunner {

    private ThinkingDiagnosticRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                EvidenceProvenance.captureCodeBaseline(Path.of("")));

        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        LocalFactCheckFixtureCatalog catalog = new LocalFactCheckFixtureCatalog(objectMapper);
        LocalFactCheckPromptDefinition promptDefinition = new LocalFactCheckPromptDefinition();

        LocalFactCheckJudgeModelFactory modelFactory = new LocalFactCheckJudgeModelFactory();
        OllamaApi ollamaApi = modelFactory.createApi(
                parsed.ollamaBaseUrl(), ThinkingDiagnosticProtocol.REQUEST_TIMEOUT);

        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities =
                resolveIdentities(ollamaApi, parsed);
        String ollamaVersion = requireVersion(parsed.ollamaVersion());

        Path outputDirectory = resolveNewOutputDirectory(parsed.outputDirectory());
        printProtocol(identities, ollamaVersion, codeBaseline, outputDirectory);
        EvidenceRunDirectory.createNamed(
                outputDirectory.getParent(), outputDirectory.getFileName().toString());

        try {
            ThinkingDiagnosticResult result = new ThinkingDiagnosticExecutor(
                    settings -> modelFactory.create(parsed.ollamaBaseUrl(), settings),
                    promptDefinition)
                    .execute(catalog, identities, ollamaVersion);
            requireUnchangedIdentities(identities, resolveIdentities(ollamaApi, parsed));
            requireSameCleanBaseline(codeBaseline, EvidenceProvenance.captureCodeBaseline(Path.of("")));
            new ThinkingDiagnosticEvidence(objectMapper, catalog)
                    .write(outputDirectory, result, codeBaseline);
            System.out.println("Thinking diagnostic complete: "
                    + outputDirectory.resolve(ThinkingDiagnosticEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Thinking diagnostic failed after output reservation; retained diagnostic directory: "
                            + outputDirectory,
                    exception);
        }
    }

    static Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> resolveIdentities(
            OllamaApi ollamaApi,
            Arguments parsed
    ) {
        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities =
                new EnumMap<>(ThinkingDiagnosticModelRole.class);
        identities.put(ThinkingDiagnosticModelRole.SUBJECT, resolve(
                ollamaApi, ThinkingDiagnosticModelRole.SUBJECT, parsed.subjectModel()));
        identities.put(ThinkingDiagnosticModelRole.CONTROL, resolve(
                ollamaApi, ThinkingDiagnosticModelRole.CONTROL, parsed.controlModel()));
        return identities;
    }

    private static ThinkingDiagnosticModelIdentity resolve(
            OllamaApi ollamaApi,
            ThinkingDiagnosticModelRole role,
            String requestedModel
    ) {
        String normalized = ThinkingDiagnosticProtocol.normalizeModelTag(requestedModel);
        return ThinkingDiagnosticModelInventory.requireInstalled(
                ollamaApi.listModels(),
                ollamaApi.showModel(new OllamaApi.ShowModelRequest(normalized)),
                role,
                requestedModel);
    }

    static void requireUnchangedIdentities(
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> expected,
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> observed
    ) {
        if (!expected.equals(observed)) {
            throw new IllegalStateException(
                    "Ollama model identity changed during the diagnostic; refusing to write evidence.");
        }
    }

    static Path resolveNewOutputDirectory(String value) {
        Path outputDirectory = ThinkingDiagnosticProtocol.EVIDENCE_ROOT.resolveNewRunDirectory(
                Path.of(""), value, "Output directory");
        if (!outputDirectory.getFileName().toString().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
            throw new IllegalArgumentException("Output directory must contain a YYYY-MM-DD date.");
        }
        return outputDirectory;
    }

    private static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline codeBaseline) {
        if (codeBaseline == null
                || codeBaseline.workingTreeDirty()
                || codeBaseline.gitCommit() == null
                || !codeBaseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new IllegalStateException(
                    "The thinking diagnostic requires a clean worktree at a full Git commit.");
        }
        return codeBaseline;
    }

    static void requireSameCleanBaseline(EvidenceCodeBaseline expected, EvidenceCodeBaseline observed) {
        EvidenceCodeBaseline initial = requireCleanBaseline(expected);
        EvidenceCodeBaseline current = requireCleanBaseline(observed);
        if (!initial.gitCommit().equals(current.gitCommit())) {
            throw new IllegalStateException("Git baseline changed during the thinking diagnostic.");
        }
    }

    private static String requireVersion(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || !value.matches("[0-9][0-9A-Za-z._+-]*")) {
            throw new IllegalArgumentException("--ollama-version must be the observed runtime version");
        }
        return value;
    }

    private static void printProtocol(
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities,
            String ollamaVersion,
            EvidenceCodeBaseline codeBaseline,
            Path outputDirectory
    ) {
        System.out.println("Starting the opt-in reasoning and empty-content diagnostic");
        System.out.println("  Commit: " + codeBaseline.gitCommit());
        System.out.println("  Ollama version: " + ollamaVersion);
        identities.forEach((role, identity) -> System.out.println(
                "  " + role + ": " + identity.normalizedInstalledName()
                        + " digest " + identity.digest()
                        + " advertisesThinking=" + identity.advertisesThinking()));
        System.out.println("  Arms: " + ThinkingDiagnosticProtocol.ARMS.size()
                + "; rows: " + ThinkingDiagnosticProtocol.ROW_COUNT);
        System.out.println("  Output: " + outputDirectory);
    }

    record Arguments(
            String ollamaBaseUrl,
            String subjectModel,
            String controlModel,
            String ollamaVersion,
            String outputDirectory
    ) {
        static Arguments parse(String[] args) {
            if (args == null || args.length != 10) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            return new Arguments(
                    value(values, "--ollama-base-url"),
                    value(values, "--subject-model"),
                    value(values, "--control-model"),
                    value(values, "--ollama-version"),
                    value(values, "--output-dir"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1) {
                throw usage();
            }
            String value = args.get(index + 1);
            if (value.isBlank() || !value.equals(value.strip())) {
                throw usage();
            }
            return value;
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --ollama-base-url <loopback-url> --subject-model <installed-tag> "
                            + "--control-model <installed-tag> --ollama-version <observed-version> "
                            + "--output-dir <new-dated-directory-under-"
                            + ThinkingDiagnosticProtocol.EVIDENCE_ROOT.durableRelativePath() + ">");
        }
    }
}
