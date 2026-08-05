package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatEvidenceModelIdentity;
import com.setaccio.lab.evidence.EvidenceCodeBaseline;
import com.setaccio.lab.evidence.EvidenceFrameworkVersions;
import java.util.List;
import java.util.Objects;

/**
 * Provider-aware projection of verified saved evidence. It retains no raw answer, credential,
 * header, endpoint, account, or provider payload.
 */
record ChatPortabilitySnapshot(
        String evidenceId,
        ChatEvidenceModelIdentity requestedModelIdentity,
        ChatPortabilityRunSettings settings,
        EvidenceFrameworkVersions frameworkVersions,
        EvidenceCodeBaseline codeBaseline,
        List<ChatPortabilityRow> rows,
        ChatEstimatedCost estimatedCost
) {

    ChatPortabilitySnapshot {
        if (evidenceId == null || !evidenceId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("evidenceId must be one safe identifier");
        }
        requestedModelIdentity = Objects.requireNonNull(requestedModelIdentity, "requestedModelIdentity must not be null");
        settings = Objects.requireNonNull(settings, "settings must not be null");
        frameworkVersions = Objects.requireNonNull(frameworkVersions, "frameworkVersions must not be null");
        codeBaseline = Objects.requireNonNull(codeBaseline, "codeBaseline must not be null");
        rows = rows == null ? List.of() : List.copyOf(rows);
        estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost must not be null");
        if (rows.size() != settings.plannedCallCount()) {
            throw new IllegalArgumentException("snapshot rows must equal the planned call count");
        }
        if (estimatedCost.outputTokenCeiling() != (long) settings.plannedCallCount() * settings.maxOutputTokens()) {
            throw new IllegalArgumentException("estimated output token ceiling must equal the locked call/token cap");
        }
        for (int index = 0; index < rows.size(); index++) {
            ChatPortabilityRow row = rows.get(index);
            ChatPromptIdentity expectedPrompt = settings.orderedPromptIdentities()
                    .get(index % settings.orderedPromptIdentities().size());
            int expectedRepetition = (index / settings.orderedPromptIdentities().size()) + 1;
            if (row == null || row.sequence() != index + 1 || row.repetition() != expectedRepetition
                    || !expectedPrompt.id().equals(row.promptId())
                    || !expectedPrompt.sha256().equals(row.promptSha256())
                    || !requestedModelIdentity.providerId().equals(row.modelIdentity().providerId())
                    || !requestedModelIdentity.requestedModel().equals(row.modelIdentity().requestedModel())) {
                throw new IllegalArgumentException("snapshot rows must match the locked provider and prompt schedule");
            }
            if (settings.optionSupport().supports(com.setaccio.lab.chat.ChatGenerationOption.SEED)) {
                if (!settings.seeds().get(expectedRepetition - 1).equals(row.seed())) {
                    throw new IllegalArgumentException("snapshot row seed does not match the locked supported setting");
                }
            } else if (row.seed() != null) {
                throw new IllegalArgumentException("snapshot row must not simulate an unsupported seed");
            }
        }
    }
}
