package com.setaccio.lab.toolcompat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.setaccio.lab.chat.OllamaChatModelFactory;
import com.setaccio.lab.evidence.EvidenceIntegrity;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Read-only Ollama API adapter for the approved cohort preflight. */
final class ToolCompatibilityCohortOllamaInventorySource
        implements ToolCompatibilityCohortPreflight.InventorySource {

    private final ReadClient client;
    private final Set<String> selectedTags;

    ToolCompatibilityCohortOllamaInventorySource(ReadClient client) {
        this.client = Objects.requireNonNull(client, "read client must not be null");
        this.selectedTags = ToolCompatibilityCohortLock.orderedModels().stream()
                .map(ToolCompatibilityCohortLock.ApprovedModel::installedTag)
                .map(ToolCompatibilityCohortPreflight::normalizeModelTag)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static ToolCompatibilityCohortOllamaInventorySource live(String baseUrl) {
        OllamaChatModelFactory.requireLoopbackBaseUrl(baseUrl);
        return new ToolCompatibilityCohortOllamaInventorySource(
                new RestOllamaReadClient(baseUrl, ToolCompatibilityProtocol.ROW_TIMEOUT));
    }

    ReadClient client() {
        return client;
    }

    @Override
    public ToolCompatibilityCohortInventory snapshot() {
        String runtimeVersion = client.runtimeVersion();
        List<ListedModel> listedModels = requireList(client.listModels());
        List<ToolCompatibilityCohortInventoryModel> inventory = new ArrayList<>();
        for (ListedModel listed : listedModels) {
            String tag = listedTag(listed);
            String normalizedTag = ToolCompatibilityCohortPreflight.normalizeModelTag(tag);
            boolean selected = selectedTags.contains(normalizedTag);
            boolean listedRemote = isRemote(listed.remoteModel(), listed.remoteHost());
            if (!isFullDigest(listed.digest())) {
                if (selected || !listedRemote) {
                    throw new ToolCompatibilityProtocolIntegrityException(
                            "Local Ollama inventory contains an unavailable full digest: " + tag);
                }
                continue;
            }

            ShownModel shown = selected && !listedRemote
                    ? requireShown(client.showModel(tag), tag)
                    : null;
            boolean remote = listedRemote || (shown != null
                    && isRemote(shown.remoteModel(), shown.remoteHost()));
            ToolCompatibilityCohortModelMetadata metadata = shown == null
                    ? ToolCompatibilityCohortModelMetadata.unavailable()
                    : metadata(tag, listed, shown);
            inventory.add(new ToolCompatibilityCohortInventoryModel(
                    tag,
                    listed.digest(),
                    remote
                            ? ToolCompatibilityCohortInventoryModel.ExecutionLocation.REMOTE
                            : ToolCompatibilityCohortInventoryModel.ExecutionLocation.LOCAL,
                    remote
                            ? ToolCompatibilityCohortSeedSemantics.UNSUPPORTED
                            : ToolCompatibilityCohortSeedSemantics.SUPPORTED,
                    metadata));
        }
        return new ToolCompatibilityCohortInventory(runtimeVersion, inventory);
    }

    static void requireIdentityStillInstalled(
            ReadClient client,
            ToolCompatibilityCohortModelIdentity identity
    ) {
        if (client == null || identity == null) {
            throw new IllegalArgumentException("read client and cohort identity are required");
        }
        List<ListedModel> listedModels = requireList(client.listModels());
        String normalized = ToolCompatibilityCohortPreflight.normalizeModelTag(
                identity.effectiveInstalledTag());
        List<ListedModel> matchingTag = listedModels.stream()
                .filter(model -> normalized.equals(ToolCompatibilityCohortPreflight.normalizeModelTag(
                        listedTag(model))))
                .toList();
        if (matchingTag.size() != 1) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Approved cohort tag is missing or duplicated during execution: " + normalized);
        }
        ListedModel installed = matchingTag.getFirst();
        if (isRemote(installed.remoteModel(), installed.remoteHost())
                || !identity.digest().equals(installed.digest())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Approved cohort model location or digest drifted during execution: " + normalized);
        }
        boolean duplicateAlias = listedModels.stream()
                .filter(model -> identity.digest().equals(model.digest()))
                .map(ToolCompatibilityCohortOllamaInventorySource::listedTag)
                .map(ToolCompatibilityCohortPreflight::normalizeModelTag)
                .anyMatch(tag -> !normalized.equals(tag));
        if (duplicateAlias) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "A duplicate alias appeared for an approved cohort digest: " + normalized);
        }
    }

    private static ToolCompatibilityCohortModelMetadata metadata(
            String tag,
            ListedModel listed,
            ShownModel shown
    ) {
        ModelDetails details = shown.details() == null ? listed.details() : shown.details();
        List<String> capabilities = shown.capabilities() == null
                ? listed.capabilities()
                : shown.capabilities();
        String architecture = textValue(shown.modelInfo(), "general.architecture");
        return new ToolCompatibilityCohortModelMetadata(
                listed.size() == null || listed.size() < 0
                        ? ToolCompatibilityMetadataField.unavailable()
                        : available(Long.toString(listed.size())),
                architecture == null
                        ? ToolCompatibilityMetadataField.unavailable()
                        : available("ollama-show architecture=" + architecture),
                artifactFormat(tag, details),
                textField(details == null ? null : details.quantizationLevel()),
                fingerprint(shown.template()),
                fingerprint(shown.system()),
                available(hasCapability(capabilities, "tools")
                        ? "tools-advertised"
                        : "tools-not-advertised"),
                available((hasCapability(capabilities, "thinking")
                        ? "capability-advertised"
                        : "capability-not-advertised")
                        + "; default/effective-mode=unavailable"));
    }

    private static ToolCompatibilityMetadataField artifactFormat(
            String tag,
            ModelDetails details
    ) {
        if (details == null || details.format() == null || details.format().isBlank()) {
            return ToolCompatibilityMetadataField.unavailable();
        }
        String format = details.format().strip();
        String normalized = format.toLowerCase(Locale.ROOT);
        if ("gguf".equals(normalized)) {
            return available("GGUF via Ollama");
        }
        if ("safetensors".equals(normalized)
                && tag.toLowerCase(Locale.ROOT).contains("mlx")) {
            return available("safetensors/MLX via Ollama");
        }
        return available(format + " via Ollama");
    }

    private static ToolCompatibilityMetadataField fingerprint(String value) {
        String exposed = value == null ? "" : value;
        return available("sha256:" + EvidenceIntegrity.sha256(
                exposed.getBytes(StandardCharsets.UTF_8)));
    }

    private static ToolCompatibilityMetadataField textField(String value) {
        return value == null || value.isBlank()
                ? ToolCompatibilityMetadataField.unavailable()
                : available(value.strip());
    }

    private static ToolCompatibilityMetadataField available(String value) {
        return ToolCompatibilityMetadataField.available(value);
    }

    private static String textValue(Map<String, Object> values, String key) {
        if (values == null) {
            return null;
        }
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

    private static boolean hasCapability(List<String> capabilities, String expected) {
        return capabilities != null && capabilities.stream()
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }

    private static String listedTag(ListedModel model) {
        if (model == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Local Ollama inventory contains a null model entry");
        }
        String tag = model.model() == null || model.model().isBlank()
                ? model.name()
                : model.model();
        if (tag == null || tag.isBlank() || !tag.equals(tag.strip())) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Local Ollama inventory contains an unavailable model tag");
        }
        return tag;
    }

    private static List<ListedModel> requireList(List<ListedModel> models) {
        if (models == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Local Ollama inventory response contains no model list");
        }
        return List.copyOf(models);
    }

    private static ShownModel requireShown(ShownModel model, String tag) {
        if (model == null) {
            throw new ToolCompatibilityProtocolIntegrityException(
                    "Ollama show returned no metadata for selected model: " + tag);
        }
        return model;
    }

    private static boolean isRemote(String remoteModel, String remoteHost) {
        return (remoteModel != null && !remoteModel.isBlank())
                || (remoteHost != null && !remoteHost.isBlank());
    }

    private static boolean isFullDigest(String digest) {
        return digest != null && digest.matches("[0-9a-f]{64}");
    }

    interface ReadClient {
        String runtimeVersion();

        List<ListedModel> listModels();

        ShownModel showModel(String model);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListedModel(
            String name,
            String model,
            @JsonProperty("remote_model") String remoteModel,
            @JsonProperty("remote_host") String remoteHost,
            Long size,
            String digest,
            ModelDetails details,
            List<String> capabilities
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShownModel(
            String template,
            String system,
            @JsonProperty("remote_model") String remoteModel,
            @JsonProperty("remote_host") String remoteHost,
            ModelDetails details,
            @JsonProperty("model_info") Map<String, Object> modelInfo,
            List<String> capabilities
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelDetails(
            String format,
            @JsonProperty("quantization_level") String quantizationLevel
    ) {}

    private static final class RestOllamaReadClient implements ReadClient {

        private final RestClient restClient;

        private RestOllamaReadClient(String baseUrl, Duration timeout) {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build();
            JdkClientHttpRequestFactory requestFactory =
                    new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(timeout);
            restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory)
                    .build();
        }

        @Override
        public String runtimeVersion() {
            try {
                VersionResponse response = restClient.get()
                        .uri("/api/version")
                        .retrieve()
                        .body(VersionResponse.class);
                return response == null ? null : response.version();
            } catch (RuntimeException exception) {
                throw readFailure("runtime version", exception);
            }
        }

        @Override
        public List<ListedModel> listModels() {
            try {
                TagsResponse response = restClient.get()
                        .uri("/api/tags")
                        .retrieve()
                        .body(TagsResponse.class);
                return response == null ? null : response.models();
            } catch (RuntimeException exception) {
                throw readFailure("installed model list", exception);
            }
        }

        @Override
        public ShownModel showModel(String model) {
            try {
                return restClient.post()
                        .uri("/api/show")
                        .body(new ShowRequest(model))
                        .retrieve()
                        .body(ShownModel.class);
            } catch (RuntimeException exception) {
                throw readFailure("metadata for " + model, exception);
            }
        }

        private static ToolCompatibilityProtocolIntegrityException readFailure(
                String subject,
                RuntimeException cause
        ) {
            return new ToolCompatibilityProtocolIntegrityException(
                    "Failed to read local Ollama " + subject, cause);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VersionResponse(String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TagsResponse(List<ListedModel> models) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShowRequest(String model) {}

}
