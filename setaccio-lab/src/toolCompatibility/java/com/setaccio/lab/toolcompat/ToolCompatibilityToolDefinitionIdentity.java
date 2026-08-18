package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.util.List;

/** Fingerprints the ordered names, descriptions, and input schemas actually exposed to the model. */
record ToolCompatibilityToolDefinitionIdentity(
        List<String> orderedToolNames,
        String sha256
) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    ToolCompatibilityToolDefinitionIdentity {
        orderedToolNames = List.copyOf(orderedToolNames == null ? List.of() : orderedToolNames);
        if (!ToolCompatibilityProtocol.caseSelection().toolNames().equals(orderedToolNames)) {
            throw new IllegalArgumentException("tool definition names must equal the canonical ordered tools");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tool definition digest must be a full lowercase SHA-256");
        }
    }

    static ToolCompatibilityToolDefinitionIdentity canonical() {
        List<DefinitionFingerprint> definitions = ToolCompatibilityCallbackCatalog.canonicalCallbacks().stream()
                .map(callback -> callback.getToolDefinition())
                .map(definition -> new DefinitionFingerprint(
                        definition.name(), definition.description(), definition.inputSchema()))
                .toList();
        try {
            return new ToolCompatibilityToolDefinitionIdentity(
                    definitions.stream().map(DefinitionFingerprint::name).toList(),
                    EvidenceIntegrity.sha256(JSON.writeValueAsBytes(definitions)));
        } catch (Exception exception) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Canonical tool definitions could not be fingerprinted", exception);
        }
    }

    private record DefinitionFingerprint(String name, String description, String inputSchema) {}
}
