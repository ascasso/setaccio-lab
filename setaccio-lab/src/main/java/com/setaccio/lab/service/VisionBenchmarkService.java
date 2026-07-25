package com.setaccio.lab.service;

import com.setaccio.lab.model.BenchmarkResult;
import com.setaccio.lab.model.RunRow;
import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionInvocationSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class VisionBenchmarkService {

    private static final String LOCAL_HOST = "local";

    private final VisionModelInvoker visionModelInvoker;
    private final FileHashingService fileHashingService;
    private final LabResultWriter labResultWriter;
    private final CacheManager cacheManager;
    private final ExecutorService executorService;
    private final String ollamaBaseUrl;

    public VisionBenchmarkService(
            VisionModelInvoker visionModelInvoker,
            FileHashingService fileHashingService,
            LabResultWriter labResultWriter,
            CacheManager cacheManager,
            @Qualifier("visionBenchmarkExecutor") ExecutorService executorService,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.visionModelInvoker = visionModelInvoker;
        this.fileHashingService = fileHashingService;
        this.labResultWriter = labResultWriter;
        this.cacheManager = cacheManager;
        this.executorService = executorService;
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public BenchmarkResult run(List<UploadedImage> images, List<String> models) {
        return runConfigured(
                images,
                models.stream().map(VisionInvocationSettings::modelDefaults).toList());
    }

    public BenchmarkResult runConfigured(
            List<UploadedImage> images,
            List<VisionInvocationSettings> invocationSettings) {
        Instant startedAt = Instant.now();
        List<CompletableFuture<RunRow>> futures = new ArrayList<>();
        for (UploadedImage image : images) {
            String hash = fileHashingService.hash(image.path());
            for (VisionInvocationSettings settings : invocationSettings) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> RunRow.from(
                                image.originalFilename(),
                                hash,
                                visionModelInvoker.invoke(image, settings)),
                        executorService));
            }
        }

        List<RunRow> runs = futures.stream().map(CompletableFuture::join).toList();
        BenchmarkResult result = new BenchmarkResult(
                "vision",
                startedAt,
                Instant.now(),
                LOCAL_HOST,
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
}
