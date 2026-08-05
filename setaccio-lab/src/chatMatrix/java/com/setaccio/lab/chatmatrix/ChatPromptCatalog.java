package com.setaccio.lab.chatmatrix;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatPromptCatalog {

    static final String RESOURCE = "chat-matrix/prompts-v1.json";
    static final String ID = "local-chat-prompts";
    static final String VERSION = "1";
    static final String SHA256 = "992471fa9ee4bef212412c2862151e725f080c0b4424f1903aa4f7b2c48ae675";
    static final List<String> PROMPT_IDS = List.of(
            "concise-summary",
            "classification-policy",
            "json-shape");
    private static final Map<String, String> PROMPT_SHA256 = Map.of(
            "concise-summary", "ebc7de5dfec2db66d2b118673349deb173bf127d26914acaaa982c1041627f8c",
            "classification-policy", "192f85a28601c9ae700d6d9a2b245402add14ce0e13003daf7cb66083c06914c",
            "json-shape", "90da8d5b8cf6a65a0de48761dd06b3ed074e48556c79cf77af20b0c38feaf62c");

    private final String id;
    private final String version;
    private final String sha256;
    private final List<ChatPromptCase> prompts;
    private final Map<String, ChatPromptCase> byId;

    private ChatPromptCatalog(String id, String version, String sha256, List<ChatPromptCase> prompts) {
        this.id = id;
        this.version = version;
        this.sha256 = sha256;
        this.prompts = List.copyOf(prompts);
        LinkedHashMap<String, ChatPromptCase> indexed = new LinkedHashMap<>();
        for (ChatPromptCase prompt : prompts) {
            if (indexed.putIfAbsent(prompt.id(), prompt) != null) {
                throw new IllegalArgumentException("Chat prompt catalog contains duplicate ID " + prompt.id());
            }
        }
        byId = Map.copyOf(indexed);
        requireLocked();
    }

    static ChatPromptCatalog load(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        byte[] bytes;
        try (InputStream input = ChatPromptCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalArgumentException("Tracked chat prompt catalog is missing");
            }
            bytes = input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked chat prompt catalog could not be read", exception);
        }
        String catalogSha256 = EvidenceIntegrity.sha256(bytes);
        CatalogDocument document;
        try {
            document = objectMapper.readerFor(CatalogDocument.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(bytes);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tracked chat prompt catalog is invalid", exception);
        }
        return new ChatPromptCatalog(document.id(), document.version(), catalogSha256, document.prompts());
    }

    void requireLocked() {
        if (!ID.equals(id) || !VERSION.equals(version) || !SHA256.equals(sha256)) {
            throw new IllegalArgumentException("Tracked chat prompt catalog identity drifted");
        }
        if (!PROMPT_IDS.equals(prompts.stream().map(ChatPromptCase::id).toList())) {
            throw new IllegalArgumentException("Tracked chat prompt order drifted");
        }
        for (ChatPromptCase prompt : prompts) {
            String expected = PROMPT_SHA256.get(prompt.id());
            String actual = EvidenceIntegrity.sha256(prompt.text().getBytes(StandardCharsets.UTF_8));
            if (!prompt.sha256().equals(expected) || !actual.equals(expected)) {
                throw new IllegalArgumentException("Tracked chat prompt digest drifted for " + prompt.id());
            }
        }
    }

    String id() {
        return id;
    }

    String version() {
        return version;
    }

    String sha256() {
        return sha256;
    }

    List<ChatPromptCase> prompts() {
        return prompts;
    }

    List<ChatPromptIdentity> identities() {
        return prompts.stream().map(prompt -> new ChatPromptIdentity(prompt.id(), prompt.sha256())).toList();
    }

    ChatPromptCase require(String promptId) {
        ChatPromptCase prompt = byId.get(promptId);
        if (prompt == null) {
            throw new IllegalArgumentException("Unknown locked chat prompt ID: " + promptId);
        }
        return prompt;
    }

    private record CatalogDocument(String id, String version, List<ChatPromptCase> prompts) {
        private CatalogDocument {
            prompts = prompts == null ? List.of() : List.copyOf(prompts);
        }
    }
}
