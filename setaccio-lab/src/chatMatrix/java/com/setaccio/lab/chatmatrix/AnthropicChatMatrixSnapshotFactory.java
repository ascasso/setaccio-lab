package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatEvidenceModelIdentity;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceFrameworkVersions;
import java.util.List;
import java.util.Objects;

final class AnthropicChatMatrixSnapshotFactory {

    private AnthropicChatMatrixSnapshotFactory() {}

    static ChatPortabilitySnapshot fromResult(
            String evidenceId,
            AnthropicChatMatrixResult result,
            EvidenceFrameworkVersions frameworkVersions,
            EvidenceCodeBaseline codeBaseline
    ) {
        Objects.requireNonNull(result, "result must not be null");
        ChatEvidenceModelIdentity requestedIdentity = ChatEvidenceModelIdentity.from(result.requestedModelIdentity());
        List<ChatPortabilityRow> rows = result.rows().stream().map(row -> new ChatPortabilityRow(
                row.sequence(), row.repetition(), null, row.promptId(), row.promptSha256(),
                ChatEvidenceModelIdentity.from(row.modelIdentity()), row.invocationSucceeded(),
                row.failureCategory() == com.setaccio.lab.chat.ChatInvocationFailureCategory.NONE,
                row.promptTokens(), row.completionTokens(), row.totalTokens(), row.latencyMillis(),
                row.attemptCount(), row.failureCategory())).toList();
        return new ChatPortabilitySnapshot(
                evidenceId,
                requestedIdentity,
                result.runSettings(),
                frameworkVersions,
                codeBaseline,
                rows,
                result.preflightCostEstimate());
    }
}
