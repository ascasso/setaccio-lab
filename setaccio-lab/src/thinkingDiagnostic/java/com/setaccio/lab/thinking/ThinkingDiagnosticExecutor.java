package com.setaccio.lab.thinking;

import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.lab.chat.ChatResponseCapture;
import com.setaccio.lab.chat.ChatThinkingPresence;
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

/**
 * Runs the locked schedule sequentially, one logical attempt per row, retaining every row.
 *
 * <p>No row is retried, repaired, replaced, or omitted. A failure is recorded as a failure and
 * the schedule continues, because the pre-registered protocol retains all rows.
 */
public final class ThinkingDiagnosticExecutor {

    private static final Blake3HashingService BLAKE3 = new ApacheCommonsBlake3HashingServiceImpl();

    private final ThinkingDiagnosticJudgeFactory judgeFactory;
    private final LocalFactCheckPromptDefinition promptDefinition;

    public ThinkingDiagnosticExecutor(
            ThinkingDiagnosticJudgeFactory judgeFactory,
            LocalFactCheckPromptDefinition promptDefinition
    ) {
        this.judgeFactory = Objects.requireNonNull(judgeFactory, "judgeFactory must not be null");
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
        LocalFactCheckJudgeSettings settings = new LocalFactCheckJudgeSettings(
                identity.requestedModel(),
                ThinkingDiagnosticProtocol.TEMPERATURE,
                entry.seed(),
                arm.maxOutputTokens(),
                ThinkingDiagnosticProtocol.REQUEST_TIMEOUT,
                ThinkingDiagnosticProtocol.MAX_ATTEMPTS);
        LocalFactCheckJudgeBoundary boundary = new LocalFactCheckJudgeBoundary(
                judgeFactory.create(settings),
                settings,
                promptDefinition,
                arm.reasoningPolicy());

        LocalFactCheckJudgeResult result;
        try {
            result = boundary.evaluate(fixture);
        } catch (RuntimeException exception) {
            return failedRow(entry, arm, identity, fixture, safeMessage(exception));
        }
        return row(entry, arm, identity, fixture, result);
    }

    private static ThinkingDiagnosticRow row(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture,
            LocalFactCheckJudgeResult result
    ) {
        ChatResponseCapture capture = result.capture();
        boolean succeeded = result.invocationSucceeded();
        String content = succeeded && capture != null ? capture.content() : null;
        String thinking = succeeded && capture != null ? capture.thinking() : null;
        ChatThinkingPresence presence = capture == null
                ? ChatThinkingPresence.UNAVAILABLE
                : capture.thinkingPresence();
        return new ThinkingDiagnosticRow(
                entry.sequence(),
                arm.armId(),
                arm.modelRole(),
                identity.requestedModel(),
                arm.reasoningPolicy(),
                capture == null
                        ? com.setaccio.lab.chat.ChatReasoningSupport.APPLIED
                        : capture.reasoningPolicySupport(),
                identity.advertisesThinking(),
                arm.maxOutputTokens(),
                entry.seed(),
                fixture.id(),
                fixture.pairId(),
                fixture.expectedVerdict(),
                BLAKE3.hashString(fixture.document()),
                BLAKE3.hashString(fixture.claim()),
                succeeded,
                content,
                thinking,
                succeeded ? presence : ChatThinkingPresence.UNAVAILABLE,
                capture == null ? null : capture.finishReason(),
                capture == null ? null : capture.evaluatedOutputTokens(),
                result.promptTokens(),
                result.totalTokens(),
                succeeded ? result.normalizedJudgeVerdict() : null,
                succeeded ? result.expectedVerdictMatched() : null,
                outcome(result, content, presence),
                result.latencyMillis(),
                result.attemptCount(),
                succeeded ? null : requireError(result));
    }

    private static ThinkingDiagnosticRow failedRow(
            ThinkingDiagnosticScheduleEntry entry,
            ThinkingDiagnosticArm arm,
            ThinkingDiagnosticModelIdentity identity,
            LocalFactCheckFixture fixture,
            String error
    ) {
        return new ThinkingDiagnosticRow(
                entry.sequence(),
                arm.armId(),
                arm.modelRole(),
                identity.requestedModel(),
                arm.reasoningPolicy(),
                com.setaccio.lab.chat.ChatReasoningSupport.APPLIED,
                identity.advertisesThinking(),
                arm.maxOutputTokens(),
                entry.seed(),
                fixture.id(),
                fixture.pairId(),
                fixture.expectedVerdict(),
                BLAKE3.hashString(fixture.document()),
                BLAKE3.hashString(fixture.claim()),
                false,
                null,
                null,
                ChatThinkingPresence.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                ThinkingDiagnosticOutcome.PROVIDER_FAILURE,
                0L,
                1,
                error);
    }

    static ThinkingDiagnosticOutcome outcome(
            LocalFactCheckJudgeResult result,
            String content,
            ChatThinkingPresence presence
    ) {
        if (!result.invocationSucceeded()) {
            LocalFactCheckDiagnosticCategory category = result.diagnosticCategory();
            return switch (category) {
                case JUDGE_MODEL_UNAVAILABLE -> ThinkingDiagnosticOutcome.MODEL_UNAVAILABLE;
                case TIMEOUT -> ThinkingDiagnosticOutcome.TIMEOUT;
                default -> ThinkingDiagnosticOutcome.PROVIDER_FAILURE;
            };
        }
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
                ? "Diagnostic row failed without a recorded provider message"
                : error;
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
