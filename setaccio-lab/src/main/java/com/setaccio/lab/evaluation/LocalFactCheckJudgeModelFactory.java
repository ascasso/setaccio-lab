package com.setaccio.lab.evaluation;

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
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

public final class LocalFactCheckJudgeModelFactory {

    private final OllamaApiFactory ollamaApiFactory;

    public LocalFactCheckJudgeModelFactory() {
        this(LocalFactCheckJudgeModelFactory::createOllamaApi);
    }

    LocalFactCheckJudgeModelFactory(OllamaApiFactory ollamaApiFactory) {
        this.ollamaApiFactory = Objects.requireNonNull(ollamaApiFactory, "ollamaApiFactory must not be null");
    }

    public ChatModel create(String baseUrl, LocalFactCheckJudgeSettings settings) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(settings, "settings must not be null");
        return create(ollamaApiFactory.create(baseUrl, settings.timeout()), settings);
    }

    public OllamaApi createApi(String baseUrl, Duration timeout) {
        requireLoopbackBaseUrl(baseUrl);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return ollamaApiFactory.create(baseUrl, timeout);
    }

    ChatModel create(OllamaApi ollamaApi, LocalFactCheckJudgeSettings settings) {
        Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        RetryTemplate oneAttempt = new RetryTemplate(RetryPolicy.withMaxRetries(settings.maxAttempts() - 1L));
        ModelManagementOptions modelManagement = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .additionalModels(List.of())
                .timeout(settings.timeout())
                .maxRetries(0)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(settings.ollamaOptions())
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
