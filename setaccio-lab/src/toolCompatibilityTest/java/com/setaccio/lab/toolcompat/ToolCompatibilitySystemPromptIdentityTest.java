package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ToolCompatibilitySystemPromptIdentityTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void locksTheUntreatedBaselineToExactlyZeroUtf8Bytes() {
        ToolCompatibilitySystemPromptIdentity identity = ToolCompatibilityProtocol.systemPromptIdentity();

        assertThat(identity.id()).isEqualTo("tool-system-none");
        assertThat(identity.version()).isOne();
        assertThat(identity.sha256())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .isEqualTo(EvidenceIntegrity.sha256(new byte[0]));
        assertThat(identity.text()).isEmpty();
        assertThat(identity.text().getBytes(StandardCharsets.UTF_8)).isEmpty();
        assertThat(identity.present()).isFalse();
    }

    @Test
    void roundTripsTheExactUntreatedIdentityAsJson() throws Exception {
        ToolCompatibilitySystemPromptIdentity expected = ToolCompatibilityProtocol.systemPromptIdentity();

        byte[] json = objectMapper.writeValueAsBytes(expected);
        ToolCompatibilitySystemPromptIdentity restored = objectMapper.readValue(
                json,
                ToolCompatibilitySystemPromptIdentity.class);

        assertThat(restored).isEqualTo(expected);
        assertThat(new String(json, StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":\"tool-system-none\",\"version\":1,\"sha256\":\""
                        + ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256
                        + "\",\"text\":\"\",\"present\":false}");
        restored.requireUntreated();
    }

    @Test
    void rejectsInvalidIdentityStateAtConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilitySystemPromptIdentity(
                " ",
                1,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                "",
                false));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                0,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                "",
                false));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                1,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                null,
                false));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                1,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                "",
                true));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                1,
                "a".repeat(64),
                "",
                false));
    }

    @Test
    void rejectsEverySemanticallyValidDriftFromTheUntreatedBaseline() {
        ToolCompatibilitySystemPromptIdentity idDrift = new ToolCompatibilitySystemPromptIdentity(
                "tool-system-other",
                1,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                "",
                false);
        ToolCompatibilitySystemPromptIdentity versionDrift = new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                2,
                ToolCompatibilitySystemPromptIdentity.UNTREATED_SHA256,
                "",
                false);
        String newline = "\n";
        ToolCompatibilitySystemPromptIdentity textDrift = new ToolCompatibilitySystemPromptIdentity(
                ToolCompatibilitySystemPromptIdentity.UNTREATED_ID,
                1,
                EvidenceIntegrity.sha256(newline.getBytes(StandardCharsets.UTF_8)),
                newline,
                true);

        assertThatIllegalArgumentException().isThrownBy(idDrift::requireUntreated);
        assertThatIllegalArgumentException().isThrownBy(versionDrift::requireUntreated);
        assertThatIllegalArgumentException().isThrownBy(textDrift::requireUntreated);
    }
}
