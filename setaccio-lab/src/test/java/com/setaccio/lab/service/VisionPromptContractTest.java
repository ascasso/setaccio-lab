package com.setaccio.lab.service;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionPromptContractTest {

    @Test
    void trackedPromptHasStableIdentityDigestAndRequiredSections() {
        VisionPromptDefinition prompt = new VisionPromptDefinition();

        assertThat(prompt.id()).isEqualTo("vision-image-analysis");
        assertThat(prompt.version()).isEqualTo("1");
        assertThat(prompt.sha256())
                .isEqualTo("4502b9f76cfd1b73b074970bafecf91f275780c18c2f508f8e44e041808872a0");
        assertThat(prompt.requiredSections()).containsExactly(
                "Primary Category",
                "Subject Matter",
                "Scene Description",
                "Visual Elements",
                "Context or Likely Setting",
                "Quality Assessment",
                "Potential File-Management Keywords");
        assertThat(prompt.text())
                .contains("Describe only details supported by the image")
                .doesNotContain("/Users/", "hostname", "Setaccio product");
    }

    @Test
    void structuralChecksAreDeterministicAndSectionSpecific() {
        VisionPromptDefinition prompt = new VisionPromptDefinition();
        VisionOutputStructureEvaluator evaluator = new VisionOutputStructureEvaluator();
        String output = """
                ## Primary Category
                Photograph
                ## Subject Matter
                Unknown
                ## Scene Description
                Unknown
                ## Visual Elements
                Unknown
                ## Context or Likely Setting
                Unknown
                ## Quality Assessment
                Unknown
                ## Potential File-Management Keywords
                photo
                """;

        var complete = evaluator.evaluate(output, prompt.requiredSections());
        var incomplete = evaluator.evaluate(output.replace("## Quality Assessment", "Quality Assessment"),
                prompt.requiredSections());

        assertThat(evaluator.complete(complete)).isTrue();
        assertThat(incomplete)
                .filteredOn(check -> check.section().equals("Quality Assessment"))
                .singleElement()
                .satisfies(check -> assertThat(check.present()).isFalse());
        assertThat(evaluator.complete(incomplete)).isFalse();
        assertThat(evaluator.evaluate(null, List.of("Primary Category")))
                .singleElement()
                .satisfies(check -> assertThat(check.present()).isFalse());
    }
}
