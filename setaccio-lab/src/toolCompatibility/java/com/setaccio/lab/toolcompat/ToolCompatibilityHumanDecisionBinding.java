package com.setaccio.lab.toolcompat;

import java.time.LocalDate;

/** Immutable evidence binding copied from an owner-completed T2.5 decision. */
record ToolCompatibilityHumanDecisionBinding(
        String baselineRunId,
        String candidateRunId,
        String promptCatalogDigest,
        String comparisonReportDigest,
        LocalDate reviewDate
) {

    ToolCompatibilityHumanDecisionBinding {
        baselineRunId = requireText(baselineRunId, "baselineRunId");
        candidateRunId = requireText(candidateRunId, "candidateRunId");
        promptCatalogDigest = requireDigest(promptCatalogDigest, "promptCatalogDigest");
        comparisonReportDigest = requireDigest(comparisonReportDigest, "comparisonReportDigest");
        if (reviewDate == null) {
            throw new IllegalArgumentException("reviewDate is required");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    field + " must be one full lowercase SHA-256 digest");
        }
        return value;
    }
}
