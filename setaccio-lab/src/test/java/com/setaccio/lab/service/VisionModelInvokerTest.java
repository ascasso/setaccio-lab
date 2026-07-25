package com.setaccio.lab.service;

import com.setaccio.lab.model.UploadedImage;
import com.setaccio.lab.model.VisionErrorCategory;
import com.setaccio.lab.model.VisionInvocationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisionModelInvokerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesTheTrackedPromptWithExplicitOptionsAndCapturesMetadata() throws Exception {
        UploadedImage image = image();
        VisionPromptDefinition promptDefinition = new VisionPromptDefinition();
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        when(ollamaChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOf(OllamaChatOptions.class);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo("vision:model");
            assertThat(options.getTemperature()).isEqualTo(0.2);
            assertThat(options.getSeed()).isEqualTo(43);
            assertThat(options.getNumPredict()).isEqualTo(512);
            assertThat(prompt.getInstructions())
                    .singleElement()
                    .isInstanceOfSatisfying(UserMessage.class, message -> {
                        assertThat(message.getText()).isEqualTo(promptDefinition.text());
                        assertThat(message.getMedia()).singleElement().satisfies(media ->
                                assertThat(media.getMimeType().toString()).isEqualTo("image/jpeg"));
                    });
            return response(completeOutput(promptDefinition), 31, 17);
        });
        VisionModelInvoker invoker = invoker(ollamaChatModel, promptDefinition);

        var result = invoker.invoke(
                image,
                new VisionInvocationSettings("vision:model", 0.2, 43, 512));

        assertThat(result.success()).isTrue();
        assertThat(result.settings().model()).isEqualTo("vision:model");
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.promptId()).isEqualTo(promptDefinition.id());
        assertThat(result.promptVersion()).isEqualTo(promptDefinition.version());
        assertThat(result.promptSha256()).isEqualTo(promptDefinition.sha256());
        assertThat(result.tokensIn()).isEqualTo(31);
        assertThat(result.tokensOut()).isEqualTo(17);
        assertThat(result.structuralChecks()).hasSize(7).allMatch(check -> check.present());
        assertThat(result.structureComplete()).isTrue();
        assertThat(result.errorCategory()).isNull();
    }

    @Test
    void keepsInvocationSuccessSeparateFromStructuralCompletion() throws Exception {
        VisionPromptDefinition promptDefinition = new VisionPromptDefinition();
        OllamaChatModel ollamaChatModel = mock(OllamaChatModel.class);
        ChatResponse response = response("A valid model response without the required headings.", null, null);
        when(ollamaChatModel.call(any(Prompt.class)))
                .thenReturn(response);

        var result = invoker(ollamaChatModel, promptDefinition)
                .invoke(image(), VisionInvocationSettings.modelDefaults("vision:model"));

        assertThat(result.success()).isTrue();
        assertThat(result.structureComplete()).isFalse();
        assertThat(result.structuralChecks()).hasSize(7).noneMatch(check -> check.present());
        assertThat(result.tokensIn()).isNull();
        assertThat(result.tokensOut()).isNull();
    }

    @Test
    void classifiesUnavailableEmptyAndProviderFailures() throws Exception {
        VisionPromptDefinition promptDefinition = new VisionPromptDefinition();
        VisionInvocationSettings settings = VisionInvocationSettings.modelDefaults("vision:model");

        var invalidInput = invoker(null, promptDefinition).invoke(
                new UploadedImage(
                        "missing.jpg",
                        "image/jpeg",
                        0,
                        temporaryDirectory.resolve("missing.jpg")),
                settings);
        var unavailable = invoker(null, promptDefinition).invoke(image(), settings);

        OllamaChatModel emptyModel = mock(OllamaChatModel.class);
        when(emptyModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        var empty = invoker(emptyModel, promptDefinition).invoke(image(), settings);

        OllamaChatModel failingModel = mock(OllamaChatModel.class);
        when(failingModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("fixture failure"));
        var failed = invoker(failingModel, promptDefinition).invoke(image(), settings);

        assertThat(invalidInput.errorCategory()).isEqualTo(VisionErrorCategory.INVALID_INPUT);
        assertThat(unavailable.errorCategory()).isEqualTo(VisionErrorCategory.MODEL_UNAVAILABLE);
        assertThat(empty.errorCategory()).isEqualTo(VisionErrorCategory.EMPTY_RESPONSE);
        assertThat(failed.errorCategory()).isEqualTo(VisionErrorCategory.PROVIDER_FAILURE);
        assertThat(failed.error()).isEqualTo("fixture failure");
    }

    private VisionModelInvoker invoker(
            OllamaChatModel ollamaChatModel,
            VisionPromptDefinition promptDefinition) {
        return new VisionModelInvoker(
                singletonProvider(ollamaChatModel),
                promptDefinition,
                new VisionOutputStructureEvaluator());
    }

    private UploadedImage image() throws Exception {
        Path path = temporaryDirectory.resolve("fixture.jpg");
        Files.write(path, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        return new UploadedImage("fixture.jpg", "image/jpeg", Files.size(path), path);
    }

    private String completeOutput(VisionPromptDefinition promptDefinition) {
        return promptDefinition.requiredSections().stream()
                .map(section -> "## " + section + "\nUnknown")
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
    }

    private ChatResponse response(String text, Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            ChatResponse response = mock(ChatResponse.class);
            when(response.getResult()).thenReturn(new Generation(new AssistantMessage(text)));
            when(response.getMetadata()).thenReturn(null);
            return response;
        }
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(promptTokens, completionTokens))
                        .build());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OllamaChatModel> singletonProvider(OllamaChatModel model) {
        ObjectProvider<OllamaChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return provider;
    }
}
