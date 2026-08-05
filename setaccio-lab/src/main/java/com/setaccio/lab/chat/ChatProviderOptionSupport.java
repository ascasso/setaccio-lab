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

    public boolean supports(ChatGenerationOption option) {
        return supported.contains(Objects.requireNonNull(option, "option must not be null"));
    }

    /**
     * Current adapters either apply a common option directly or reject it before invocation.
     * The explicit enum keeps the evidence vocabulary ready for later translated/ignored cases.
     */
    public Map<ChatGenerationOption, ChatProviderOptionStatus> statuses() {
        EnumMap<ChatGenerationOption, ChatProviderOptionStatus> statuses = new EnumMap<>(ChatGenerationOption.class);
        for (ChatGenerationOption option : ChatGenerationOption.values()) {
            statuses.put(option, supported.contains(option)
                    ? ChatProviderOptionStatus.SUPPORTED
                    : ChatProviderOptionStatus.REJECTED);
        }
        return Collections.unmodifiableMap(statuses);
    }
}
