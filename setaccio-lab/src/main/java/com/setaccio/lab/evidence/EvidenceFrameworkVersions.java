package com.setaccio.lab.evidence;

public record EvidenceFrameworkVersions(
        String springBoot,
        String springAi
) {

    public EvidenceFrameworkVersions {
        springBoot = requireText(springBoot, "springBoot");
        springAi = requireText(springAi, "springAi");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
