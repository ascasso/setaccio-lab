package com.setaccio.lab.vision;

import java.util.List;

record VisionCorpusCase(
        String caseId,
        String imageFile,
        String mimeType,
        String blake3,
        String referenceObservation,
        List<String> expectedConcepts,
        List<String> unsupportedDetails,
        List<String> limitations,
        VisionPrivacyReview privacyReview
) {

    VisionCorpusCase {
        expectedConcepts = expectedConcepts == null ? List.of() : List.copyOf(expectedConcepts);
        unsupportedDetails = unsupportedDetails == null ? List.of() : List.copyOf(unsupportedDetails);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
