package com.setaccio.lab.chat;

import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public final class OllamaChatModelFactory {

    private final OllamaApiFactory ollamaApiFactory;

    public OllamaChatModelFactory() {
        this(OllamaChatModelFactory::createOllamaApi);
    }

    OllamaChatModelFactory(OllamaApiFactory ollamaApiFactory) {
        this.ollamaApiFactory = Objects.requireNonNull(ollamaApiFactory, "ollamaApiFactory must not be null");
    }

    public ChatModel create(
            String baseUrl,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (settings.seed() == null) {
            throw new IllegalArgumentException("seed must be explicit for the Ollama chat adapter");
        }
        OllamaApi ollamaApi = ollamaApiFactory.create(baseUrl, settings.requestTimeout());
        return create(ollamaApi, modelIdentity, settings);
    }

    public ChatModel create(
            OllamaApi ollamaApi,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        return create(ollamaApi, modelIdentity, settings, ChatReasoningPolicy.PROVIDER_DEFAULT);
    }

    public ChatModel create(
            OllamaApi ollamaApi,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings,
            ChatReasoningPolicy reasoningPolicy
    ) {
        Objects.requireNonNull(modelIdentity, "modelIdentity must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (settings.seed() == null) {
            throw new IllegalArgumentException("seed must be explicit for the Ollama chat adapter");
        }
        OllamaChatOptions options = OllamaReasoningOptions.apply(
                        OllamaChatOptions.builder()
                                .model(modelIdentity.requestedModel())
                                .temperature(settings.temperature())
                                .seed(settings.seed())
                                .numPredict(settings.maxOutputTokens()),
                        reasoningPolicy)
                .build();
        return createNoPullModel(ollamaApi, options, settings.requestTimeout(), settings.maxAttempts());
    }

    public ChatInvocation createInvocation(
            String baseUrl,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        return new OllamaChatInvocation(
                create(baseUrl, modelIdentity, settings),
                modelIdentity,
                settings);
    }

    public ChatInvocation createInvocation(
            OllamaApi ollamaApi,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings
    ) {
        return createInvocation(ollamaApi, modelIdentity, settings, ChatReasoningPolicy.PROVIDER_DEFAULT);
    }

    public ChatInvocation createInvocation(
            OllamaApi ollamaApi,
            OllamaChatModelIdentity modelIdentity,
            ChatGenerationSettings settings,
            ChatReasoningPolicy reasoningPolicy
    ) {
        return new OllamaChatInvocation(
                create(ollamaApi, modelIdentity, settings, reasoningPolicy),
                modelIdentity,
                settings,
                reasoningPolicy);
    }

    public OllamaApi createApi(String baseUrl, Duration timeout) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return ollamaApiFactory.create(baseUrl, timeout);
    }

    public static ChatModel createNoPullModel(
            OllamaApi ollamaApi,
            OllamaChatOptions options,
            Duration timeout,
            int maxAttempts
    ) {
        Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly 1 for controlled Ollama chat");
        }

        RetryTemplate oneAttempt = new RetryTemplate(RetryPolicy.withMaxRetries(0));
        ModelManagementOptions modelManagement = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .additionalModels(List.of())
                .timeout(timeout)
                .maxRetries(0)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(options)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(modelManagement)
                .retryTemplate(oneAttempt)
                .build();
    }

    private static OllamaApi createOllamaApi(String baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .webClientBuilder(WebClient.builder())
                .build();
    }

    public static void requireLoopbackBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (!baseUrl.equals(baseUrl.strip())) {
            throw new IllegalArgumentException("baseUrl must not have surrounding whitespace");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("baseUrl must be a valid loopback HTTP URL", exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        if (scheme == null || host == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || !isLoopbackHost(host)) {
            throw new IllegalArgumentException("baseUrl must be a loopback HTTP URL");
        }
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.equals("localhost")
                || normalized.equals("::1")
                || isIpv4Loopback(normalized);
    }

    private static boolean isIpv4Loopback(String host) {
        if (!host.matches("127(?:\\.\\d{1,3}){3}")) {
            return false;
        }
        for (String octet : host.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    interface OllamaApiFactory {
        OllamaApi create(String baseUrl, Duration timeout);
    }
}
