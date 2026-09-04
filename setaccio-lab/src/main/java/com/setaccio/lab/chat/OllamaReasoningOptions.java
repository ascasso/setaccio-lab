package com.setaccio.lab.chat;

import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Maps the provider-neutral {@link ChatReasoningPolicy} onto Ollama's request option.
 *
 * <p>Spring AI's {@code ThinkOption} stays behind this adapter. Nothing in the provider-neutral
 * chat contract mentions it.
 *
 * <p>Sending nothing is a real, distinct choice: Spring AI documents that a thinking-capable
 * model auto-enables thinking when the option is unset, so {@link ChatReasoningPolicy#DISABLED}
 * and {@link ChatReasoningPolicy#PROVIDER_DEFAULT} are not interchangeable.
 */
public final class OllamaReasoningOptions {

    private OllamaReasoningOptions() {}

    public static OllamaChatOptions.Builder apply(
            OllamaChatOptions.Builder builder,
            ChatReasoningPolicy policy
    ) {
        Objects.requireNonNull(builder, "builder must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        return switch (policy) {
            case PROVIDER_DEFAULT -> builder;
            case ENABLED -> builder.enableThinking();
            case DISABLED -> builder.disableThinking();
        };
    }

    public static OllamaChatOptions withPolicy(OllamaChatOptions options, ChatReasoningPolicy policy) {
        Objects.requireNonNull(options, "options must not be null");
        return apply(options.mutate(), policy).build();
    }

    public static ChatReasoningSupport support(ChatReasoningPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        return policy == ChatReasoningPolicy.PROVIDER_DEFAULT
                ? ChatReasoningSupport.NOT_REQUESTED
                : ChatReasoningSupport.APPLIED;
    }
}
