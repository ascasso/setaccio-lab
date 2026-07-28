package com.setaccio.lab.service;

import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationResult;
import com.setaccio.lab.model.VisionInvocationSettings;
import com.setaccio.lab.model.VisionStructuralCheck;
import com.setaccio.lab.util.ImageMimeTypes;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

@Component
public final class VisionModelInvoker {

    private static final Logger logger = LoggerFactory.getLogger(VisionModelInvoker.class);

    private final ObjectProvider<OllamaChatModel> ollamaChatModelProvider;
    private final VisionPromptDefinition promptDefinition;
    private final VisionOutputStructureEvaluator structureEvaluator;

    public VisionModelInvoker(
            ObjectProvider<OllamaChatModel> ollamaChatModelProvider,
            VisionPromptDefinition promptDefinition,
            VisionOutputStructureEvaluator structureEvaluator) {
        this.ollamaChatModelProvider = ollamaChatModelProvider;
        this.promptDefinition = promptDefinition;
        this.structureEvaluator = structureEvaluator;
    }

    public VisionInvocationResult invoke(UploadedImage image, VisionInvocationSettings settings) {
        return invoke(image, settings, promptDefinition);
    }

    public VisionInvocationResult invoke(
            UploadedImage image,
            VisionInvocationSettings settings,
            VisionPromptDefinition selectedPromptDefinition) {
        if (selectedPromptDefinition == null) {
            throw new IllegalArgumentException("Vision prompt definition must not be null");
        }
        long started = System.nanoTime();
        String mimeType = image == null ? null : image.contentType();
        if (image == null || image.path() == null || !Files.isRegularFile(image.path())) {
            return failed(settings, selectedPromptDefinition, mimeType, started, VisionErrorCategory.INVALID_INPUT,
                    "Vision input is missing or is not a regular file");
        }

        MimeType detectedMimeType = ImageMimeTypes.detect(image.path());
        mimeType = detectedMimeType.toString();
        try {
            OllamaChatModel ollamaChatModel = ollamaChatModelProvider.getIfAvailable();
            if (ollamaChatModel == null) {
                return failed(settings, selectedPromptDefinition, mimeType, started, VisionErrorCategory.MODEL_UNAVAILABLE,
                        "Ollama chat model is not available");
            }
            Media media = new Media(
                    detectedMimeType,
                    new FileSystemResource(image.path()));
            UserMessage userMessage = UserMessage.builder()
                    .text(selectedPromptDefinition.text())
                    .media(media)
                    .build();
            OllamaChatOptions.Builder options = OllamaChatOptions.builder()
                    .model(settings.model());
            if (settings.temperature() != null) {
                options.temperature(settings.temperature());
            }
            if (settings.seed() != null) {
                options.seed(settings.seed());
            }
            if (settings.maxTokens() != null) {
                options.numPredict(settings.maxTokens());
            }

            ChatResponse response = ollamaChatModel.call(new Prompt(List.of(userMessage), options.build()));
            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null
                    || response.getResult().getOutput().getText().isBlank()) {
                return failed(settings, selectedPromptDefinition, mimeType, started, VisionErrorCategory.EMPTY_RESPONSE,
                        "Ollama returned no vision result");
            }

            String text = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            List<VisionStructuralCheck> checks =
                    structureEvaluator.evaluate(text, selectedPromptDefinition.requiredSections());
            return new VisionInvocationResult(
                    settings,
                    mimeType,
                    selectedPromptDefinition.id(),
                    selectedPromptDefinition.version(),
                    selectedPromptDefinition.sha256(),
                    elapsedMillis(started),
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens(),
                    text,
                    checks,
                    structureEvaluator.complete(checks),
                    true,
                    null,
                    null);
        } catch (Exception e) {
            logger.warn("Vision invocation failed for model={}: {}", settings.model(), e.getClass().getSimpleName());
            return failed(
                    settings,
                    selectedPromptDefinition,
                    mimeType,
                    started,
                    VisionErrorCategory.PROVIDER_FAILURE,
                    safeMessage(e));
        }
    }

    private VisionInvocationResult failed(
            VisionInvocationSettings settings,
            VisionPromptDefinition selectedPromptDefinition,
            String mimeType,
            long started,
            VisionErrorCategory category,
            String error) {
        return new VisionInvocationResult(
                settings,
                mimeType,
                selectedPromptDefinition.id(),
                selectedPromptDefinition.version(),
                selectedPromptDefinition.sha256(),
                elapsedMillis(started),
                null,
                null,
                null,
                List.of(),
                false,
                false,
                category,
                error);
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
