package com.setaccio.lab.chat;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatInvocationContractTest {

    @Test
    void keepsProviderNeutralIdentitySeparateFromOllamaDigestSemantics() {
        OllamaChatModelIdentity identity = identity();

        assertThat(identity.providerId()).isEqualTo("ollama");
        assertThat(identity.requestedModel()).isEqualTo("gemma4:e2b");
        assertThat(identity.effectiveModel()).isEqualTo("gemma4:e2b");
        assertThat(identity.digest()).hasSize(64);

        assertThatThrownBy(() -> new OllamaChatModelIdentity(
                "ollama",
                "gemma4:e2b",
                "gemma4:e2b",
                "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("digest must be a full lowercase SHA-256 digest");
    }

    @Test
    void requiresEveryCommonOptionToBeClassifiedExactlyOnce() {
        assertThat(ChatProviderOptionSupport.supportsAll().supported())
                .containsExactlyInAnyOrder(ChatGenerationOption.values());

        assertThatThrownBy(() -> new ChatProviderOptionSupport(
                EnumSet.of(ChatGenerationOption.TEMPERATURE),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be classified exactly once");

        assertThatThrownBy(() -> new ChatProviderOptionSupport(
                EnumSet.allOf(ChatGenerationOption.class),
                Map.of(ChatGenerationOption.SEED, "not available")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be classified exactly once");
    }

    @Test
    void allowsAnUnsupportedProviderOptionWithoutInventingAValue() {
        ChatProviderOptionSupport support = new ChatProviderOptionSupport(
                EnumSet.complementOf(EnumSet.of(ChatGenerationOption.SEED)),
                Map.of(ChatGenerationOption.SEED, "provider does not expose deterministic seeds"));
        ChatGenerationSettings settings = new ChatGenerationSettings(
                0.0,
                null,
                128,
                Duration.ofSeconds(30),
                1);

        assertThat(support.supported()).doesNotContain(ChatGenerationOption.SEED);
        assertThat(support.unsupportedReasons()).containsEntry(
                ChatGenerationOption.SEED,
                "provider does not expose deterministic seeds");
        assertThat(support.statuses()).containsEntry(ChatGenerationOption.SEED, ChatProviderOptionStatus.REJECTED);
        assertThat(support.statuses()).containsEntry(ChatGenerationOption.TEMPERATURE, ChatProviderOptionStatus.SUPPORTED);
        assertThat(settings.seed()).isNull();
    }

    @Test
    void rejectsIncoherentOutcomeStates() {
        assertThatThrownBy(() -> new ChatInvocationOutcome(
                identity(),
                ChatProviderOptionSupport.supportsAll(),
                "prompt-a",
                false,
                null,
                null,
                null,
                null,
                1,
                1,
                ChatInvocationFailureCategory.NONE,
                "provider failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failed invocation has an invalid failure category");

        assertThatThrownBy(() -> new ChatInvocationOutcome(
                identity(),
                ChatProviderOptionSupport.supportsAll(),
                "prompt-a",
                true,
                "response",
                1,
                null,
                1,
                1,
                1,
                ChatInvocationFailureCategory.NONE,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("usage token counts must be all present or all absent");
    }

    static OllamaChatModelIdentity identity() {
        return new OllamaChatModelIdentity(
                "ollama",
                "gemma4:e2b",
                "gemma4:e2b",
                "7fbdbf8f5e45a75bb122155ed546e765b4d9c53a1285f62fd9f506baa1c5a47e");
    }
}
