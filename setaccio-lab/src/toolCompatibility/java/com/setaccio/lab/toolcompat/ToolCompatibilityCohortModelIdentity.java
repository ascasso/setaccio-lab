package com.setaccio.lab.toolcompat;

/** Resolved immutable identity for one peer or the separately labelled reference. */
record ToolCompatibilityCohortModelIdentity(
        int cohortPosition,
        Role role,
        String requestedTag,
        String effectiveInstalledTag,
        String digest,
        ToolCompatibilityCohortSeedSemantics seedSemantics,
        ToolCompatibilityCohortModelMetadata metadata
) implements ToolCompatibilityResolvedModelIdentity {

    ToolCompatibilityCohortModelIdentity {
        if (cohortPosition < 1) {
            throw new IllegalArgumentException("cohortPosition must be positive");
        }
        if (role == null || seedSemantics == null || metadata == null) {
            throw new IllegalArgumentException(
                    "cohort model role, seed semantics, and metadata are required");
        }
        ToolCompatibilityResolvedModelIdentity.requireFields(
                requestedTag, effectiveInstalledTag, digest);
    }

    @Override
    public String requestedModel() {
        return requestedTag;
    }

    @Override
    public String effectiveModel() {
        return effectiveInstalledTag;
    }

    enum Role {
        PEER,
        REFERENCE
    }
}
