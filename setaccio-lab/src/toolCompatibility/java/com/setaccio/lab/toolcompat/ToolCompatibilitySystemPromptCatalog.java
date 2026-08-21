package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ToolCompatibilitySystemPromptCatalog {

    static final String RESOURCE = "tool-compatibility/system-prompt-catalog-v1.json";
    static final String ID = "tool-system-prompt-catalog";
    static final int VERSION = 1;
    static final String SHA256 = "d55122cd60ac056c8f5cc3e35a2661e497bc1468cff6a593f4cf666b1eb7e06d";
    static final List<String> CONDITION_IDS = List.of(
            ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
            ToolCompatibilitySystemPromptIdentity.DISCIPLINE_ID);

    private static final Set<String> ROOT_FIELDS = Set.of("id", "version", "prompts");
    private static final Set<String> PROMPT_FIELDS = Set.of("id", "version", "sha256", "text", "present");
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final String id;
    private final int version;
    private final String sha256;
    private final List<ToolCompatibilitySystemPromptIdentity> prompts;

    private ToolCompatibilitySystemPromptCatalog(
            String id,
            int version,
            String sha256,
            List<ToolCompatibilitySystemPromptIdentity> prompts
    ) {
        this.id = requireText(id, "id");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a full lowercase SHA-256 digest");
        }
        this.sha256 = sha256;
        this.prompts = List.copyOf(prompts == null ? List.of() : prompts);
        if (this.prompts.isEmpty()) {
            throw new IllegalArgumentException("prompts must not be empty");
        }
        Set<String> promptIds = new HashSet<>();
        for (ToolCompatibilitySystemPromptIdentity prompt : this.prompts) {
            if (!promptIds.add(prompt.id())) {
                throw new IllegalArgumentException("system-prompt catalog must not contain duplicate prompt IDs");
            }
        }
    }

    static ToolCompatibilitySystemPromptCatalog loadLocked() {
        return parseLocked(readResource());
    }

    static ToolCompatibilitySystemPromptCatalog parseLocked(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog bytes are missing");
        }
        String sha256 = EvidenceIntegrity.sha256(bytes);
        if (!SHA256.equals(sha256)) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog digest drifted");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(bytes);
            requireExactFields(root, ROOT_FIELDS, "catalog");
            String id = requiredText(root.path("id"), "id");
            int version = requiredPositiveInteger(root.path("version"), "version");
            JsonNode promptNodes = root.path("prompts");
            if (!promptNodes.isArray()) {
                throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog prompts must be an array");
            }
            List<ToolCompatibilitySystemPromptIdentity> prompts = new ArrayList<>();
            for (JsonNode promptNode : promptNodes) {
                prompts.add(parsePrompt(promptNode));
            }
            ToolCompatibilitySystemPromptCatalog catalog = new ToolCompatibilitySystemPromptCatalog(
                    id, version, sha256, prompts);
            catalog.requireLocked();
            return catalog;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog is invalid", exception);
        }
    }

    String id() {
        return id;
    }

    int version() {
        return version;
    }

    String sha256() {
        return sha256;
    }

    List<ToolCompatibilitySystemPromptIdentity> prompts() {
        return prompts;
    }

    ToolCompatibilitySystemPromptIdentity untreated() {
        return requirePrompt(ToolCompatibilitySystemPromptIdentity.UNTREATED_ID);
    }

    ToolCompatibilitySystemPromptIdentity toolDiscipline() {
        return requirePrompt(ToolCompatibilitySystemPromptIdentity.DISCIPLINE_ID);
    }

    ToolCompatibilitySystemPromptIdentity requirePrompt(String promptId) {
        String requestedPromptId = requireText(promptId, "promptId");
        return prompts.stream()
                .filter(prompt -> prompt.id().equals(requestedPromptId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool compatibility system prompt: " + requestedPromptId));
    }

    private void requireLocked() {
        if (!ID.equals(id) || VERSION != version || !SHA256.equals(sha256)) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog identity drifted");
        }
        if (!CONDITION_IDS.equals(prompts.stream().map(ToolCompatibilitySystemPromptIdentity::id).toList())) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog order drifted");
        }
        untreated().requireUntreated();
        toolDiscipline().requireToolDiscipline();
    }

    private static ToolCompatibilitySystemPromptIdentity parsePrompt(JsonNode promptNode) {
        requireExactFields(promptNode, PROMPT_FIELDS, "prompt");
        return new ToolCompatibilitySystemPromptIdentity(
                requiredText(promptNode.path("id"), "prompt id"),
                requiredPositiveInteger(promptNode.path("version"), "prompt version"),
                requiredText(promptNode.path("sha256"), "prompt sha256"),
                requiredString(promptNode.path("text"), "prompt text"),
                requiredBoolean(promptNode.path("present"), "prompt present"));
    }

    private static byte[] readResource() {
        try (InputStream input = ToolCompatibilitySystemPromptCatalog.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog is missing");
            }
            return input.readAllBytes();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog could not be read", exception);
        }
    }

    private static void requireExactFields(JsonNode node, Set<String> expectedFields, String structure) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog "
                    + structure + " must be an object");
        }
        Set<String> actualFields = new HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        if (!expectedFields.equals(actualFields)) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog "
                    + structure + " fields drifted");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        return requireText(requiredString(node, field), field);
    }

    private static String requiredString(JsonNode node, String field) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog "
                    + field + " must be text");
        }
        return node.textValue();
    }

    private static int requiredPositiveInteger(JsonNode node, String field) {
        if (node == null || !node.canConvertToInt() || !node.isIntegralNumber() || node.intValue() < 1) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog "
                    + field + " must be positive");
        }
        return node.intValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException("Tracked tool compatibility system-prompt catalog "
                    + field + " must be boolean");
        }
        return node.booleanValue();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
        return value;
    }
}
