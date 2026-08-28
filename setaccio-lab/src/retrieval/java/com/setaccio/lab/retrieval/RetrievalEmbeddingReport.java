package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceCodeBaseline;

/** Deterministically renders saved R4 embedding-generation provenance without exposing ignored vectors. */
final class RetrievalEmbeddingReport {

    String render(
            RetrievalEmbeddingResult result,
            RetrievalEmbeddingAnalyzer.Analysis analysis,
            String rawFile,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder out = new StringBuilder("# Local Embedding Retrieval\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Git commit: `").append(codeBaseline.gitCommit()).append("`\n");
        out.append("- Evidence status: `")
                .append(codeBaseline.workingTreeDirty()
                        ? "diagnostic/non-final (dirty working tree)"
                        : "clean-baseline candidate")
                .append("`\n");
        out.append("- Protocol version: `").append(result.protocolVersion()).append("`\n");
        out.append("- Provider / endpoint category: `").append(result.runSettings().provider()).append("` / `")
                .append(result.runSettings().endpointCategory()).append("`\n");
        out.append("- Embedding model: requested `").append(result.modelIdentity().requestedModel())
                .append("`, effective `").append(result.modelIdentity().effectiveModel())
                .append("`, Ollama digest `").append(result.modelIdentity().digest()).append("`\n");
        out.append("- Corpus: `").append(result.corpusCatalogId()).append("` version `")
                .append(result.corpusCatalogVersion()).append("` (`")
                .append(result.corpusCatalogSha256()).append("`)\n");
        out.append("- Query catalog: `").append(result.queryCatalogId()).append("` version `")
                .append(result.queryCatalogVersion()).append("` (`")
                .append(result.queryCatalogSha256()).append("`)\n");
        out.append("- Inputs: ").append(result.documentVectors().size()).append(" documents + ")
                .append(result.queryVectors().size()).append(" queries in one explicit batch.\n");
        out.append("- Vector dimension: `").append(result.vectorDimension()).append("`\n");
        out.append("- Chunking: `").append(result.runSettings().chunkingPolicy()).append("`; normalization: `")
                .append(result.runSettings().normalizationPolicy()).append("`\n");
        out.append("- Ranking: `").append(result.runSettings().distanceMetric()).append("`; top K: `")
                .append(result.runSettings().topK()).append("`\n");
        out.append("- Attempt policy: exactly ").append(result.runSettings().maxAttempts())
                .append("; timeout: ").append(result.runSettings().requestTimeoutMillis()).append(" ms\n");
        out.append("- Pull strategy: `").append(result.pullModelStrategy()).append("`\n");

        out.append("\n## Retained evidence\n\n");
        out.append("The ignored raw artifact retains every normalized document and query vector, their exact ")
                .append("corpus/query SHA-256 identities, model identity, provider timing metadata, and the ")
                .append("deterministic top-K document IDs, ranks, content digests, and cosine scores for every ")
                .append("confirmed query.\n");

        out.append("\n## Interpretation boundary\n\n");
        out.append("This records one local embedding-generation and ranking configuration. It does not set a ")
                .append("support threshold, score no-match behavior, generate answers, establish semantic relevance, ")
                .append("or compare models or retrieval methods.\n");

        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }
}
