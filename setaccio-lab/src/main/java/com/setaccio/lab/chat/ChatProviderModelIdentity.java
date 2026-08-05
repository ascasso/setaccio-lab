package com.setaccio.lab.chat;

public interface ChatProviderModelIdentity {

    String providerId();

    String requestedModel();

    String effectiveModel();
}
