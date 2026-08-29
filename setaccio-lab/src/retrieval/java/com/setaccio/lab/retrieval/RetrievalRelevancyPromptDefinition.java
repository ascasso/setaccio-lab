package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the tracked R6 prompt consumed by Spring AI's {@code RelevancyEvaluator}. */
final class RetrievalRelevancyPromptDefinition {

    static final String PROMPT_ID = "retrieval-relevancy-evaluator-v1";
    static final String RESOURCE = "retrieval/relevancy-evaluator-prompt-v1.md";

    private final String text;
    private final String sha256;

    private RetrievalRelevancyPromptDefinition(String text, String sha256) {
        this.text = text;
        this.sha256 = sha256;
    }

    static RetrievalRelevancyPromptDefinition load() {
        try (InputStream input = RetrievalRelevancyPromptDefinition.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Retrieval relevancy prompt resource is missing: " + RESOURCE);
            }
            byte[] bytes = input.readAllBytes();
            String text = new String(bytes, StandardCharsets.UTF_8);
            validate(text);
            return new RetrievalRelevancyPromptDefinition(text, EvidenceIntegrity.sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load retrieval relevancy prompt resource", exception);
        }
    }

    RetrievalRelevancyPromptContract contract() {
        return new RetrievalRelevancyPromptContract(PROMPT_ID, sha256);
    }

    String text() {
        return text;
    }

    private static void validate(String text) {
        if (text == null || text.isBlank() || text.contains("\r") || !text.endsWith("\n")) {
            throw new IllegalStateException("Retrieval relevancy prompt must be non-blank, LF-only, and LF-terminated.");
        }
        for (String placeholder : new String[] {"{query}", "{response}", "{context}"}) {
            if (occurrences(text, placeholder) != 1) {
                throw new IllegalStateException("Retrieval relevancy prompt must contain exactly one " + placeholder + ".");
            }
        }
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
