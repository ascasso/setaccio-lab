package com.setaccio.lab.chat;

import java.util.Objects;

/**
 * Provider-neutral, serializable model identity for saved evidence. A hosted identifier is never
 * represented as a fabricated local digest.
 */
public record ChatEvidenceModelIdentity(
        String providerId,
        String requestedModel,
        String effectiveModel,
        ChatModelIdentifierKind identifierKind,
        String localDigest
) {

    public ChatEvidenceModelIdentity {
        providerId = requireText(providerId, "providerId");
        requestedModel = requireText(requestedModel, "requestedModel");
        effectiveModel = requireText(effectiveModel, "effectiveModel");
        identifierKind = Objects.requireNonNull(identifierKind, "identifierKind must not be null");
        if (identifierKind == ChatModelIdentifierKind.LOCAL_DIGEST) {
            if (localDigest == null || !localDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("localDigest must be a full lowercase SHA-256 digest");
            }
        } else if (localDigest != null) {
            throw new IllegalArgumentException("hosted model identity must not claim a local digest");
        }
    }

    public static ChatEvidenceModelIdentity from(OllamaChatModelIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return new ChatEvidenceModelIdentity(
                identity.providerId(),
                identity.requestedModel(),
                identity.effectiveModel(),
                ChatModelIdentifierKind.LOCAL_DIGEST,
                identity.digest());
    }

    public static ChatEvidenceModelIdentity from(AnthropicChatModelIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return new ChatEvidenceModelIdentity(
                identity.providerId(),
                identity.requestedModel(),
                identity.effectiveModel(),
                identity.versionedModelId()
                        ? ChatModelIdentifierKind.HOSTED_VERSIONED
                        : ChatModelIdentifierKind.HOSTED_ALIAS,
                null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return value;
    }
}
