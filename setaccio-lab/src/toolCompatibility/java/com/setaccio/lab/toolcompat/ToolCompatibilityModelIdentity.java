package com.setaccio.lab.toolcompat;

record ToolCompatibilityModelIdentity(
        String requestedModel,
        String effectiveModel,
        String digest
) implements ToolCompatibilityResolvedModelIdentity {

    ToolCompatibilityModelIdentity {
        if (!ToolCompatibilityProtocol.INITIAL_MODEL.equals(requestedModel)) {
            throw new IllegalArgumentException("requestedModel must equal the locked initial model");
        }
        ToolCompatibilityResolvedModelIdentity.requireFields(
                requestedModel, effectiveModel, digest);
    }
}
