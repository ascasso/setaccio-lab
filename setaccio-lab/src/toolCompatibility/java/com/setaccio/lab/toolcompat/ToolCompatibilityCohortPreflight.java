package com.setaccio.lab.toolcompat;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves an ordered Phase 3 cohort completely before any evidence allocation. */
final class ToolCompatibilityCohortPreflight {

    Prepared prepare(Input input, InventorySource inventorySource) {
        if (input == null || inventorySource == null) {
            throw new IllegalArgumentException("cohort preflight dependencies are required");
        }
        OllamaChatModelFactory.requireLoopbackBaseUrl(input.ollamaBaseUrl());
        List<String> requestedPeers = requireRequestedPeers(input.peerModels());
        String requestedReference = requireModelTag(input.referenceModel(), "referenceModel");
        requireDistinctRequests(requestedPeers, requestedReference);
        Path outputDirectory = ToolCompatibilityPreflight.resolveNewOutputDirectory(
                input.projectDirectory(), input.outputDirectory());

        ToolCompatibilityCohortInventory inventory = inventorySource.snapshot();
        if (inventory == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort inventory source returned no snapshot");
        }
        String runtimeVersion = requireRuntimeVersion(inventory.ollamaRuntimeVersion());
        Map<String, ToolCompatibilityCohortInventoryModel> installed = installedByTag(
                inventory.models());

        List<ToolCompatibilityCohortModelIdentity> peers = new ArrayList<>();
        Map<String, String> selectedDigestOwners = new HashMap<>();
        int position = 1;
        for (String requestedPeer : requestedPeers) {
            ToolCompatibilityCohortModelIdentity identity = resolve(
                    position++,
                    ToolCompatibilityCohortModelIdentity.Role.PEER,
                    requestedPeer,
                    installed,
                    selectedDigestOwners);
            peers.add(identity);
        }
        ToolCompatibilityCohortModelIdentity reference = resolve(
                position,
                ToolCompatibilityCohortModelIdentity.Role.REFERENCE,
                requestedReference,
                installed,
                selectedDigestOwners);

        return new Prepared(
                outputDirectory,
                runtimeVersion,
                peers,
                reference);
    }

    private static List<String> requireRequestedPeers(List<String> peerModels) {
        List<String> requested = List.copyOf(peerModels == null ? List.of() : peerModels);
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("At least one explicit peer model is required");
        }
        List<String> validated = new ArrayList<>();
        for (String model : requested) {
            validated.add(requireModelTag(model, "peerModels"));
        }
        return List.copyOf(validated);
    }

    private static String requireModelTag(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must contain nonblank trimmed model tags");
        }
        if (value.contains(",")) {
            throw new IllegalArgumentException(
                    field + " must use explicit ordered entries, not comma-separated model tags");
        }
        return value;
    }

    private static void requireDistinctRequests(List<String> peers, String reference) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String peer : peers) {
            if (!normalized.add(normalizeModelTag(peer))) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Proposed cohort contains a duplicate normalized peer tag");
            }
        }
        if (!normalized.add(normalizeModelTag(reference))) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Reference model must be separately labelled and absent from the peer list");
        }
    }

    private static String requireRuntimeVersion(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Ollama runtime version must be available before cohort allocation");
        }
        return value;
    }

    private static Map<String, ToolCompatibilityCohortInventoryModel> installedByTag(
            List<ToolCompatibilityCohortInventoryModel> models
    ) {
        Map<String, ToolCompatibilityCohortInventoryModel> installed = new LinkedHashMap<>();
        for (ToolCompatibilityCohortInventoryModel model : models) {
            if (model == null) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Cohort inventory contains a null model entry");
            }
            String normalized = normalizeModelTag(model.installedTag());
            if (installed.putIfAbsent(normalized, model) != null) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Installed Ollama inventory contains duplicate normalized tag: " + normalized);
            }
        }
        return installed;
    }

    private static ToolCompatibilityCohortModelIdentity resolve(
            int position,
            ToolCompatibilityCohortModelIdentity.Role role,
            String requestedTag,
            Map<String, ToolCompatibilityCohortInventoryModel> installed,
            Map<String, String> selectedDigestOwners
    ) {
        String normalizedRequested = normalizeModelTag(requestedTag);
        ToolCompatibilityCohortInventoryModel selected = installed.get(normalizedRequested);
        if (selected == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Requested cohort model is not installed: " + normalizedRequested);
        }
        if (selected.executionLocation()
                != ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Cohort models must execute locally: " + normalizedRequested);
        }

        List<String> aliases = installed.entrySet().stream()
                .filter(entry -> selected.digest().equals(entry.getValue().digest()))
                .map(Map.Entry::getKey)
                .filter(tag -> !tag.equals(normalizedRequested))
                .toList();
        if (!aliases.isEmpty()) {
            if (normalizedRequested.endsWith(":latest")
                    && aliases.stream().anyMatch(tag -> !tag.endsWith(":latest"))) {
                throw new ToolCompatibilityProtocolIntegrityException(
                        "Mutable :latest alias cannot be locked while a versioned alias exists: "
                                + normalizedRequested);
            }
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Installed Ollama inventory contains duplicate aliases for selected digest: "
                            + normalizedRequested);
        }

        String previousOwner = selectedDigestOwners.putIfAbsent(
                selected.digest(), normalizedRequested);
        if (previousOwner != null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Proposed cohort contains duplicate model bytes for "
                            + previousOwner + " and " + normalizedRequested);
        }
        return new ToolCompatibilityCohortModelIdentity(
                position,
                role,
                requestedTag,
                normalizedRequested,
                selected.digest(),
                selected.metadata());
    }

    static String normalizeModelTag(String model) {
        String normalized = requireModelTag(model, "model");
        return normalized.contains(":") ? normalized : normalized + ":latest";
    }

    record Input(
            Path projectDirectory,
            String ollamaBaseUrl,
            List<String> peerModels,
            String referenceModel,
            String outputDirectory
    ) {

        Input {
            peerModels = List.copyOf(peerModels == null ? List.of() : peerModels);
        }
    }

    record Prepared(
            Path outputDirectory,
            String ollamaRuntimeVersion,
            List<ToolCompatibilityCohortModelIdentity> peers,
            ToolCompatibilityCohortModelIdentity reference
    ) {

        Prepared {
            if (outputDirectory == null
                    || ollamaRuntimeVersion == null
                    || ollamaRuntimeVersion.isBlank()
                    || reference == null) {
                throw new IllegalArgumentException("Prepared cohort preflight is incomplete");
            }
            peers = List.copyOf(peers == null ? List.of() : peers);
            if (peers.isEmpty()) {
                throw new IllegalArgumentException("Prepared cohort must contain peers");
            }
            if (peers.stream().anyMatch(model ->
                    model.role() != ToolCompatibilityCohortModelIdentity.Role.PEER)
                    || reference.role() != ToolCompatibilityCohortModelIdentity.Role.REFERENCE) {
                throw new IllegalArgumentException("Prepared cohort roles are inconsistent");
            }
        }

        List<ToolCompatibilityCohortModelIdentity> orderedModels() {
            List<ToolCompatibilityCohortModelIdentity> ordered = new ArrayList<>(peers);
            ordered.add(reference);
            return List.copyOf(ordered);
        }
    }

    @FunctionalInterface
    interface InventorySource {
        ToolCompatibilityCohortInventory snapshot();
    }
}
