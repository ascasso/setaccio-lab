package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.model.ChatBenchmarkPrompt;
import com.setaccio.lab.service.ChatBenchmarkService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPromptCatalogTest {

    @Test
    void locksCatalogBytesPromptBytesIdsAndOrder() {
        ChatPromptCatalog catalog = ChatMatrixTestFixtures.CATALOG;

        assertThat(catalog.id()).isEqualTo(ChatPromptCatalog.ID);
        assertThat(catalog.version()).isEqualTo(ChatPromptCatalog.VERSION);
        assertThat(catalog.sha256()).isEqualTo(ChatPromptCatalog.SHA256);
        assertThat(catalog.prompts()).extracting(ChatPromptCase::id)
                .containsExactlyElementsOf(ChatPromptCatalog.PROMPT_IDS);
        assertThat(catalog.prompts()).allSatisfy(prompt ->
                assertThat(com.setaccio.lab.evidence.EvidenceIntegrity.sha256(
                        prompt.text().getBytes(StandardCharsets.UTF_8)))
                        .isEqualTo(prompt.sha256()));
    }

    @Test
    void preservesTheExistingInteractiveDefaultPromptContractWithoutMigratingIt() {
        assertThat(ChatMatrixTestFixtures.CATALOG.prompts())
                .extracting(prompt -> new ChatBenchmarkPrompt(prompt.id(), prompt.text()))
                .containsExactlyElementsOf(ChatBenchmarkService.defaultPrompts());
    }
}
