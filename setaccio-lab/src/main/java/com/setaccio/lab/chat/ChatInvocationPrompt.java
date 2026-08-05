package com.setaccio.lab.chat;

public record ChatInvocationPrompt(String id, String text) {
    public ChatInvocationPrompt {
        id = requireText(id, "id", true);
        text = requireText(text, "text", false);
    }

    private static String requireText(String value, String field, boolean rejectSurroundingWhitespace) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (rejectSurroundingWhitespace && !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return value;
    }
}
