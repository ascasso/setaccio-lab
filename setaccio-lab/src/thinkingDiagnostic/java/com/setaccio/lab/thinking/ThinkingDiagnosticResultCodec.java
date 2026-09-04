package com.setaccio.lab.thinking;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.ChatReasoningSupport;
import com.setaccio.lab.chat.ChatThinkingPresence;
import com.setaccio.lab.evaluation.LocalFactCheckExpectedVerdict;
import com.setaccio.lab.evaluation.LocalFactCheckJudgeVerdict;
import java.nio.file.Path;
import java.util.List;

/** Reads and writes the exact v1 and v2 raw wire formats without rewriting retained evidence. */
final class ThinkingDiagnosticResultCodec {

    private final ObjectMapper objectMapper;

    ThinkingDiagnosticResultCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    ThinkingDiagnosticResult read(Path path) throws Exception {
        JsonNode root = objectMapper.readTree(path.toFile());
        JsonNode versionNode = root == null ? null : root.get("protocolVersion");
        if (versionNode == null || !versionNode.canConvertToInt()) {
            throw new IllegalArgumentException("Raw thinking diagnostic has no integer protocolVersion");
        }
        int version = versionNode.intValue();
        return switch (version) {
            case ThinkingDiagnosticProtocol.LEGACY_VERSION -> fromV1(readTree(root, V1Result.class));
            case ThinkingDiagnosticProtocol.VERSION -> readTree(root, ThinkingDiagnosticResult.class);
            default -> throw new IllegalArgumentException(
                    "Unsupported thinking diagnostic protocol version: " + version);
        };
    }

    byte[] write(ThinkingDiagnosticResult result) throws Exception {
        Object wireResult = result.protocolVersion() == ThinkingDiagnosticProtocol.LEGACY_VERSION
                ? toV1(result) : result;
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(wireResult);
    }

    private <T> T readTree(JsonNode root, Class<T> type) throws Exception {
        try (JsonParser parser = root.traverse(objectMapper)) {
            return objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(parser);
        }
    }

    private static ThinkingDiagnosticResult fromV1(V1Result source) {
        return new ThinkingDiagnosticResult(
                source.protocolVersion(), source.provider(), source.endpointCategory(),
                source.executionStrategy(), source.pullModelStrategy(), source.temperature(),
                source.seed(), source.maxAttempts(), source.requestTimeoutMillis(),
                null, null, null,
                source.arms().stream().map(V1Arm::toCurrent).toList(),
                source.modelIdentities(), source.ollamaVersion(), source.promptId(),
                source.promptVersion(), source.promptSha256(), source.fixtureCatalogId(),
                source.fixtureCatalogVersion(), source.fixtureCatalogSha256(),
                source.orderedSchedule(), source.rows().stream().map(V1Row::toCurrent).toList());
    }

    private static V1Result toV1(ThinkingDiagnosticResult source) {
        return new V1Result(
                source.protocolVersion(), source.provider(), source.endpointCategory(),
                source.executionStrategy(), source.pullModelStrategy(), source.temperature(),
                source.seed(), source.maxAttempts(), source.requestTimeoutMillis(),
                source.arms().stream().map(V1Arm::fromCurrent).toList(),
                source.modelIdentities(), source.ollamaVersion(), source.promptId(),
                source.promptVersion(), source.promptSha256(), source.fixtureCatalogId(),
                source.fixtureCatalogVersion(), source.fixtureCatalogSha256(),
                source.orderedSchedule(), source.rows().stream().map(V1Row::fromCurrent).toList());
    }

    private record V1Result(
            int protocolVersion,
            String provider,
            String endpointCategory,
            String executionStrategy,
            String pullModelStrategy,
            double temperature,
            int seed,
            int maxAttempts,
            long requestTimeoutMillis,
            List<V1Arm> arms,
            List<ThinkingDiagnosticModelIdentity> modelIdentities,
            String ollamaVersion,
            String promptId,
            String promptVersion,
            String promptSha256,
            String fixtureCatalogId,
            String fixtureCatalogVersion,
            String fixtureCatalogSha256,
            List<ThinkingDiagnosticScheduleEntry> orderedSchedule,
            List<V1Row> rows
    ) {}

    private record V1Arm(
            String armId,
            ThinkingDiagnosticModelRole modelRole,
            ChatReasoningPolicy reasoningPolicy,
            int maxOutputTokens
    ) {
        private ThinkingDiagnosticArm toCurrent() {
            return new ThinkingDiagnosticArm(
                    armId, modelRole, reasoningPolicy,
                    ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR,
                    maxOutputTokens, false);
        }

        private static V1Arm fromCurrent(ThinkingDiagnosticArm arm) {
            return new V1Arm(
                    arm.armId(), arm.modelRole(), arm.reasoningPolicy(), arm.maxOutputTokens());
        }
    }

    private record V1Row(
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
        private ThinkingDiagnosticRow toCurrent() {
            return new ThinkingDiagnosticRow(
                    sequence, armId, ThinkingDiagnosticExecutionBoundary.FACT_CHECK_EVALUATOR,
                    modelRole, requestedModel, requestedReasoningPolicy, reasoningPolicySupport,
                    modelAdvertisesThinking, maxOutputTokens, seed, fixtureId, pairId,
                    expectedVerdict, documentBlake3, claimBlake3, invocationSucceeded, content,
                    thinking, thinkingPresence, finishReason, evaluatedOutputTokens, promptTokens,
                    totalTokens, normalizedJudgeVerdict, expectedVerdictMatched, outcome,
                    latencyMillis, attemptCount, error);
        }

        private static V1Row fromCurrent(ThinkingDiagnosticRow row) {
            return new V1Row(
                    row.sequence(), row.armId(), row.modelRole(), row.requestedModel(),
                    row.requestedReasoningPolicy(), row.reasoningPolicySupport(),
                    row.modelAdvertisesThinking(), row.maxOutputTokens(), row.seed(),
                    row.fixtureId(), row.pairId(), row.expectedVerdict(), row.documentBlake3(),
                    row.claimBlake3(), row.invocationSucceeded(), row.content(), row.thinking(),
                    row.thinkingPresence(), row.finishReason(), row.evaluatedOutputTokens(),
                    row.promptTokens(), row.totalTokens(), row.normalizedJudgeVerdict(),
                    row.expectedVerdictMatched(), row.outcome(), row.latencyMillis(),
                    row.attemptCount(), row.error());
        }
    }
}
