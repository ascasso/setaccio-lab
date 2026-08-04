package com.setaccio.lab.evaluation;

import java.util.Objects;

public record LocalFactCheckFixture(
        String id,
        String pairId,
        String document,
        String claim,
        LocalFactCheckExpectedVerdict expectedVerdict
) {
    private static final String SAFE_ID = "[a-z0-9]+(?:-[a-z0-9]+)*";

    public LocalFactCheckFixture {
        id = requireId(id, "id");
        pairId = requireId(pairId, "pairId");
        document = requireText(document, "document");
        claim = requireText(claim, "claim");
        expectedVerdict = Objects.requireNonNull(expectedVerdict, "expectedVerdict must not be null");
    }

    private static String requireId(String value, String field) {
        String text = requireText(value, field);
        if (!text.matches(SAFE_ID)) {
            throw new IllegalArgumentException(field + " must be a lowercase kebab-case identifier");
        }
        return text;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        }
        return value;
    }
}
