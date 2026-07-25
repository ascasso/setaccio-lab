package com.setaccio.lab.vision;

import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.service.VisionPromptDefinition;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class VisionMatrixExecutor {

    private final VisionInvocation invocation;
    private final VisionPromptDefinition promptDefinition;
    private final Clock clock;

    VisionMatrixExecutor(
            VisionInvocation invocation,
            VisionPromptDefinition promptDefinition) {
        this(invocation, promptDefinition, Clock.systemUTC());
    }

    VisionMatrixExecutor(
            VisionInvocation invocation,
            VisionPromptDefinition promptDefinition,
            Clock clock) {
        if (invocation == null) {
            throw new IllegalArgumentException("invocation must not be null");
        }
        if (promptDefinition == null) {
            throw new IllegalArgumentException("promptDefinition must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        VisionMatrixProtocol.requirePrompt(promptDefinition);
        this.invocation = invocation;
        this.promptDefinition = promptDefinition;
        this.clock = clock;
    }

    VisionMatrixResult execute(
            LoadedVisionCorpus corpus,
            VisionMatrixRunSettings settings) {
        if (corpus == null || corpus.cases().isEmpty()) {
            throw new IllegalArgumentException("corpus must contain at least one case");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }

        Instant startedAt = clock.instant();
        List<VisionMatrixInput> inputs = corpus.cases().stream()
                .map(loadedCase -> new VisionMatrixInput(
                        loadedCase.metadata().caseId(),
                        loadedCase.metadata().mimeType(),
                        loadedCase.metadata().blake3()))
                .toList();
        List<VisionMatrixRow> rows = new ArrayList<>();
        int sequence = 0;
        for (String model : settings.models()) {
            for (LoadedVisionCorpus.LoadedVisionCase loadedCase : corpus.cases()) {
                VisionCorpusCase metadata = loadedCase.metadata();
                UploadedImage image = new UploadedImage(
                        metadata.caseId(),
                        metadata.mimeType(),
                        fileSize(loadedCase.imagePath()),
                        loadedCase.imagePath());
                for (int repetition = 1; repetition <= settings.repetitions(); repetition++) {
                    VisionInvocationSettings invocationSettings = new VisionInvocationSettings(
                            model,
                            settings.temperature(),
                            settings.seedFor(repetition),
                            settings.maxTokens());
                    VisionInvocationResult result = invokeSafely(image, invocationSettings);
                    rows.add(VisionMatrixRow.from(
                            ++sequence,
                            metadata.caseId(),
                            repetition,
                            metadata.blake3(),
                            result));
                }
            }
        }

        return new VisionMatrixResult(
                VisionMatrixProtocol.SUITE,
                VisionMatrixProtocol.PROVIDER,
                VisionMatrixProtocol.HOST,
                startedAt,
                clock.instant(),
                settings,
                VisionMatrixProtocol.EXECUTION_STRATEGY,
                VisionMatrixProtocol.PULL_MODEL_STRATEGY,
                promptDefinition.id(),
                promptDefinition.version(),
                promptDefinition.sha256(),
                inputs,
                rows);
    }

    private VisionInvocationResult invokeSafely(
            UploadedImage image,
            VisionInvocationSettings settings) {
        try {
            VisionInvocationResult result = invocation.invoke(image, settings);
            if (result != null) {
                return result;
            }
            return failed(settings, image.contentType(), "Vision invocation returned null");
        } catch (Exception e) {
            return failed(settings, image.contentType(), safeMessage(e));
        }
    }

    private VisionInvocationResult failed(
            VisionInvocationSettings settings,
            String mimeType,
            String error) {
        return new VisionInvocationResult(
                settings,
                mimeType,
                promptDefinition.id(),
                promptDefinition.version(),
                promptDefinition.sha256(),
                0,
                null,
                null,
                null,
                List.of(),
                false,
                false,
                VisionErrorCategory.PROVIDER_FAILURE,
                error);
    }

    private static long fileSize(java.nio.file.Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read vision corpus image size", e);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
