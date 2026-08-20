package com.setaccio.lab.toolcompat;

record ToolCompatibilityTokenUsageEvidence(
        ToolCompatibilityUsageAvailability availability,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    ToolCompatibilityTokenUsageEvidence {
        if (availability == null) {
            throw new IllegalArgumentException("usage availability must not be null");
        }
        if (negative(promptTokens) || negative(completionTokens) || negative(totalTokens)) {
            throw new IllegalArgumentException("usage token counts must be non-negative when present");
        }
        boolean allAbsent = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean allPresent = promptTokens != null && completionTokens != null && totalTokens != null;
        if (availability == ToolCompatibilityUsageAvailability.ABSENT && !allAbsent) {
            throw new IllegalArgumentException("absent usage must not contain token counts");
        }
        if (availability == ToolCompatibilityUsageAvailability.COMPLETE && !allPresent) {
            throw new IllegalArgumentException("complete usage must contain every token count");
        }
        if (availability == ToolCompatibilityUsageAvailability.PARTIAL && allAbsent) {
            throw new IllegalArgumentException("partial usage must contain at least one token count");
        }
        if (availability == ToolCompatibilityUsageAvailability.COMPLETE
                && totalTokens.longValue() != (long) promptTokens + completionTokens) {
            throw new IllegalArgumentException("complete usage total must equal prompt plus completion tokens");
        }
    }

    static ToolCompatibilityTokenUsageEvidence observed(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        boolean allAbsent = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean allPresent = promptTokens != null && completionTokens != null && totalTokens != null;
        ToolCompatibilityUsageAvailability availability = allAbsent
                ? ToolCompatibilityUsageAvailability.ABSENT
                : allPresent
                        ? ToolCompatibilityUsageAvailability.COMPLETE
                        : ToolCompatibilityUsageAvailability.PARTIAL;
        return new ToolCompatibilityTokenUsageEvidence(
                availability, promptTokens, completionTokens, totalTokens);
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }
}

enum ToolCompatibilityUsageAvailability {
    ABSENT,
    PARTIAL,
    COMPLETE
}
