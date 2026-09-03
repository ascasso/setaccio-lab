package com.setaccio.lab.chat;

/** Whether a separate reasoning field was present on a recorded provider response. */
public enum ChatThinkingPresence {

    /** The response carried a non-blank reasoning field, separate from the assistant content. */
    PRESENT,

    /** The response was read and carried no reasoning field, or a blank one. */
    ABSENT,

    /** No response was obtained, so presence could not be determined. */
    UNAVAILABLE
}
