package com.setaccio.lab.chat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ChatProviderOptionSupport(
        Set<ChatGenerationOption> supported,
        Map<ChatGenerationOption, String> unsupportedReasons
) {
    public ChatProviderOptionSupport {
        Objects.requireNonNull(supported, "supported must not be null");
        Objects.requireNonNull(unsupportedReasons, "unsupportedReasons must not be null");

        EnumSet<ChatGenerationOption> supportedCopy = supported.isEmpty()
                ? EnumSet.noneOf(ChatGenerationOption.class)
                : EnumSet.copyOf(supported);
        EnumMap<ChatGenerationOption, String> unsupportedCopy = new EnumMap<>(ChatGenerationOption.class);
        unsupportedReasons.forEach((option, reason) -> {
            Objects.requireNonNull(option, "unsupported option must not be null");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("unsupported option reason must not be blank");
            }
            unsupportedCopy.put(option, reason);
        });

        for (ChatGenerationOption option : ChatGenerationOption.values()) {
            boolean isSupported = supportedCopy.contains(option);
            boolean isUnsupported = unsupportedCopy.containsKey(option);
            if (isSupported == isUnsupported) {
                throw new IllegalArgumentException(
                        "option " + option + " must be classified exactly once as supported or unsupported");
            }
        }

        supported = Collections.unmodifiableSet(supportedCopy);
        unsupportedReasons = Collections.unmodifiableMap(unsupportedCopy);
    }

    public static ChatProviderOptionSupport supportsAll() {
        return new ChatProviderOptionSupport(EnumSet.allOf(ChatGenerationOption.class), Map.of());
    }
}
