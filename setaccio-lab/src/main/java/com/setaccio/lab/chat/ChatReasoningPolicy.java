package com.setaccio.lab.chat;

/**
 * The reasoning policy a caller explicitly requests for one invocation.
 *
 * <p>This is provider-neutral on purpose. Provider-specific reasoning types stay inside the
 * adapter that speaks to that provider.
 *
 * <p>{@link #PROVIDER_DEFAULT} means the caller sends no policy at all and the model's own
 * default applies. That is not the same as {@link #DISABLED}: a thinking-capable Ollama model
 * auto-enables thinking when no policy is sent.
 */
public enum ChatReasoningPolicy {
    PROVIDER_DEFAULT,
    ENABLED,
    DISABLED
}
