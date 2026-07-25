package com.setaccio.lab.evidence;

public record EvidenceCodeBaseline(
        String gitCommit,
        boolean workingTreeDirty
) {

    public EvidenceCodeBaseline {
        if (gitCommit == null || gitCommit.isBlank()) {
            throw new IllegalArgumentException("gitCommit must not be blank");
        }
    }
}
