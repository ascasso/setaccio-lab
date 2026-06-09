package com.setaccio.core.model;

public enum Blake3Implementation {
    APACHE_COMMONS_CODEC("apache-commons-codec", "Apache Commons Codec Blake3 implementation"),
    BOUNCY_CASTLE("bouncy-castle", "Bouncy Castle Blake3 implementation - extensive crypto suite");

    private final String key;
    private final String description;

    Blake3Implementation(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public static Blake3Implementation fromKey(String key) {
        for (Blake3Implementation impl : values()) {
            if (impl.key.equalsIgnoreCase(key)) {
                return impl;
            }
        }
        throw new IllegalArgumentException("Unknown Blake3 implementation: " + key);
    }
}