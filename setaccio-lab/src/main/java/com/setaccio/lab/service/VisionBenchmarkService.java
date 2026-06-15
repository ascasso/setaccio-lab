package com.setaccio.lab.service;

import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.RunRow;
import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.util.ImageMimeTypes;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

@Service
public class VisionBenchmarkService {

    private static final Logger logger = LoggerFactory.getLogger(VisionBenchmarkService.class);

    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final FileHashingService fileHashingService;
    private final LabResultWriter labResultWriter;
    private final CacheManager cacheManager;
    private final ExecutorService executorService;
    private final String ollamaBaseUrl;

    public VisionBenchmarkService(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            FileHashingService fileHashingService,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("visionBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.fileHashingService = fileHashingService;
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public BenchmarkResult run(List<UploadedImage> images, List<String> models) {
        Instant startedAt = Instant.now();
        List<CompletableFuture<RunRow>> futures = new ArrayList<>();
        for (UploadedImage image : images) {
            String hash = fileHashingService.hash(image.path());
            for (String model : models) {
                futures.add(CompletableFuture.supplyAsync(() -> runOne(image, model, hash), executorService));
            }
        }

        List<RunRow> runs = futures.stream().map(CompletableFuture::join).toList();
        BenchmarkResult result = new BenchmarkResult(
                "vision",
                startedAt,
                Instant.now(),
                hostName(),
                ollamaBaseUrl,
                runs
        );

        labResultWriter.write(result);
        Cache cache = cacheManager.getCache("vision-benchmark-results");
        if (cache != null) {
            cache.put(result.startedAt().toString(), result);
        }
        return result;
    }

    private RunRow runOne(UploadedImage image, String model, String hash) {
        long started = System.nanoTime();
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return failed(model, image, hash, started, "Ollama chat model is not available");
            }

            Media media = new Media(ImageMimeTypes.detect(image.path()), new FileSystemResource(image.path()));
            UserMessage userMessage = UserMessage.builder()
                    .text(buildClassificationPrompt())
                    .media(media)
                    .build();
            OllamaChatOptions options = OllamaChatOptions.builder()
                    .model(model)
                    .build();

            ChatResponse response = ollamaChatModel.call(new Prompt(List.of(userMessage), options));
            String text = response.getResult().getOutput().getText();
            return RunRow.ok(model, image.originalFilename(), hash, elapsedMillis(started), null, null, text);
        } catch (Exception e) {
            logger.warn("Vision benchmark failed for model={} input={}: {}", model, image.originalFilename(), e.getMessage());
            return failed(model, image, hash, started, e.getMessage());
        }
    }

    private RunRow failed(String model, UploadedImage image, String hash, long started, String error) {
        return RunRow.fail(model, image.originalFilename(), hash, elapsedMillis(started), error);
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String buildClassificationPrompt() {
        return """
                Analyze this image for an AI-assisted file evaluation benchmark.

                Include:
                1. Primary category
                2. Subject matter
                3. Scene description
                4. Visual elements
                5. Context or likely setting
                6. Quality assessment
                7. Potential file-management keywords

                Use concise structured text with clear headings.
                """;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
