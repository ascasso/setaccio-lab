package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.util.EnumMap;
import java.util.Map;

/** Deterministic R6 evidence summary without a relevance or answer-quality score. */
final class RetrievalRelevancyReport {

    String render(
            RetrievalRelevancyResult result,
            RetrievalRelevancyAnalyzer.Analysis analysis,
            String rawFile,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval Relevancy Evaluation\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Git commit: `").append(codeBaseline.gitCommit()).append("`\n");
        out.append("- Evidence status: `").append(codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final (dirty working tree)" : "clean-baseline candidate").append("`\n");
        out.append("- Verified R5 source run: `").append(result.sourceEvidence().sourceRunId()).append("`\n");
        out.append("- Verified R5 raw SHA-256: `").append(result.sourceEvidence().sourceRawSha256()).append("`\n");
        out.append("- Evaluator prompt: `").append(result.prompt().promptId()).append("` (`")
                .append(result.prompt().promptSha256()).append("`)\n");
        out.append("- Evaluator model: requested `").append(result.modelIdentity().requestedModel())
                .append("`, effective `").append(result.modelIdentity().effectiveModel()).append("`, digest `")
                .append(result.modelIdentity().digest()).append("`\n");
        out.append("- Settings: temperature `").append(result.runSettings().temperature()).append("`, seed `")
                .append(result.runSettings().seed()).append("`, max output tokens `")
                .append(result.runSettings().maxOutputTokens()).append("`, timeout `PT")
                .append(result.runSettings().requestTimeoutMillis() / 1000).append("S`, attempts `1`, pull `never`\n");
        out.append("- Execution: ").append(result.rows().size())
                .append(" preserved R5 rows in order; retrieval and answer generation are not re-run.\n");

        Map<RetrievalRelevancyDiagnosticCategory, Integer> diagnostics =
                new EnumMap<>(RetrievalRelevancyDiagnosticCategory.class);
        Map<RetrievalRelevancyModelRelationship, Integer> relationships =
                new EnumMap<>(RetrievalRelevancyModelRelationship.class);
        for (RetrievalRelevancyRow row : result.rows()) {
            diagnostics.merge(row.evaluatorOutcome().diagnosticCategory(), 1, Integer::sum);
            relationships.merge(row.modelRelationship(), 1, Integer::sum);
        }
        out.append("\n## Recorded evaluator observations\n\n");
        out.append("| Observation | Rows |\n| --- | ---: |\n");
        for (RetrievalRelevancyDiagnosticCategory category : RetrievalRelevancyDiagnosticCategory.values()) {
            out.append("| Evaluator `").append(category).append("` | ")
                    .append(diagnostics.getOrDefault(category, 0)).append(" |\n");
        }
        for (RetrievalRelevancyModelRelationship relationship : RetrievalRelevancyModelRelationship.values()) {
            out.append("| Model relationship `").append(relationship).append("` | ")
                    .append(relationships.getOrDefault(relationship, 0)).append(" |\n");
        }

        out.append("\n## Evidence retained per row\n\n");
        out.append("Every raw row retains its exact R5 answer row and R3-derived retrieved document text, IDs, "
                + "ranks, content SHA-256 values, lexical observations, and deterministic fixture expectation. "
                + "An attempted row also retains evaluator prompt/model identity, Spring evaluator pass/score, normalized "
                + "verdict, raw evaluator text, safe response metadata, available usage, latency, and one-attempt diagnostic. "
                + "Rows without context or an answer are explicitly not attempted. Human support judgment remains `NOT_REVIEWED` "
                + "and answer correctness remains `NOT_ASSESSED`.\n");

        out.append("\n## Interpretation boundary\n\n");
        out.append("R6 records a Spring AI `RelevancyEvaluator` observation using only the preserved retrieved context. "
                + "It does not make the evaluator ground truth, convert a verdict into a fixture-expectation match, make a "
                + "human support or answer-correctness claim, rank or select a model, or merge retrieval, answer, and evaluator "
                + "outcomes into a score. Self-evaluation is retained as a flag, not normalized away.\n");
        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }
}
