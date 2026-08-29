package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import java.util.EnumMap;
import java.util.Map;

/** Deterministic R5 evidence summary with no merged retrieval-and-answer score. */
final class RetrievalAnswerReport {

    String render(
            RetrievalAnswerResult result,
            RetrievalAnswerAnalyzer.Analysis analysis,
            String rawFile,
            String rawSha256,
            EvidenceCodeBaseline codeBaseline
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval Answer Generation\n\n");
        out.append("- Raw result: `").append(rawFile).append("`\n");
        out.append("- Raw SHA-256: `").append(rawSha256).append("`\n");
        out.append("- Git commit: `").append(codeBaseline.gitCommit()).append("`\n");
        out.append("- Evidence status: `").append(codeBaseline.workingTreeDirty()
                ? "diagnostic/non-final (dirty working tree)" : "clean-baseline candidate").append("`\n");
        out.append("- Verified R3 source run: `").append(result.sourceEvidence().sourceRunId()).append("`\n");
        out.append("- Verified R3 raw SHA-256: `").append(result.sourceEvidence().sourceRawSha256()).append("`\n");
        out.append("- Prompt: `").append(result.prompt().promptId()).append("` (`")
                .append(result.prompt().promptSha256()).append("`)\n");
        out.append("- Answer model: requested `").append(result.modelIdentity().requestedModel())
                .append("`, effective `").append(result.modelIdentity().effectiveModel()).append("`, digest `")
                .append(result.modelIdentity().digest()).append("`\n");
        out.append("- Settings: temperature `").append(result.runSettings().temperature()).append("`, seed `")
                .append(result.runSettings().seed()).append("`, max output tokens `")
                .append(result.runSettings().maxOutputTokens()).append("`, timeout `PT")
                .append(result.runSettings().requestTimeoutMillis() / 1000).append("S`, attempts `1`, pull `never`\n");
        out.append("- Execution: ").append(result.rows().size()).append(" sequential answer rows; retrieval is not re-run.\n");

        Map<ChatInvocationFailureCategory, Integer> failures = new EnumMap<>(ChatInvocationFailureCategory.class);
        Map<RetrievalAnswerReferenceBehavior, Integer> references = new EnumMap<>(RetrievalAnswerReferenceBehavior.class);
        int abstentions = 0;
        for (RetrievalAnswerRow row : result.rows()) {
            failures.merge(row.invocation().failureCategory(), 1, Integer::sum);
            references.merge(row.referenceAnalysis().behavior(), 1, Integer::sum);
            if (row.explicitAbstentionObserved()) {
                abstentions++;
            }
        }
        out.append("\n## Separate answer observations\n\n");
        out.append("| Observation | Rows |\n| --- | ---: |\n");
        for (ChatInvocationFailureCategory category : ChatInvocationFailureCategory.values()) {
            out.append("| Invocation `").append(category).append("` | ")
                    .append(failures.getOrDefault(category, 0)).append(" |\n");
        }
        for (RetrievalAnswerReferenceBehavior behavior : RetrievalAnswerReferenceBehavior.values()) {
            out.append("| Reference `").append(behavior).append("` | ")
                    .append(references.getOrDefault(behavior, 0)).append(" |\n");
        }
        out.append("| Explicit `NO_SUPPORT` abstention | ").append(abstentions).append(" |\n");

        out.append("\n## Evidence retained per row\n\n");
        out.append("Every raw row retains its exact verified R3 retrieval row, including returned document text, "
                + "IDs, ranks, content SHA-256 values, lexical scores, and fixture labels; the exact rendered "
                + "prompt; answer model and prompt identities; raw answer text; safe provider response identifier; "
                + "available usage; latency; one-attempt invocation/failure outcome; bracketed document-reference "
                + "observation; and explicit abstention observation. Unsupported assertions remain `NOT_ASSESSED`.\n");

        out.append("\n## Interpretation boundary\n\n");
        out.append("R5 preserves retrieved evidence and records answer-generation behavior. It does not rerun or score "
                + "retrieval, assess answer correctness, decide whether an assertion is supported, make a semantic "
                + "relevance claim, rank a model, or invoke an AI evaluator. A bracketed reference is a syntax and "
                + "identity observation, not proof that the cited text supports the answer.\n");
        if (!analysis.integrityFailures().isEmpty()) {
            out.append("\n## Integrity failures\n\n");
            analysis.integrityFailures().forEach(failure -> out.append("- ").append(failure).append('\n'));
        }
        return out.toString();
    }
}
