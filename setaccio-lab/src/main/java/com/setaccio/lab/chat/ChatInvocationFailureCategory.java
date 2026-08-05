package com.setaccio.lab.chat;

public enum ChatInvocationFailureCategory {
    NONE,
    EMPTY_RESPONSE,
    MODEL_UNAVAILABLE,
    TIMEOUT,
    PROVIDER_FAILURE
}
