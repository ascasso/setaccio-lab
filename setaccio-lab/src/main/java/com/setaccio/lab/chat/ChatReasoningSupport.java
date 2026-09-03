package com.setaccio.lab.chat;

/** How an adapter handled the requested {@link ChatReasoningPolicy} for one invocation. */
public enum ChatReasoningSupport {

    /** The adapter mapped the requested policy onto a provider option it accepts. */
    APPLIED,

    /** The caller requested {@link ChatReasoningPolicy#PROVIDER_DEFAULT}; nothing was sent. */
    NOT_REQUESTED,

    /** The provider has no equivalent option, so an explicit policy could not be expressed. */
    UNSUPPORTED
}
