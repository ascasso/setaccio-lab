package com.setaccio.lab.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VisionCorpusTemplateContractTest {

    private static final String TEMPLATE = "/vision-corpus/cases.template.json";
    private static final List<String> CASE_IDS = List.of(
            "vision-single-subject",
            "vision-complex-scene",
            "vision-text-heavy",
            "vision-low-quality",
            "vision-ambiguous",
            "vision-file-organization"
    );
    private static final Set<String> CASE_FIELDS = Set.of(
            "caseId",
            "imageFile",
            "mimeType",
            "blake3",
            "referenceObservation",
            "expectedConcepts",
            "unsupportedDetails",
            "limitations",
            "privacyReview"
    );
    private static final Set<String> PRIVACY_FIELDS = Set.of(
            "sensitiveContentReviewed",
            "exifGpsReviewed",
            "approvedForTracking"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void definesSixStablePublicSafeCaseContracts() throws IOException {
        JsonNode root = readTemplate();

        assertThat(root.path("corpusVersion").asInt()).isEqualTo(1);
        assertThat(root.path("cases").isArray()).isTrue();
        assertThat(root.path("cases")).hasSize(CASE_IDS.size());

        Set<String> observedIds = new HashSet<>();
        root.path("cases").forEach(caseNode -> {
            String caseId = caseNode.path("caseId").asText();
            observedIds.add(caseId);

            assertThat(fieldNames(caseNode)).containsExactlyInAnyOrderElementsOf(CASE_FIELDS);
            assertThat(caseId).matches("[a-z0-9]+(?:-[a-z0-9]+)*");
            assertThat(caseNode.path("imageFile").asText())
                    .matches("images/" + caseId + "\\.(?:jpg|jpeg|png|gif|webp)");
            assertThat(caseNode.path("mimeType").asText()).startsWith("image/");
            assertThat(caseNode.path("blake3").asText()).contains("BLAKE3");
            assertThat(caseNode.path("referenceObservation").asText()).isNotBlank();
            assertThat(caseNode.path("expectedConcepts")).isNotEmpty();
            assertThat(caseNode.path("unsupportedDetails")).isNotEmpty();
            assertThat(caseNode.path("limitations")).isNotEmpty();

            JsonNode privacyReview = caseNode.path("privacyReview");
            assertThat(fieldNames(privacyReview)).containsExactlyInAnyOrderElementsOf(PRIVACY_FIELDS);
            assertThat(privacyReview.path("sensitiveContentReviewed").asBoolean()).isFalse();
            assertThat(privacyReview.path("exifGpsReviewed").asBoolean()).isFalse();
            assertThat(privacyReview.path("approvedForTracking").asBoolean()).isFalse();
        });

        assertThat(observedIds).containsExactlyInAnyOrderElementsOf(CASE_IDS);
    }

    @Test
    void containsNoOriginalFilenameOrAbsolutePathFields() throws IOException {
        String json = readTemplate().toString();

        assertThat(json)
                .doesNotContain("originalFilename")
                .doesNotContain("absolutePath")
                .doesNotContain("sourcePath")
                .doesNotContain("/Users/")
                .doesNotContain("\\\\");
    }

    private JsonNode readTemplate() throws IOException {
        try (InputStream input = VisionCorpusTemplateContractTest.class.getResourceAsStream(TEMPLATE)) {
            assertThat(input).as("tracked vision corpus template").isNotNull();
            return objectMapper.readTree(input);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
