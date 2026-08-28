package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;

/** Deterministically renders the saved retrieval-only metrics. */
final class RetrievalEvaluationReport {

    String render(
            RetrievalEvaluationResult result,
            RetrievalEvaluationAnalyzer.Analysis analysis,
            String rawFile,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval-Only Evaluation\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Git commit: `").append(codeBaseline.gitCommit()).append("`\n");
        out.append("- Evidence status: `")
                .append(codeBaseline.workingTreeDirty()
                        ? "diagnostic/non-final (dirty working tree)"
                        : "clean-baseline candidate")
                .append("`\n");
        out.append("- Protocol version: `").append(result.protocolVersion()).append("`\n");
        out.append("- Execution: 14 confirmed fixtures in catalog order; each row is immediately repeated ")
                .append("for deterministic stability.\n");
        out.append("- Corpus: `").append(result.corpusCatalogId()).append("` version `")
                .append(result.corpusCatalogVersion()).append("` (`")
                .append(result.corpusCatalogSha256()).append("`)\n");
        out.append("- Query catalog: `").append(result.queryCatalogId()).append("` version `")
                .append(result.queryCatalogVersion()).append("` (`")
                .append(result.queryCatalogSha256()).append("`)\n");
        out.append("- Retrieval method: `").append(result.lexicalParameters().methodId())
                .append("` version `").append(result.lexicalParameters().methodVersion()).append("`\n");
        out.append("- Tokenizer: `").append(result.lexicalParameters().tokenizerId())
                .append("`; tie-break: `").append(result.lexicalParameters().tieBreak()).append("`\n");

        out.append("\n## Retrieval-only metrics\n\n");
        out.append("| Measure | Result |\n");
        out.append("| --- | ---: |\n");
        metric(out, "Expected supporting document retrieved", analysis.expectedSupportingDocumentsRetrieved(), analysis.matchingFixtures());
        metric(out, "Expected supporting document in top 1", analysis.expectedSupportingDocumentsInTop1(), analysis.matchingFixtures());
        metric(out, "Expected supporting document in top 3", analysis.expectedSupportingDocumentsInTop3(), analysis.matchingFixtures());
        metric(out, "Fixtures retrieving a forbidden document", analysis.forbiddenDocumentRetrievedFixtures(), analysis.matchingFixtures());
        metric(out, "Correct no-match", analysis.correctNoMatchFixtures(), analysis.noMatchFixtures());
        metric(out, "Rows stable across immediate repeat", analysis.stableRows(), analysis.totalFixtures());

        out.append("\n## Evidence retained per row\n\n");
        out.append("Every raw row retains the confirmed fixture identity and labels, exact query, corpus ")
                .append("identity, locked lexical parameters, the complete returned document text, ordered document ")
                .append("IDs, one-based ranks, content SHA-256 values, exact score numerators/denominators, matched ")
                .append("terms, and the immediate-repeat stability result.\n");

        out.append("\n## Interpretation boundary\n\n");
        out.append("These are deterministic retrieval observations against human-confirmed fixture labels for ")
                .append("the exact public corpus and lexical method. They do not assess generated answers, ")
                .append("semantic relevance beyond those labels, embedding retrieval, model behavior, or an AI evaluator.\n");

        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }

    private static void metric(StringBuilder out, String label, int numerator, int denominator) {
        out.append("| ").append(label).append(" | ")
                .append(numerator).append('/').append(denominator).append(" |\n");
    }
}
