package com.setaccio.lab.service;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void catalogKeepsVersionOneAsTheDefaultAndProvidesTheBoundedVersionTwoContract() {
        VisionPromptDefinition version1 = new VisionPromptDefinition();
        VisionPromptCatalog catalog = new VisionPromptCatalog(version1);

        VisionPromptDefinition version2 = catalog.require("2");

        assertThat(catalog.require("1")).isSameAs(version1);
        assertThat(version2.id()).isEqualTo(version1.id());
        assertThat(version2.version()).isEqualTo("2");
        assertThat(version2.requiredSections()).containsExactlyElementsOf(version1.requiredSections());
        assertThat(version2.text()).contains(
                "Do not infer an exact location,",
                "identity, event, or time",
                "say \"unknown\"");
        assertThat(version2.sha256()).isEqualTo("2b3d1edc36b12a72b429f67633f2fce76f7f413e1175d144f156c89794e38972");
    }

    @Test
    void catalogRejectsUnknownPromptVersionsClearly() {
        VisionPromptCatalog catalog = new VisionPromptCatalog(new VisionPromptDefinition());

        assertThatThrownBy(() -> catalog.require("99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported vision prompt version '99'; supported versions: 1, 2");
        assertThatThrownBy(() -> catalog.require(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Vision prompt version is required");
    }
}
