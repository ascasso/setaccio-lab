package com.setaccio.lab.retrieval;

import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Loads and renders the tracked R5 grounded-answer prompt without changing its identity. */
final class RetrievalAnswerPromptDefinition {

    static final String PROMPT_ID = "retrieval-grounded-answer-v1";
    static final String RESOURCE = "retrieval/answer-prompt-v1.md";
    private static final String QUERY_PLACEHOLDER = "{{QUERY}}";
    private static final String DOCUMENTS_PLACEHOLDER = "{{RETRIEVED_DOCUMENTS}}";

    private final String template;
    private final String sha256;

    private RetrievalAnswerPromptDefinition(String template, String sha256) {
        this.template = template;
        this.sha256 = sha256;
    }

    static RetrievalAnswerPromptDefinition load() {
        try (InputStream input = RetrievalAnswerPromptDefinition.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Retrieval answer prompt resource is missing: " + RESOURCE);
            }
            byte[] bytes = input.readAllBytes();
            String template = new String(bytes, StandardCharsets.UTF_8);
            validateTemplate(template);
            return new RetrievalAnswerPromptDefinition(template, EvidenceIntegrity.sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load retrieval answer prompt resource", exception);
        }
    }

    RetrievalAnswerPromptContract contract() {
        return new RetrievalAnswerPromptContract(PROMPT_ID, sha256);
    }

    String render(String query, List<RetrievalEvaluationRetrievedDocument> documents) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        List<RetrievalEvaluationRetrievedDocument> copied = documents == null ? List.of() : List.copyOf(documents);
        String renderedDocuments = copied.isEmpty()
                ? "No documents were retrieved."
                : copied.stream().map(RetrievalAnswerPromptDefinition::renderDocument).reduce((left, right) -> left + "\n\n" + right)
                        .orElseThrow();
        int queryIndex = template.indexOf(QUERY_PLACEHOLDER);
        int documentsIndex = template.indexOf(DOCUMENTS_PLACEHOLDER);
        return template.substring(0, queryIndex)
                + query
                + template.substring(queryIndex + QUERY_PLACEHOLDER.length(), documentsIndex)
                + renderedDocuments
                + template.substring(documentsIndex + DOCUMENTS_PLACEHOLDER.length());
    }

    private static String renderDocument(RetrievalEvaluationRetrievedDocument document) {
        Objects.requireNonNull(document, "retrieved document must not be null");
        return "[rank=" + document.rank() + " documentId=" + document.documentId() + " sha256="
                + document.contentSha256() + "]\n" + document.content() + "\n[/retrieved-document]";
    }

    private static void validateTemplate(String template) {
        if (template == null || template.isBlank() || !template.endsWith("\n")) {
            throw new IllegalStateException("Retrieval answer prompt must be non-blank and LF-terminated.");
        }
        if (template.contains("\r") || occurrences(template, QUERY_PLACEHOLDER) != 1
                || occurrences(template, DOCUMENTS_PLACEHOLDER) != 1
                || template.indexOf(QUERY_PLACEHOLDER) > template.indexOf(DOCUMENTS_PLACEHOLDER)) {
            throw new IllegalStateException("Retrieval answer prompt placeholders are invalid.");
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
