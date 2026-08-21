package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilitySystemPromptCatalogTest {

    private static final String DISCIPLINE_TEXT = "You are a tool-using assistant.\n"
            + "\n"
            + "Use a tool when the request requires external information or an action.\n"
            + "Do not invent tool results.\n"
            + "Do not call a tool when it is unnecessary.\n"
            + "Use only the tools available to you.\n"
            + "Think silently and do not output internal reasoning or <think> tags.\n"
            + "After a tool completes, answer using only its returned result.";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void locksExactlyTwoOrderedPromptConditionsWithExactUtf8Bytes() {
        ToolCompatibilitySystemPromptCatalog catalog = ToolCompatibilityProtocol.systemPromptCatalog();

        assertThat(catalog.id()).isEqualTo("tool-system-prompt-catalog");
        assertThat(catalog.version()).isOne();
        assertThat(catalog.sha256())
                .isEqualTo("d55122cd60ac056c8f5cc3e35a2661e497bc1468cff6a593f4cf666b1eb7e06d");
        assertThat(catalog.prompts())
                .extracting(ToolCompatibilitySystemPromptIdentity::id)
                .containsExactly("tool-system-none", "tool-system-discipline");
        assertThat(catalog.untreated()).isEqualTo(ToolCompatibilitySystemPromptIdentity.untreated());

        ToolCompatibilitySystemPromptIdentity discipline = catalog.toolDiscipline();
        assertThat(discipline.version()).isOne();
        assertThat(discipline.present()).isTrue();
        assertThat(discipline.text()).isEqualTo(DISCIPLINE_TEXT);
        assertThat(discipline.text().getBytes(StandardCharsets.UTF_8))
                .containsExactly(DISCIPLINE_TEXT.getBytes(StandardCharsets.UTF_8));
        assertThat(discipline.text().getBytes(StandardCharsets.UTF_8)).hasSize(344);
        assertThat(discipline.sha256())
                .isEqualTo("fcc115e73a44ed2fcd76ba11ee0937a54d308465e16a929d781d8de27e04cd71")
                .isEqualTo(EvidenceIntegrity.sha256(DISCIPLINE_TEXT.getBytes(StandardCharsets.UTF_8)));
        discipline.requireToolDiscipline();
    }

    @Test
    void rejectsToolDisciplineIdentityDriftEvenWhenThePromptTextMatchesItsDigest() {
        ToolCompatibilitySystemPromptIdentity discipline = ToolCompatibilityProtocol.systemPromptCatalog().toolDiscipline();

        ToolCompatibilitySystemPromptIdentity versionDrift = new ToolCompatibilitySystemPromptIdentity(
                discipline.id(),
                2,
                discipline.sha256(),
                discipline.text(),
                true);
        ToolCompatibilitySystemPromptIdentity idDrift = new ToolCompatibilitySystemPromptIdentity(
                "tool-system-other",
                discipline.version(),
                discipline.sha256(),
                discipline.text(),
                true);

        assertThatIllegalArgumentException().isThrownBy(versionDrift::requireToolDiscipline);
        assertThatIllegalArgumentException().isThrownBy(idDrift::requireToolDiscipline);
    }

    @Test
    void rejectsCatalogByteTextAndConditionOrderDrift() throws Exception {
        byte[] locked = lockedCatalogBytes();
        byte[] byteDrift = Arrays.copyOf(locked, locked.length + 1);
        byteDrift[byteDrift.length - 1] = ' ';
        byte[] textDrift = mutatedCatalog(root -> ((ObjectNode) root.path("prompts").get(1))
                .put("text", "Think aloud instead."));
        byte[] orderDrift = mutatedCatalog(root -> {
            ArrayNode prompts = (ArrayNode) root.path("prompts");
            prompts.insert(0, prompts.remove(1));
        });

        assertThat(EvidenceIntegrity.sha256(locked)).isEqualTo(ToolCompatibilitySystemPromptCatalog.SHA256);
        assertThat(ToolCompatibilitySystemPromptCatalog.parseLocked(locked).sha256())
                .isEqualTo(ToolCompatibilitySystemPromptCatalog.SHA256);
        assertThatThrownBy(() -> ToolCompatibilitySystemPromptCatalog.parseLocked(byteDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");
        assertThatThrownBy(() -> ToolCompatibilitySystemPromptCatalog.parseLocked(textDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");
        assertThatThrownBy(() -> ToolCompatibilitySystemPromptCatalog.parseLocked(orderDrift))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest drifted");
    }

    private byte[] mutatedCatalog(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(lockedCatalogBytes());
        mutation.accept(root);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private static byte[] lockedCatalogBytes() throws Exception {
        try (InputStream input = ToolCompatibilitySystemPromptCatalogTest.class.getClassLoader()
                .getResourceAsStream(ToolCompatibilitySystemPromptCatalog.RESOURCE)) {
            if (input == null) {
                throw new AssertionError("Locked tool compatibility system-prompt catalog resource is missing");
            }
            return input.readAllBytes();
        }
    }
}
