package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatInvocationPrompt;
import com.setaccio.lab.chat.ChatInvocationRequest;
import com.setaccio.lab.chat.ChatProviderModelIdentity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes the R5 answer stage over preserved R3 rows without re-running retrieval. */
final class RetrievalAnswerExecutor {

    private static final Pattern BRACKETED_TOKEN = Pattern.compile("\\[([^\\[\\]]*)\\]");
    private static final Pattern DOCUMENT_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final ChatInvocation invocation;

    RetrievalAnswerExecutor(ChatInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException("invocation must not be null");
        }
        this.invocation = invocation;
    }

    RetrievalAnswerResult execute(
            RetrievalAnswerSourceEvidence sourceEvidence,
            RetrievalEvaluationResult retrievalEvidence,
            RetrievalAnswerPromptDefinition promptDefinition,
            RetrievalAnswerModelIdentity modelIdentity,
            RetrievalAnswerRunSettings runSettings,
            ChatProviderModelIdentity invocationModelIdentity
    ) {
        if (sourceEvidence == null || retrievalEvidence == null || promptDefinition == null
                || modelIdentity == null || runSettings == null || invocationModelIdentity == null) {
            throw new IllegalArgumentException("answer execution inputs must not be null");
        }
        if (!modelIdentity.providerId().equals(invocationModelIdentity.providerId())
                || !modelIdentity.requestedModel().equals(invocationModelIdentity.requestedModel())
                || !modelIdentity.effectiveModel().equals(invocationModelIdentity.effectiveModel())) {
            throw new IllegalArgumentException("invocation model identity must match the locked answer model identity");
        }
        RetrievalAnswerPromptContract prompt = promptDefinition.contract();
        Instant startedAt = Instant.now();
        List<RetrievalAnswerRow> rows = new ArrayList<>(retrievalEvidence.rows().size());
        for (RetrievalEvaluationRow retrievalRow : retrievalEvidence.rows()) {
            String renderedPrompt = promptDefinition.render(retrievalRow.query(), retrievalRow.retrievedDocuments());
            ChatInvocationOutcome outcome = invocation.invoke(new ChatInvocationRequest(
                    invocationModelIdentity,
                    new ChatInvocationPrompt(prompt.promptId(), renderedPrompt),
                    runSettings.chatSettings()));
            RetrievalAnswerInvocationOutcome recorded = record(outcome, prompt, modelIdentity);
            RetrievalAnswerReferenceAnalysis referenceAnalysis = analyzeReferences(
                    recorded.answerText(), retrievalRow.retrievedDocuments(), recorded.successful());
            rows.add(new RetrievalAnswerRow(
                    retrievalRow.sequence(),
                    retrievalRow,
                    renderedPrompt,
                    recorded,
                    referenceAnalysis,
                    recorded.successful() && "NO_SUPPORT".equals(recorded.answerText().strip()),
                    RetrievalAnswerSupportAssessment.NOT_ASSESSED));
        }
        return new RetrievalAnswerResult(
                RetrievalAnswerProtocol.VERSION,
                RetrievalAnswerProtocol.SUITE,
                startedAt,
                Instant.now(),
                RetrievalAnswerProtocol.EXECUTION_ENGINE,
                RetrievalAnswerProtocol.EXECUTION_STRATEGY,
                sourceEvidence,
                retrievalEvidence,
                prompt,
                modelIdentity,
                runSettings,
                rows);
    }

    static RetrievalAnswerReferenceAnalysis analyzeReferences(
            String answerText,
            List<RetrievalEvaluationRetrievedDocument> retrievedDocuments,
            boolean successful
    ) {
        if (!successful || answerText == null) {
            return new RetrievalAnswerReferenceAnalysis(
                    RetrievalAnswerReferenceBehavior.NOT_OBSERVED, List.of(), List.of(), List.of());
        }
        if ("NO_SUPPORT".equals(answerText.strip())) {
            return new RetrievalAnswerReferenceAnalysis(
                    RetrievalAnswerReferenceBehavior.EXPLICIT_ABSTENTION, List.of(), List.of(), List.of());
        }
        Set<String> retrievedIds = new LinkedHashSet<>();
        if (retrievedDocuments != null) {
            retrievedDocuments.forEach(document -> retrievedIds.add(document.documentId()));
        }
        LinkedHashSet<String> references = new LinkedHashSet<>();
        LinkedHashSet<String> unretrieved = new LinkedHashSet<>();
        LinkedHashSet<String> malformed = new LinkedHashSet<>();
        Matcher matcher = BRACKETED_TOKEN.matcher(answerText);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!DOCUMENT_ID.matcher(token).matches()) {
                malformed.add(token);
            } else {
                references.add(token);
                if (!retrievedIds.contains(token)) {
                    unretrieved.add(token);
                }
            }
        }
        RetrievalAnswerReferenceBehavior behavior = !malformed.isEmpty()
                ? RetrievalAnswerReferenceBehavior.MALFORMED_DOCUMENT_REFERENCE
                : !unretrieved.isEmpty()
                        ? RetrievalAnswerReferenceBehavior.UNRETRIEVED_DOCUMENT_REFERENCE
                        : references.isEmpty()
                                ? RetrievalAnswerReferenceBehavior.NO_DOCUMENT_REFERENCE
                                : RetrievalAnswerReferenceBehavior.RETRIEVED_DOCUMENT_REFERENCES_ONLY;
        return new RetrievalAnswerReferenceAnalysis(
                behavior, List.copyOf(references), List.copyOf(unretrieved), List.copyOf(malformed));
    }

    private static RetrievalAnswerInvocationOutcome record(
            ChatInvocationOutcome outcome,
            RetrievalAnswerPromptContract prompt,
            RetrievalAnswerModelIdentity expectedModel
    ) {
        if (outcome == null) {
            throw new IllegalStateException("Answer provider returned no invocation outcome.");
        }
        ChatProviderModelIdentity actualModel = outcome.modelIdentity();
        if (!expectedModel.providerId().equals(actualModel.providerId())
                || !expectedModel.requestedModel().equals(actualModel.requestedModel())
                || !expectedModel.effectiveModel().equals(actualModel.effectiveModel())) {
            throw new IllegalStateException("Answer provider model identity drifted during execution.");
        }
        if (!prompt.promptId().equals(outcome.promptId()) || outcome.attemptCount() != 1) {
            throw new IllegalStateException("Answer invocation drifted from the locked prompt or one-attempt contract.");
        }
        return new RetrievalAnswerInvocationOutcome(
                expectedModel,
                prompt.promptId(),
                prompt.promptSha256(),
                outcome.invocationSucceeded(),
                outcome.rawResponse(),
                outcome.providerResponseId(),
                outcome.promptTokens(),
                outcome.completionTokens(),
                outcome.totalTokens(),
                outcome.latencyMillis(),
                outcome.attemptCount(),
                outcome.failureCategory());
    }

}
