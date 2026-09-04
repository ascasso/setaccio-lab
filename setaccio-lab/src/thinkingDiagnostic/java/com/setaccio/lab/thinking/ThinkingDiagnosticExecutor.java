package com.setaccio.lab.thinking;

import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatInvocationFailureCategory;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatInvocationPrompt;
import com.setaccio.lab.chat.ChatInvocationRequest;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.ChatThinkingPresence;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import com.setaccio.lab.chat.OllamaReasoningOptions;
import com.setaccio.lab.evaluation.LocalFactCheckDiagnosticCategory;
import com.setaccio.lab.evaluation.LocalFactCheckFixture;
import com.setaccio.lab.evaluation.LocalFactCheckFixtureCatalog;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeBoundary;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeResult;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeSettings;
import com.setaccio.lab.evaluation.LocalFactCheckPromptDefinition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runs the locked v2 schedule sequentially, once per arm and fixture, retaining every row. */
public final class ThinkingDiagnosticExecutor {

    private static final Blake3HashingService BLAKE3 = new ApacheCommonsBlake3HashingServiceImpl();

    private final ThinkingDiagnosticJudgeFactory judgeFactory;
    private final ThinkingDiagnosticChatFactory chatFactory;
    private final LocalFactCheckPromptDefinition promptDefinition;

    public ThinkingDiagnosticExecutor(
            ThinkingDiagnosticJudgeFactory judgeFactory,
            ThinkingDiagnosticChatFactory chatFactory,
            LocalFactCheckPromptDefinition promptDefinition
    ) {
        this.judgeFactory = Objects.requireNonNull(judgeFactory, "judgeFactory must not be null");
        this.chatFactory = Objects.requireNonNull(chatFactory, "chatFactory must not be null");
        this.promptDefinition = Objects.requireNonNull(
                promptDefinition, "promptDefinition must not be null");
    }

    public ThinkingDiagnosticResult execute(
            LocalFactCheckFixtureCatalog catalog,
            Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> identities,
            String ollamaVersion
    ) {
        Objects.requireNonNull(catalog, "catalog must not be null");
        Objects.requireNonNull(identities, "identities must not be null");
        for (ThinkingDiagnosticModelRole role : ThinkingDiagnosticModelRole.values()) {
            if (identities.get(role) == null) {
                throw new IllegalArgumentException("Missing resolved model identity for role " + role);
            }
        }

        List<ThinkingDiagnosticScheduleEntry> schedule = ThinkingDiagnosticProtocol.schedule(catalog);
        List<ThinkingDiagnosticRow> rows = new ArrayList<>(schedule.size());
        for (ThinkingDiagnosticScheduleEntry entry : schedule) {
            ThinkingDiagnosticArm arm = ThinkingDiagnosticProtocol.requireArm(entry.armId());
            ThinkingDiagnosticModelIdentity identity = identities.get(arm.modelRole());
            LocalFactCheckFixture fixture = catalog.require(entry.fixtureId());
            rows.add(runRow(entry, arm, identity, fixture));
        }

        Map<ThinkingDiagnosticModelRole, ThinkingDiagnosticModelIdentity> ordered =
                new EnumMap<>(identities);
        return new ThinkingDiagnosticResult(
                ThinkingDiagnosticProtocol.VERSION,
                ThinkingDiagnosticProtocol.PROVIDER,
                ThinkingDiagnosticProtocol.ENDPOINT_CATEGORY,
                ThinkingDiagnosticProtocol.EXECUTION_STRATEGY,
                ThinkingDiagnosticProtocol.PULL_MODEL_STRATEGY,
                ThinkingDiagnosticProtocol.TEMPERATURE,
                ThinkingDiagnosticProtocol.SEED,
                ThinkingDiagnosticProtocol.MAX_ATTEMPTS,
                ThinkingDiagnosticProtocol.REQUEST_TIMEOUT.toMillis(),
                ThinkingDiagnosticProtocol.PROMPT_DELIVERY,
                ThinkingDiagnosticProtocol.POLICY_COMPARISON,
                ThinkingDiagnosticProtocol.BOUNDARY_COMPARISON,
                ThinkingDiagnosticProtocol.ARMS,
                List.copyOf(ordered.values()),
                ollamaVersion,
                promptDefinition.id(),
                promptDefinition.version(),
                promptDefinition.sha256(),
                catalog.id(),
                catalog.version(),
                catalog.sha256(),
                schedule,
                List.copyOf(rows));
    }

    private ThinkingDiagnosticRow runRow(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture
    ) {
        try {
            return switch (arm.executionBoundary()) {
                case FACT_CHECK_EVALUATOR -> runFactCheckRow(entry, arm, identity, fixture);
                case CHAT_INVOCATION -> runChatRow(entry, arm, identity, fixture);
            };
        } catch (RuntimeException exception) {
            return failedRow(entry, arm, identity, fixture, safeMessage(exception));
        }
    }

    private ThinkingDiagnosticRow runFactCheckRow(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture
    ) {
        LocalFactCheckJudgeSettings settings = new LocalFactCheckJudgeSettings(
                identity.requestedModel(), ThinkingDiagnosticProtocol.TEMPERATURE, entry.seed(),
                arm.maxOutputTokens(), ThinkingDiagnosticProtocol.REQUEST_TIMEOUT,
                ThinkingDiagnosticProtocol.MAX_ATTEMPTS);
        LocalFactCheckJudgeResult result = new LocalFactCheckJudgeBoundary(
                judgeFactory.create(settings), settings, promptDefinition, arm.reasoningPolicy())
                .evaluate(fixture);
        ChatResponseCapture capture = result.capture();
        boolean succeeded = result.invocationSucceeded();
        ChatThinkingPresence presence = capture == null
                ? ChatThinkingPresence.UNAVAILABLE : capture.thinkingPresence();
        String content = succeeded && capture != null ? capture.content() : null;
        String thinking = succeeded && capture != null ? capture.thinking() : null;
        return row(
                entry, arm, identity, fixture, succeeded, content, thinking,
                succeeded ? presence : ChatThinkingPresence.UNAVAILABLE,
                capture == null ? null : capture.finishReason(),
                capture == null ? null : capture.evaluatedOutputTokens(),
                result.promptTokens(), result.totalTokens(),
                succeeded ? result.normalizedJudgeVerdict() : null,
                succeeded ? result.expectedVerdictMatched() : null,
                outcome(result.diagnosticCategory(), succeeded, content, presence),
                result.latencyMillis(), result.attemptCount(),
                capture == null ? OllamaReasoningOptions.support(arm.reasoningPolicy())
                        : capture.reasoningPolicySupport(),
                succeeded ? null : requireError(result));
    }

    private ThinkingDiagnosticRow runChatRow(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture
    ) {
        ChatGenerationSettings settings = new ChatGenerationSettings(
                ThinkingDiagnosticProtocol.TEMPERATURE, entry.seed(), arm.maxOutputTokens(),
                ThinkingDiagnosticProtocol.REQUEST_TIMEOUT, ThinkingDiagnosticProtocol.MAX_ATTEMPTS);
        OllamaChatModelIdentity chatIdentity = new OllamaChatModelIdentity(
                ThinkingDiagnosticProtocol.PROVIDER, identity.requestedModel(),
                identity.normalizedInstalledName(), identity.digest());
        ChatInvocation invocation = chatFactory.create(chatIdentity, settings, arm.reasoningPolicy());
        ChatInvocationOutcome result = invocation.invoke(new ChatInvocationRequest(
                chatIdentity,
                new ChatInvocationPrompt(
                        promptDefinition.id(),
                        promptDefinition.render(fixture.document(), fixture.claim())),
                settings));
        ChatResponseCapture capture = result.capture();
        boolean succeeded = result.invocationSucceeded();
        ChatThinkingPresence presence = capture == null
                ? ChatThinkingPresence.UNAVAILABLE : capture.thinkingPresence();
        String content = succeeded && capture != null ? capture.content() : null;
        String thinking = succeeded && capture != null ? capture.thinking() : null;
        return row(
                entry, arm, identity, fixture, succeeded, content, thinking,
                succeeded ? presence : ChatThinkingPresence.UNAVAILABLE,
                capture == null ? null : capture.finishReason(),
                capture == null ? null : capture.evaluatedOutputTokens(),
                result.promptTokens(), result.totalTokens(), null, null,
                outcome(result.failureCategory(), succeeded, content, presence),
                result.latencyMillis(), result.attemptCount(),
                capture == null ? OllamaReasoningOptions.support(arm.reasoningPolicy())
                        : capture.reasoningPolicySupport(),
                succeeded ? null : result.error());
    }

    private static ThinkingDiagnosticRow row(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture,
            boolean succeeded,
            String content,
            String thinking,
            ChatThinkingPresence presence,
            String finishReason,
            Integer evaluatedOutputTokens,
            Integer promptTokens,
            Integer totalTokens,
            com.setaccio.lab.evaluation.LocalFactCheckJudgeVerdict normalizedJudgeVerdict,
            Boolean expectedVerdictMatched,
            ThinkingDiagnosticOutcome outcome,
            long latencyMillis,
            int attemptCount,
            ChatReasoningSupport reasoningSupport,
            String error
    ) {
        return new ThinkingDiagnosticRow(
                entry.sequence(), arm.armId(), arm.executionBoundary(), arm.modelRole(),
                identity.requestedModel(), arm.reasoningPolicy(), reasoningSupport,
                identity.advertisesThinking(), arm.maxOutputTokens(), entry.seed(), fixture.id(),
                fixture.pairId(), fixture.expectedVerdict(), BLAKE3.hashString(fixture.document()),
                BLAKE3.hashString(fixture.claim()), succeeded, content, thinking, presence,
                finishReason, evaluatedOutputTokens, promptTokens, totalTokens,
                normalizedJudgeVerdict, expectedVerdictMatched, outcome, latencyMillis,
                attemptCount, error);
    }

    private static ThinkingDiagnosticRow failedRow(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture,
            String error
    ) {
        return row(
                entry, arm, identity, fixture, false, null, null,
                ChatThinkingPresence.UNAVAILABLE, null, null, null, null, null, null,
                ThinkingDiagnosticOutcome.PROVIDER_FAILURE, 0L, 1,
                OllamaReasoningOptions.support(arm.reasoningPolicy()), error);
    }

    private static ThinkingDiagnosticOutcome outcome(
            LocalFactCheckDiagnosticCategory category,
            boolean succeeded,
            String content,
            ChatThinkingPresence presence
    ) {
        if (!succeeded) {
            return switch (category) {
                case JUDGE_MODEL_UNAVAILABLE -> ThinkingDiagnosticOutcome.MODEL_UNAVAILABLE;
                case TIMEOUT -> ThinkingDiagnosticOutcome.TIMEOUT;
                default -> ThinkingDiagnosticOutcome.PROVIDER_FAILURE;
            };
        }
        return responseOutcome(content, presence);
    }

    private static ThinkingDiagnosticOutcome outcome(
            ChatInvocationFailureCategory category,
            boolean succeeded,
            String content,
            ChatThinkingPresence presence
    ) {
        if (!succeeded) {
            return switch (category) {
                case MODEL_UNAVAILABLE -> ThinkingDiagnosticOutcome.MODEL_UNAVAILABLE;
                case TIMEOUT -> ThinkingDiagnosticOutcome.TIMEOUT;
                default -> ThinkingDiagnosticOutcome.PROVIDER_FAILURE;
            };
        }
        return responseOutcome(content, presence);
    }

    private static ThinkingDiagnosticOutcome responseOutcome(
            String content,
            ChatThinkingPresence presence
    ) {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasThinking = presence == ChatThinkingPresence.PRESENT;
        if (hasContent) {
            return hasThinking
                    ? ThinkingDiagnosticOutcome.CONTENT_WITH_THINKING
                    : ThinkingDiagnosticOutcome.CONTENT_WITHOUT_THINKING;
        }
        return hasThinking
                ? ThinkingDiagnosticOutcome.EMPTY_CONTENT_WITH_THINKING
                : ThinkingDiagnosticOutcome.EMPTY_CONTENT_WITHOUT_THINKING;
    }

    private static String requireError(LocalFactCheckJudgeResult result) {
        String error = result.error();
        return error == null || error.isBlank()
                ? "Diagnostic row failed without a recorded provider message" : error;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = null;
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}
