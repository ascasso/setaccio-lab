package com.setaccio.lab.chat;

public final class ChatModelUnavailableException extends RuntimeException {

    public ChatModelUnavailableException(String message) {
        super(message);
    }

    public ChatModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
