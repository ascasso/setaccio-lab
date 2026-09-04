package com.setaccio.lab.thinking;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocation;
import com.setaccio.lab.chat.ChatReasoningPolicy;
import com.setaccio.lab.chat.OllamaChatModelIdentity;

/** Creates the provider-neutral chat invocation boundary for one diagnostic arm. */
@FunctionalInterface
public interface ThinkingDiagnosticChatFactory {

    ChatInvocation create(
            OllamaChatModelIdentity identity,
            ChatGenerationSettings settings,
            ChatReasoningPolicy reasoningPolicy
    );
}
