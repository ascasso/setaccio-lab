package com.setaccio.lab.model;

public record VisionStructuralCheck(
        String section,
        boolean present
) {

    public VisionStructuralCheck {
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section must not be blank");
        }
    }
}
