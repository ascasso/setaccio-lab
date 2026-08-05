package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatEvidenceModelIdentity;
import com.setaccio.lab.evidence.EvidenceManifest;
import java.util.List;
import java.util.Objects;

/** Converts already-verified Ollama matrix evidence into the raw-output-free portability envelope. */
final class ChatPortabilitySnapshotFactory {

    private ChatPortabilitySnapshotFactory() {}

    static ChatPortabilitySnapshot fromVerifiedOllama(
            String evidenceId,
            ChatMatrixResult result,
            EvidenceManifest manifest,
            ChatEstimatedCost estimatedCost
    ) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        ChatMatrixRunSettings runSettings = Objects.requireNonNull(result.runSettings(), "runSettings must not be null");
        ChatEvidenceModelIdentity identity = ChatEvidenceModelIdentity.from(result.modelIdentity());
        ChatPortabilityRunSettings settings = new ChatPortabilityRunSettings(
                result.promptCatalogId(),
                result.promptCatalogVersion(),
                result.promptCatalogSha256(),
                result.orderedPromptIdentities(),
                runSettings.repetitions(),
                result.rows().size(),
                runSettings.temperature(),
                runSettings.maxOutputTokens(),
                runSettings.timeoutMillis(),
                runSettings.maxAttempts(),
                runSettings.seeds(),
                result.rows().isEmpty()
                        ? com.setaccio.lab.chat.ChatProviderOptionSupport.supportsAll()
                        : result.rows().getFirst().optionSupport());
        List<ChatPortabilityRow> rows = result.rows().stream().map(row -> new ChatPortabilityRow(
                row.sequence(),
                row.repetition(),
                row.seed(),
                row.promptId(),
                row.promptSha256(),
                identity,
                row.invocationSucceeded(),
                row.failureCategory() == com.setaccio.lab.chat.ChatInvocationFailureCategory.NONE,
                row.promptTokens(),
                row.completionTokens(),
                row.totalTokens(),
                row.latencyMillis(),
                row.attemptCount(),
                row.failureCategory())).toList();
        return new ChatPortabilitySnapshot(
                evidenceId,
                identity,
                settings,
                manifest.frameworkVersions(),
                manifest.codeBaseline(),
                rows,
                estimatedCost);
    }
}
