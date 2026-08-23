package com.setaccio.lab.toolcompat;

import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaApi;

/** No-pull local session that rechecks runtime and immutable identity before every row. */
final class ToolCompatibilityCohortLiveSession
        implements ToolCompatibilityCohortExecutor.Session {

    private final ToolCompatibilityCohortOllamaInventorySource.ReadClient readClient;
    private final ControlledModelFactory controlledModelFactory;

    ToolCompatibilityCohortLiveSession(
            ToolCompatibilityCohortOllamaInventorySource.ReadClient readClient,
            ControlledModelFactory controlledModelFactory
    ) {
        this.readClient = Objects.requireNonNull(readClient, "read client must not be null");
        this.controlledModelFactory = Objects.requireNonNull(
                controlledModelFactory, "controlled model factory must not be null");
    }

    static ToolCompatibilityCohortLiveSession create(
            String baseUrl,
            ToolCompatibilityCohortOllamaInventorySource inventorySource
    ) {
        Objects.requireNonNull(inventorySource, "inventory source must not be null");
        OllamaApi ollamaApi = ToolCompatibilityInvocationBoundary.createControlledOllamaApi(baseUrl);
        return new ToolCompatibilityCohortLiveSession(
                inventorySource.client(),
                (identity, effectiveSeed) ->
                        ToolCompatibilityInvocationBoundary.createControlledCohortOllamaModel(
                                ollamaApi, identity, effectiveSeed));
    }

    @Override
    public String ollamaRuntimeVersion() {
        return readClient.runtimeVersion();
    }

    @Override
    public ToolCompatibilityCohortControlledOllamaModel controlledModel(
            ToolCompatibilityCohortModelIdentity modelIdentity,
            Integer effectiveSeed
    ) {
        ToolCompatibilityCohortOllamaInventorySource.requireIdentityStillInstalled(
                readClient, modelIdentity);
        ToolCompatibilityCohortControlledOllamaModel controlled =
                controlledModelFactory.create(modelIdentity, effectiveSeed);
        if (controlled == null
                || !modelIdentity.equals(controlled.modelIdentity())
                || !Objects.equals(effectiveSeed, controlled.effectiveSeed())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Controlled cohort model factory changed identity or seed semantics");
        }
        return controlled;
    }

    @FunctionalInterface
    interface ControlledModelFactory {
        ToolCompatibilityCohortControlledOllamaModel create(
                ToolCompatibilityCohortModelIdentity modelIdentity,
                Integer effectiveSeed);
    }
}
