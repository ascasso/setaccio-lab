package com.setaccio.lab.toolcompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ToolCompatibilityCohortLiveSessionTest {

    @Test
    void rechecksTheExactIdentityBeforeCreatingEachNoStateControlledModel() {
        MutableReadClient client = new MutableReadClient();
        ToolCompatibilityCohortModelIdentity identity = identity();
        client.models.add(listed(identity.digest()));
        AtomicInteger factoryCalls = new AtomicInteger();
        ChatModel chatModel = mock(ChatModel.class);
        ToolCompatibilityCohortLiveSession session = new ToolCompatibilityCohortLiveSession(
                client,
                (modelIdentity, effectiveSeed) -> {
                    factoryCalls.incrementAndGet();
                    return new ToolCompatibilityCohortControlledOllamaModel(
                            chatModel, modelIdentity, effectiveSeed);
                });

        ToolCompatibilityCohortControlledOllamaModel controlled =
                session.controlledModel(identity, 42);

        assertThat(controlled.chatModel()).isSameAs(chatModel);
        assertThat(controlled.modelIdentity()).isEqualTo(identity);
        assertThat(controlled.effectiveSeed()).isEqualTo(42);
        assertThat(factoryCalls).hasValue(1);
        assertThat(client.listReads).hasValue(1);
        assertThat(session.ollamaRuntimeVersion()).isEqualTo("0.32.15");

        client.models.set(0, listed("b".repeat(64)));
        assertThatThrownBy(() -> session.controlledModel(identity, 43))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("digest drifted");
        assertThat(factoryCalls).hasValue(1);
    }

    private static ToolCompatibilityCohortModelIdentity identity() {
        return new ToolCompatibilityCohortModelIdentity(
                1,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                "fixture:1b",
                "fixture:1b",
                "a".repeat(64),
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                ToolCompatibilityCohortModelMetadata.unavailable());
    }

    private static ToolCompatibilityCohortOllamaInventorySource.ListedModel listed(
            String digest
    ) {
        return new ToolCompatibilityCohortOllamaInventorySource.ListedModel(
                "fixture:1b",
                "fixture:1b",
                null,
                null,
                1000L,
                digest,
                null,
                List.of("tools"));
    }

    private static final class MutableReadClient
            implements ToolCompatibilityCohortOllamaInventorySource.ReadClient {

        private final List<ToolCompatibilityCohortOllamaInventorySource.ListedModel> models =
                new ArrayList<>();
        private final AtomicInteger listReads = new AtomicInteger();

        @Override
        public String runtimeVersion() {
            return "0.32.15";
        }

        @Override
        public List<ToolCompatibilityCohortOllamaInventorySource.ListedModel> listModels() {
            listReads.incrementAndGet();
            return List.copyOf(models);
        }

        @Override
        public ToolCompatibilityCohortOllamaInventorySource.ShownModel showModel(String model) {
            throw new AssertionError("live-session identity rechecks must not call show or infer");
        }
    }
}
