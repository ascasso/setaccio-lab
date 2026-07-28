package com.setaccio.lab.vision;

record VisionPrivacyReview(
        boolean sensitiveContentReviewed,
        boolean exifGpsReviewed,
        boolean approvedForTracking
) {}
