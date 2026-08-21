package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceFiles;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import com.setaccio.lab.evidence.EvidenceRunDirectory;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepares one ignored, non-overwriting worksheet for an actual human review of a verified paired
 * prompt-matrix run.
 *
 * <p>It preserves deterministic observations and raw local responses for the owner to inspect,
 * but never scores semantics or selects a human decision.</p>
 */
final class ToolCompatibilityHumanReviewPreparer {

    static final String WORKSHEET_FILENAME = "HUMAN-REVIEW.md";

    private final ToolCompatibilityPromptMatrixComparison comparison;

    ToolCompatibilityHumanReviewPreparer(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        comparison = new ToolCompatibilityPromptMatrixComparison(objectMapper);
    }

    PreparationResult prepare(
            Path baselineDirectory,
            Path candidateDirectory,
            Path outputRoot,
            LocalDate reviewDate,
            String reviewId
    ) {
        if (reviewDate == null) {
            throw new IllegalArgumentException("reviewDate must not be null");
        }
        Path safeOutputRoot = requireOutputRoot(outputRoot);
        ToolCompatibilityPromptMatrixComparison.ComparisonResult paired = comparison.compare(
                baselineDirectory, candidateDirectory);
        String worksheet = render(paired, reviewDate);
        Path outputDirectory = EvidenceRunDirectory.createNamed(safeOutputRoot, reviewId);
        Path worksheetPath = outputDirectory.resolve(WORKSHEET_FILENAME);
        EvidenceFiles.writeNewText(
                worksheetPath,
                worksheet,
                "Failed to write private tool compatibility human-review worksheet");
        return new PreparationResult(
                worksheetPath,
                EvidenceIntegrity.sha256(paired.report().getBytes(StandardCharsets.UTF_8)));
    }

    private static Path requireOutputRoot(Path outputRoot) {
        if (outputRoot == null) {
            throw new IllegalArgumentException("outputRoot must not be null");
        }
        Path normalized = outputRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("private human-review output root is unsafe");
        }
        return normalized;
    }

    private static String render(
            ToolCompatibilityPromptMatrixComparison.ComparisonResult comparison,
            LocalDate reviewDate
    ) {
        ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline = comparison.baselineCondition();
        ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate = comparison.candidateCondition();
        ToolCompatibilityPromptMatrixResult baselineResult = baseline.result();
        ToolCompatibilityPromptMatrixResult candidateResult = candidate.result();
        ToolCompatibilityPairedSchedule schedule = baselineResult.pairedExecutionSchedule();
        String reportSha256 = EvidenceIntegrity.sha256(comparison.report().getBytes(StandardCharsets.UTF_8));
        Map<RowKey, ToolCompatibilityRow> baselineRows = rowsByCaseAndRepetition(baselineResult.rows());
        Map<RowKey, ToolCompatibilityRow> candidateRows = rowsByCaseAndRepetition(candidateResult.rows());

        StringBuilder out = new StringBuilder("# Private Tool Compatibility Human-Review Worksheet\n\n");
        out.append("> Private ignored artifact: it includes raw local provider text. Do not commit, publish, "
                + "or treat it as an automated judgment.\n\n");
        out.append("## Evidence binding\n\n");
        out.append("- Baseline run: `").append(inline(baseline.manifest().runId())).append("`\n");
        out.append("- Candidate run: `").append(inline(candidate.manifest().runId())).append("`\n");
        out.append("- Shared Git commit: `")
                .append(inline(baseline.manifest().codeBaseline().gitCommit()))
                .append("`\n");
        out.append("- Prompt catalog: `").append(inline(schedule.promptCatalogId())).append("` version `")
                .append(schedule.promptCatalogVersion()).append("` (`")
                .append(inline(schedule.promptCatalogSha256())).append("`)\n");
        out.append("- Comparison report SHA-256: `").append(reportSha256).append("`\n");
        out.append("- Review date: `").append(reviewDate).append("`\n");
        out.append("- Protocol: ").append(baselineResult.orderedCaseIds().size())
                .append(" case(s) × ").append(baselineResult.runSettings().repetitions())
                .append(" repetition(s) = ").append(baselineResult.rows().size())
                .append(" paired row(s).\n\n");
        out.append("Both saved conditions passed strict offline verification and the paired comparison gate "
                + "before this worksheet was allocated. The decision fields remain blank for the owner; "
                + "no LLM may complete them.\n\n");

        for (String caseId : baselineResult.orderedCaseIds()) {
            for (int repetition = 1; repetition <= baselineResult.runSettings().repetitions(); repetition++) {
                RowKey key = new RowKey(caseId, repetition);
                ToolCompatibilityRow baselineRow = requireRow(baselineRows, key, "baseline");
                ToolCompatibilityRow candidateRow = requireRow(candidateRows, key, "candidate");
                renderPair(out, caseId, repetition, baselineRow, candidateRow);
            }
        }

        renderDecision(out, baseline, candidate, schedule, reportSha256, reviewDate);
        return out.toString();
    }

    private static Map<RowKey, ToolCompatibilityRow> rowsByCaseAndRepetition(
            List<ToolCompatibilityRow> rows
    ) {
        Map<RowKey, ToolCompatibilityRow> byKey = new LinkedHashMap<>();
        for (ToolCompatibilityRow row : rows) {
            RowKey key = new RowKey(row.caseId(), row.repetition());
            if (byKey.putIfAbsent(key, row) != null) {
                throw new IllegalArgumentException("verified human-review rows must not contain duplicate pairs");
            }
        }
        return Map.copyOf(byKey);
    }

    private static ToolCompatibilityRow requireRow(
            Map<RowKey, ToolCompatibilityRow> rows,
            RowKey key,
            String condition
    ) {
        ToolCompatibilityRow row = rows.get(key);
        if (row == null) {
            throw new IllegalArgumentException(
                    "verified " + condition + " evidence is missing case/repetition pair " + key);
        }
        return row;
    }

    private static void renderPair(
            StringBuilder out,
            String caseId,
            int repetition,
            ToolCompatibilityRow baseline,
            ToolCompatibilityRow candidate
    ) {
        ToolCompatibilityCaseOracle.CaseExpectation oracle = ToolCompatibilityProtocol.caseOracle()
                .requireCase(caseId);
        ToolBenchmarkPrompt canonicalCase = canonicalCase(caseId);
        out.append("## `").append(inline(caseId)).append("` / repetition `")
                .append(repetition).append("`\n\n");
        out.append("- Required tools: ").append(toolList(
                oracle.calls().stream().map(ToolCompatibilityExpectedCall::toolName).distinct().toList()))
                .append("\n");
        out.append("- Forbidden tools: ").append(toolList(
                canonicalCase.expectation().forbiddenExecutedTools())).append("\n\n");

        renderObservedRow(out, "Baseline", baseline);
        renderObservedRow(out, "Candidate", candidate);

        out.append("### Owner pair notes\n\n");
        out.append("- Did the prompt improve behavior in this case without harming tool selection? \n");
        out.append("- Did it change abstention or introduce prompt-specific artifacts? \n");
        out.append("- Are final responses mechanically terse or incomplete? \n");
        out.append("- Human notes: \n\n");
    }

    private static ToolBenchmarkPrompt canonicalCase(String caseId) {
        return ToolCompatibilityProtocol.caseSelection().cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown canonical tool compatibility case: " + caseId));
    }

    private static String toolList(List<String> toolNames) {
        return toolNames.isEmpty()
                ? "none"
                : toolNames.stream()
                        .map(name -> "`" + inline(name) + "`")
                        .collect(java.util.stream.Collectors.joining(", "));
    }

    private static void renderObservedRow(StringBuilder out, String label, ToolCompatibilityRow row) {
        out.append("### ").append(label).append(" recorded evidence\n\n");
        out.append("- Contract: `").append(row.caseContractPassed() ? "pass" : "fail").append("`\n");
        out.append("- Exact call sequence: `")
                .append(row.exactCallSequenceMatched() ? "matched" : "mismatched").append("`\n");
        out.append("- All expected semantic arguments: `")
                .append(row.allExpectedArgumentsMatched() ? "matched" : "mismatched").append("`\n");
        out.append("- Final response: `")
                .append(row.finalResponsePresent() ? "present" : "empty").append("`\n");
        out.append("- Visible reasoning marker: `")
                .append(row.reasoningMarkerDetected() ? "present" : "absent").append("`\n");
        out.append("- Paired execution: global `").append(row.globalPairSequence()).append("`, `")
                .append(row.conditionExecutionPosition().wireValue()).append("`\n");
        out.append("- Provider turns: `").append(row.providerTurns().size()).append("`\n");
        out.append("- Any provider turn reached the output limit: `")
                .append(row.anyProviderTurnReachedOutputLimit()).append("`\n");
        out.append("- Aggregate completion tokens: `")
                .append(tokenValue(row.aggregateUsage().completionTokens())).append("`\n");
        out.append("- Row latency: `").append(row.rowLatency().toMillis()).append(" ms`\n");
        if (row.failureCategory() != null) {
            out.append("- Terminal failure category: `").append(inline(row.failureCategory())).append("`\n");
        }
        if (row.diagnosticCategory() != null) {
            out.append("- Diagnostic category: `").append(inline(row.diagnosticCategory())).append("`\n");
        }
        out.append('\n');

        out.append("#### Tool-call evidence\n\n");
        if (row.toolCalls().isEmpty()) {
            out.append("No tool calls were observed.\n\n");
        } else {
            for (ToolCompatibilityToolCallEvidence call : row.toolCalls()) {
                out.append("- Call `#").append(call.sequence()).append("`: `")
                        .append(inline(call.toolName())).append("` on provider turn `")
                        .append(call.providerTurnSequence()).append("`\n");
                out.append("  - Expected call state: `")
                        .append(evidenceState(call.expectedCallAtSequenceState())).append("`\n");
                out.append("  - Expected argument state: `")
                        .append(evidenceState(call.expectedArgumentsState())).append("`\n");
                out.append("  - Raw arguments:\n\n")
                        .append(fenced(call.rawArgumentJson(), "json")).append("\n\n");
            }
        }

        out.append("#### Provider-turn evidence\n\n");
        if (row.providerTurns().isEmpty()) {
            out.append("No provider turns were observed.\n\n");
            return;
        }
        for (ToolCompatibilityProviderTurnEvidence turn : row.providerTurns()) {
            out.append("##### Provider turn `").append(turn.sequence()).append("`\n\n");
            out.append("- Invocation state: `").append(evidenceState(turn.invocationState())).append("`\n");
            out.append("- Output-limit state: `")
                    .append(outputLimitState(turn.outputLimitState())).append("`\n");
            out.append("- Completion tokens: `").append(tokenValue(turn.usage().completionTokens()))
                    .append("`\n");
            out.append("- Latency: `").append(turn.latency().toMillis()).append(" ms`\n");
            out.append("- Tool-call IDs: `")
                    .append(turn.orderedToolCallIds().isEmpty()
                            ? "none"
                            : inline(String.join(", ", turn.orderedToolCallIds())))
                    .append("`\n\n");
            out.append(fenced(turn.assistantText(), "text")).append("\n\n");
        }
    }

    private static void renderDecision(
            StringBuilder out,
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition baseline,
            ToolCompatibilityPromptMatrixEvidence.VerifiedCondition candidate,
            ToolCompatibilityPairedSchedule schedule,
            String reportSha256,
            LocalDate reviewDate
    ) {
        out.append("## Final human decision\n\n");
        out.append("The owner must complete this section manually. Select exactly one decision; an agent or "
                + "LLM must not select it.\n\n");
        out.append("- [ ] `adopt`\n");
        out.append("- [ ] `revise`\n");
        out.append("- [ ] `reject`\n");
        out.append("- [ ] `inconclusive`\n");
        out.append("- Reviewer: \n");
        out.append("- Human rationale: \n");
        out.append("- Follow-up boundary: \n\n");
        out.append("### Decision binding\n\n");
        out.append("- Baseline run: `").append(inline(baseline.manifest().runId())).append("`\n");
        out.append("- Candidate run: `").append(inline(candidate.manifest().runId())).append("`\n");
        out.append("- Prompt catalog digest: `").append(inline(schedule.promptCatalogSha256())).append("`\n");
        out.append("- Comparison report digest: `").append(reportSha256).append("`\n");
        out.append("- Review date: `").append(reviewDate).append("`\n");
    }

    private static String evidenceState(ToolCompatibilityEvidenceState state) {
        return state.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static String outputLimitState(ToolCompatibilityOutputLimitState state) {
        return state.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static String tokenValue(Integer tokens) {
        return tokens == null ? "n/a" : Integer.toString(tokens);
    }

    private static String fenced(String value, String language) {
        String text = value == null || value.isBlank() ? "(no recorded text)" : value.strip();
        int longest = 0;
        int current = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '`') {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        return fence + language + "\n" + text + "\n" + fence;
    }

    private static String inline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "<br>");
    }

    record PreparationResult(Path worksheet, String comparisonReportSha256) {

        PreparationResult {
            if (worksheet == null || comparisonReportSha256 == null
                    || !comparisonReportSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("private worksheet path and comparison digest are required");
            }
        }
    }

    private record RowKey(String caseId, int repetition) {}
}
