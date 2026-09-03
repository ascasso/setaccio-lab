package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceProvenance;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Explicit opt-in entry point for the owner-approved Phase 3 cohort. */
public final class ToolCompatibilityCohortRunner {

    private ToolCompatibilityCohortRunner() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        ToolCompatibilityPreflight.parseMaxTokens(parsed.maxTokens());
        ToolCompatibilityPreflight.parseTimeout(parsed.timeout());

        Path projectDirectory = Path.of("").toAbsolutePath().normalize();
        ToolCompatibilityCohortOllamaInventorySource inventorySource =
                ToolCompatibilityCohortOllamaInventorySource.live(parsed.ollamaBaseUrl());
        ToolCompatibilityCohortPreflight.Prepared prepared =
                new ToolCompatibilityCohortPreflight().prepareApproved(
                        projectDirectory,
                        parsed.ollamaBaseUrl(),
                        parsed.outputDirectory(),
                        inventorySource);
        ToolCompatibilityCohortExecutionPlan plan =
                ToolCompatibilityCohortExecutionPlan.createApproved(prepared);
        EvidenceCodeBaseline codeBaseline = requireCleanBaseline(
                EvidenceProvenance.captureCodeBaseline(projectDirectory));
        ToolCompatibilityCohortLiveSession session = ToolCompatibilityCohortLiveSession.create(
                parsed.ollamaBaseUrl(), inventorySource);
        plan.requireRuntimeUnchanged(session.ollamaRuntimeVersion());
        for (ToolCompatibilityCohortModelIdentity model : prepared.orderedModels()) {
            ToolCompatibilityCohortOllamaInventorySource.requireIdentityStillInstalled(
                    inventorySource.client(), model);
        }

        printProtocol(plan, codeBaseline);
        Path outputDirectory = ToolCompatibilityPreflight.allocate(prepared.outputDirectory());
        try {
            ToolCompatibilityCohortResult result =
                    new ToolCompatibilityCohortExecutor().execute(plan, session);
            requireSameCleanBaseline(
                    codeBaseline,
                    EvidenceProvenance.captureCodeBaseline(projectDirectory));
            ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
            new ToolCompatibilityCohortEvidence(objectMapper).write(
                    outputDirectory, result, codeBaseline);
            System.out.println("Tool compatibility cohort complete: "
                    + outputDirectory.resolve(ToolCompatibilityCohortEvidence.SUMMARY_FILENAME));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Tool compatibility cohort failed after output allocation; retained "
                            + "incomplete diagnostic directory: " + outputDirectory,
                    exception);
        }
    }

    static EvidenceCodeBaseline requireCleanBaseline(EvidenceCodeBaseline baseline) {
        if (baseline == null
                || baseline.workingTreeDirty()
                || baseline.gitCommit() == null
                || !baseline.gitCommit().matches("[0-9a-f]{40}")) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort execution requires one clean Git commit before allocation");
        }
        return baseline;
    }

    static void requireSameCleanBaseline(
            EvidenceCodeBaseline expected,
            EvidenceCodeBaseline observed
    ) {
        EvidenceCodeBaseline initial = requireCleanBaseline(expected);
        EvidenceCodeBaseline current = requireCleanBaseline(observed);
        if (!initial.gitCommit().equals(current.gitCommit())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Git commit drifted during cohort execution");
        }
    }

    private static void printProtocol(
            ToolCompatibilityCohortExecutionPlan plan,
            EvidenceCodeBaseline codeBaseline
    ) {
        System.out.println("Starting opt-in Ollama tool compatibility cohort");
        System.out.println("  Models: 5 ordered peers + 1 separately labelled reference");
        for (ToolCompatibilityCohortModelIdentity model : plan.preflight().orderedModels()) {
            System.out.println("  " + model.cohortPosition() + ". "
                    + model.role().name().toLowerCase() + " " + model.effectiveInstalledTag()
                    + " / " + model.digest());
        }
        System.out.println("  Ollama runtime: " + plan.preflight().ollamaRuntimeVersion());
        System.out.println("  Rows: " + plan.schedule().entries().size()
                + " sequential model-major (6 models x 8 cases x 2 repetitions)");
        System.out.println("  Cohort schedule: " + plan.schedule().id() + " v"
                + plan.schedule().version() + " / " + plan.schedule().sha256());
        System.out.println("  Prompt policy: "
                + plan.promptPolicy().promptCondition().name().toLowerCase());
        System.out.println("  Prompt-policy limitation: " + plan.promptPolicy().limitation());
        System.out.println("  Temperature/seeds: 0.0 / [42, 43] explicit for all locked models");
        System.out.println("  Maximum output tokens per provider turn: "
                + ToolCompatibilityProtocol.MAX_OUTPUT_TOKENS_PER_PROVIDER_TURN);
        System.out.println("  Logical row deadline: " + ToolCompatibilityProtocol.ROW_TIMEOUT);
        System.out.println("  Attempts: exactly 1 logical attempt per row");
        System.out.println("  Thinking request: no per-model override");
        System.out.println("  Ollama pull strategy: never");
        System.out.println("  Git baseline: " + codeBaseline.gitCommit());
        System.out.println("  Output: " + plan.preflight().outputDirectory());
    }

    record Arguments(
            String ollamaBaseUrl,
            String maxTokens,
            String timeout,
            String outputDirectory
    ) {

        static Arguments parse(String[] args) {
            if (args == null || args.length != 8) {
                throw usage();
            }
            List<String> values = new ArrayList<>(List.of(args));
            List<String> supported = List.of(
                    "--ollama-base-url",
                    "--max-tokens",
                    "--timeout",
                    "--output-dir");
            for (int index = 0; index < values.size(); index += 2) {
                if (!supported.contains(values.get(index))) {
                    throw usage();
                }
            }
            if (supported.stream().anyMatch(option ->
                    values.stream().filter(option::equals).count() != 1)) {
                throw usage();
            }
            return new Arguments(
                    value(values, "--ollama-base-url"),
                    value(values, "--max-tokens"),
                    value(values, "--timeout"),
                    value(values, "--output-dir"));
        }

        private static String value(List<String> args, String option) {
            int index = args.indexOf(option);
            if (index < 0 || index == args.size() - 1 || args.get(index + 1).isBlank()) {
                throw usage();
            }
            return args.get(index + 1);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Expected --ollama-base-url <explicit-loopback-url> --max-tokens 512 "
                            + "--timeout PT2M --output-dir <new-dated-evidence-directory>; "
                            + "cohort tags and digests are suite-owned");
        }
    }
}
