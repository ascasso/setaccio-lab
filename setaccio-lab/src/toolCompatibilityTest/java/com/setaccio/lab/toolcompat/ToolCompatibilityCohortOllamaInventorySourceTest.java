package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityCohortOllamaInventorySourceTest {

    private static final String REFERENCE = "qwen3.8:27b-mlx";
    private static final String REFERENCE_DIGEST =
            "5642e97495e1a088883805981563dcdc4a040c2f53388b7a41d1f24d3622cf7e";

    @Test
    void mapsTheOllamaSnakeCaseWireFieldsUsedByTheReadOnlyAdapter() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();

        ToolCompatibilityCohortOllamaInventorySource.ListedModel listed = mapper.readValue(
                """
                {"name":"remote:cloud","model":"remote:cloud","remote_model":"upstream",
                 "remote_host":"https://ollama.com","size":0,"digest":"%s",
                 "details":{"format":"safetensors","quantization_level":"nvfp4"}}
                """.formatted("a".repeat(64)),
                ToolCompatibilityCohortOllamaInventorySource.ListedModel.class);
        ToolCompatibilityCohortOllamaInventorySource.ShownModel shown = mapper.readValue(
                """
                {"template":"{{ .Prompt }}","model_info":{"general.architecture":"qwen3_5"},
                 "capabilities":["tools","thinking"]}
                """,
                ToolCompatibilityCohortOllamaInventorySource.ShownModel.class);

        assertThat(listed.remoteModel()).isEqualTo("upstream");
        assertThat(listed.remoteHost()).isEqualTo("https://ollama.com");
        assertThat(listed.details().quantizationLevel()).isEqualTo("nvfp4");
        assertThat(shown.modelInfo()).containsEntry("general.architecture", "qwen3_5");
        assertThat(shown.capabilities()).containsExactly("tools", "thinking");
    }

    @Test
    void mapsReadOnlyApiMetadataWithoutInventingUnavailableValues() {
        FakeReadClient client = new FakeReadClient();
        client.models.add(listed(
                REFERENCE,
                REFERENCE_DIGEST,
                18_174_721_847L,
                null,
                null,
                new ToolCompatibilityCohortOllamaInventorySource.ModelDetails(
                        "safetensors", "nvfp4"),
                List.of("completion", "tools", "thinking")));
        client.models.add(listed(
                "unselected:1b",
                "a".repeat(64),
                1000L,
                null,
                null,
                null,
                null));
        client.models.add(listed(
                "cloud-model:cloud",
                "b".repeat(64),
                0L,
                "upstream-model",
                "https://ollama.com",
                null,
                null));
        client.shown.put(REFERENCE, new ToolCompatibilityCohortOllamaInventorySource.ShownModel(
                "{{ .Prompt }}",
                null,
                null,
                null,
                new ToolCompatibilityCohortOllamaInventorySource.ModelDetails(
                        "safetensors", "nvfp4"),
                Map.of("general.architecture", "qwen3_5"),
                List.of("completion", "tools", "thinking")));

        ToolCompatibilityCohortInventory inventory =
                new ToolCompatibilityCohortOllamaInventorySource(client).snapshot();

        assertThat(inventory.ollamaRuntimeVersion()).isEqualTo("0.32.15");
        assertThat(client.showRequests).containsExactly(REFERENCE);
        ToolCompatibilityCohortInventoryModel reference = inventory.models().getFirst();
        assertThat(reference.executionLocation())
                .isEqualTo(ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL);
        assertThat(reference.seedSemantics())
                .isEqualTo(ToolCompatibilityCohortSeedSemantics.SUPPORTED);
        assertThat(reference.metadata().sizeBytes().value()).isEqualTo("18174721847");
        assertThat(reference.metadata().familyProvenance().value())
                .isEqualTo("ollama-show architecture=qwen3_5");
        assertThat(reference.metadata().artifactRuntimeFormat().value())
                .isEqualTo("safetensors/MLX via Ollama");
        assertThat(reference.metadata().quantizationOrPrecision().value())
                .isEqualTo("nvfp4");
        assertThat(reference.metadata().templateFingerprint().value()).isEqualTo(
                "sha256:b507b9c2f6ca642bffcd06665ea7c91f235fd32daeefdf875a0f938db05fb315");
        assertThat(reference.metadata().defaultSystemPromptFingerprint().value()).isEqualTo(
                "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(reference.metadata().toolCapability().value()).isEqualTo("tools-advertised");
        assertThat(reference.metadata().thinkingMode().value())
                .isEqualTo("capability-advertised; default/effective-mode=unavailable");

        ToolCompatibilityCohortInventoryModel unselected = inventory.models().get(1);
        assertThat(unselected.metadata())
                .isEqualTo(ToolCompatibilityCohortModelMetadata.unavailable());
        ToolCompatibilityCohortInventoryModel remote = inventory.models().get(2);
        assertThat(remote.executionLocation())
                .isEqualTo(ToolCompatibilityCohortInventoryModel.ExecutionLocation.REMOTE);
        assertThat(remote.seedSemantics())
                .isEqualTo(ToolCompatibilityCohortSeedSemantics.UNSUPPORTED);
    }

    @Test
    void recheckRejectsDigestDriftAndNewDuplicateAliases() {
        FakeReadClient client = new FakeReadClient();
        client.models.add(listed(
                "fixture:1b",
                "a".repeat(64),
                1000L,
                null,
                null,
                null,
                List.of("tools")));
        ToolCompatibilityCohortModelIdentity identity = identity("fixture:1b", "a".repeat(64));

        ToolCompatibilityCohortOllamaInventorySource.requireIdentityStillInstalled(
                client, identity);

        client.models.set(0, listed(
                "fixture:1b",
                "c".repeat(64),
                1000L,
                null,
                null,
                null,
                List.of("tools")));
        assertThatThrownBy(() ->
                ToolCompatibilityCohortOllamaInventorySource.requireIdentityStillInstalled(
                        client, identity))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("digest drifted");

        client.models.set(0, listed(
                "fixture:1b",
                "a".repeat(64),
                1000L,
                null,
                null,
                null,
                List.of("tools")));
        client.models.add(listed(
                "fixture-alias:1b",
                "a".repeat(64),
                1000L,
                null,
                null,
                null,
                List.of("tools")));
        assertThatThrownBy(() ->
                ToolCompatibilityCohortOllamaInventorySource.requireIdentityStillInstalled(
                        client, identity))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("duplicate alias");
    }

    private static ToolCompatibilityCohortOllamaInventorySource.ListedModel listed(
            String tag,
            String digest,
            Long size,
            String remoteModel,
            String remoteHost,
            ToolCompatibilityCohortOllamaInventorySource.ModelDetails details,
            List<String> capabilities
    ) {
        return new ToolCompatibilityCohortOllamaInventorySource.ListedModel(
                tag,
                tag,
                remoteModel,
                remoteHost,
                size,
                digest,
                details,
                capabilities);
    }

    private static ToolCompatibilityCohortModelIdentity identity(String tag, String digest) {
        return new ToolCompatibilityCohortModelIdentity(
                1,
                ToolCompatibilityCohortModelIdentity.Role.PEER,
                tag,
                tag,
                digest,
                ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                ToolCompatibilityCohortModelMetadata.unavailable());
    }

    private static final class FakeReadClient
            implements ToolCompatibilityCohortOllamaInventorySource.ReadClient {

        private final List<ToolCompatibilityCohortOllamaInventorySource.ListedModel> models =
                new ArrayList<>();
        private final Map<String, ToolCompatibilityCohortOllamaInventorySource.ShownModel> shown =
                new LinkedHashMap<>();
        private final List<String> showRequests = new ArrayList<>();

        @Override
        public String runtimeVersion() {
            return "0.32.15";
        }

        @Override
        public List<ToolCompatibilityCohortOllamaInventorySource.ListedModel> listModels() {
            return List.copyOf(models);
        }

        @Override
        public ToolCompatibilityCohortOllamaInventorySource.ShownModel showModel(String model) {
            showRequests.add(model);
            return shown.get(model);
        }
    }
}
