package com.setaccio.lab.chat;

/**
 * Identifies an Anthropic-hosted model without claiming a local content digest.
 */
public record AnthropicChatModelIdentity(
        String providerId,
        String requestedModel,
        String effectiveModel,
        boolean versionedModelId
) implements ChatProviderModelIdentity {

    public static final String ANTHROPIC_PROVIDER_ID = "anthropic";

    public AnthropicChatModelIdentity {
        providerId = requireText(providerId, "providerId");
        if (!ANTHROPIC_PROVIDER_ID.equals(providerId)) {
            throw new IllegalArgumentException("providerId must be anthropic");
        }
        requestedModel = requireText(requestedModel, "requestedModel");
        effectiveModel = requireText(effectiveModel, "effectiveModel");
    }

    public AnthropicChatModelIdentity withEffectiveModel(String responseModel) {
        if (responseModel == null || responseModel.isBlank()) {
            return this;
        }
        return new AnthropicChatModelIdentity(providerId, requestedModel, responseModel, versionedModelId);
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
