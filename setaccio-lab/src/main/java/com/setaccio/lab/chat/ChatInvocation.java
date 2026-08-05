package com.setaccio.lab.chat;

@FunctionalInterface
public interface ChatInvocation {

    ChatInvocationOutcome invoke(ChatInvocationRequest request);
}
