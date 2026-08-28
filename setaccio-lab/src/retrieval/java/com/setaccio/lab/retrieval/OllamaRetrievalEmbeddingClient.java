package com.setaccio.lab.retrieval;

import com.setaccio.lab.chat.OllamaChatModelFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/** Direct Spring AI Ollama API adapter with no pull or retry behavior. */
final class OllamaRetrievalEmbeddingClient implements RetrievalEmbeddingClient {

    private final OllamaApi ollamaApi;

    OllamaRetrievalEmbeddingClient(OllamaApi ollamaApi) {
        this.ollamaApi = Objects.requireNonNull(ollamaApi, "ollamaApi must not be null");
    }

    static OllamaApi createApi(String baseUrl, Duration timeout) {
        OllamaChatModelFactory.requireLoopbackBaseUrl(baseUrl);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .webClientBuilder(WebClient.builder())
                .build();
    }

    @Override
    public EmbeddingResponse embed(RetrievalEmbeddingModelIdentity modelIdentity, List<String> inputs) {
        if (modelIdentity == null) {
            throw new IllegalArgumentException("modelIdentity must not be null");
        }
        if (inputs == null || inputs.isEmpty() || inputs.stream().anyMatch(input -> input == null || input.isBlank())) {
            throw new IllegalArgumentException("embedding inputs must be non-empty and non-blank");
        }
        OllamaApi.EmbeddingsResponse response = ollamaApi.embed(new OllamaApi.EmbeddingsRequest(
                modelIdentity.requestedModel(),
                List.copyOf(inputs),
                null,
                Map.of(),
                false,
                null));
        if (response == null || response.embeddings() == null) {
            throw new IllegalStateException("Ollama embedding response did not contain vectors");
        }
        List<List<Float>> vectors = new ArrayList<>(response.embeddings().size());
        for (float[] vector : response.embeddings()) {
            if (vector == null) {
                vectors.add(List.of());
                continue;
            }
            List<Float> values = new ArrayList<>(vector.length);
            for (float value : vector) {
                values.add(value);
            }
            vectors.add(List.copyOf(values));
        }
        return new EmbeddingResponse(
                response.model(),
                vectors,
                response.totalDuration(),
                response.loadDuration(),
                response.promptEvalCount());
    }
}
