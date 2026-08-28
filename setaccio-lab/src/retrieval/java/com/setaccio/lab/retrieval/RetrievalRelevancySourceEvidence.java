package com.setaccio.lab.retrieval;

/** Immutable provenance for the verified R5 raw evidence consumed by an R6 run. */
public record RetrievalRelevancySourceEvidence(
        String sourceRunId,
        String sourceRawSha256,
        String sourceManifestSha256,
        String sourceGitCommit
) {

    public RetrievalRelevancySourceEvidence {
        if (sourceRunId == null || !sourceRunId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("sourceRunId must be a safe relative run name");
        }
        requireDigest(sourceRawSha256, "sourceRawSha256");
        requireDigest(sourceManifestSha256, "sourceManifestSha256");
        if (sourceGitCommit == null || !sourceGitCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("sourceGitCommit must be a full lowercase Git commit");
        }
    }

    private static void requireDigest(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a full lowercase SHA-256 digest");
        }
    }
}
