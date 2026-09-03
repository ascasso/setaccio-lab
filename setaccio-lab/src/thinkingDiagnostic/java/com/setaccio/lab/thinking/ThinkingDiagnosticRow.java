package com.setaccio.lab.thinking;

import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatThinkingPresence;
import com.setaccio.lab.evaluation.LocalFactCheckExpectedVerdict;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeVerdict;
import java.util.Objects;

/**
 * One recorded diagnostic row.
 *
 * <p>{@code content} and {@code thinking} are stored separately and are never merged. Both stay
 * in the ignored raw artifact only; the deterministic summary reports aggregates.
 */
public record ThinkingDiagnosticRow(
        int sequence,
        String armId,
        ThinkingDiagnosticModelRole modelRole,
        String requestedModel,
        ChatReasoningPolicy requestedReasoningPolicy,
        ChatReasoningSupport reasoningPolicySupport,
        boolean modelAdvertisesThinking,
        int maxOutputTokens,
        int seed,
        String fixtureId,
        String pairId,
        LocalFactCheckExpectedVerdict expectedVerdict,
        String documentBlake3,
        String claimBlake3,
        boolean invocationSucceeded,
        String content,
        String thinking,
        ChatThinkingPresence thinkingPresence,
        String finishReason,
        Integer evaluatedOutputTokens,
        Integer promptTokens,
        Integer totalTokens,
        LocalFactCheckJudgeVerdict normalizedJudgeVerdict,
        Boolean expectedVerdictMatched,
        ThinkingDiagnosticOutcome outcome,
        long latencyMillis,
        int attemptCount,
        String error
) {
    public ThinkingDiagnosticRow {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (armId == null || armId.isBlank()) {
            throw new IllegalArgumentException("armId must not be blank");
        }
        modelRole = Objects.requireNonNull(modelRole, "modelRole must not be null");
        if (requestedModel == null || requestedModel.isBlank()) {
            throw new IllegalArgumentException("requestedModel must not be blank");
        }
        requestedReasoningPolicy = Objects.requireNonNull(
                requestedReasoningPolicy, "requestedReasoningPolicy must not be null");
        reasoningPolicySupport = Objects.requireNonNull(
                reasoningPolicySupport, "reasoningPolicySupport must not be null");
        thinkingPresence = Objects.requireNonNull(thinkingPresence, "thinkingPresence must not be null");
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        expectedVerdict = Objects.requireNonNull(expectedVerdict, "expectedVerdict must not be null");
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis must not be negative");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if ((thinkingPresence == ChatThinkingPresence.PRESENT) != (thinking != null && !thinking.isBlank())) {
            throw new IllegalArgumentException(
                    "thinkingPresence PRESENT must accompany non-blank thinking text, and only that");
        }
        if (invocationSucceeded == (error != null)) {
            throw new IllegalArgumentException(
                    "exactly one of a completed invocation or a recorded error is required");
        }
        if (!invocationSucceeded && (content != null || thinking != null)) {
            throw new IllegalArgumentException("a failed row must not record content or thinking");
        }
        if (evaluatedOutputTokens != null && evaluatedOutputTokens < 0) {
            throw new IllegalArgumentException("evaluatedOutputTokens must not be negative");
        }
    }

    /** True when the row spent its whole explicit output budget. */
    public boolean budgetSaturated() {
        return evaluatedOutputTokens != null && evaluatedOutputTokens >= maxOutputTokens;
    }

    public boolean contentPresent() {
        return content != null && !content.isBlank();
    }
}
