package com.setaccio.lab.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

class OllamaReasoningOptionsTest {

    @Test
    void mapsAnExplicitEnabledPolicyOntoOllamasThinkOption() {
        OllamaChatOptions options = OllamaReasoningOptions
                .apply(OllamaChatOptions.builder().model("m:1"), ChatReasoningPolicy.ENABLED)
                .build();

        assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.ENABLED);
        assertThat(OllamaReasoningOptions.support(ChatReasoningPolicy.ENABLED))
                .isEqualTo(ChatReasoningSupport.APPLIED);
    }

    @Test
    void mapsAnExplicitDisabledPolicyOntoOllamasThinkOption() {
        OllamaChatOptions options = OllamaReasoningOptions
                .apply(OllamaChatOptions.builder().model("m:1"), ChatReasoningPolicy.DISABLED)
                .build();

        assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
        assertThat(OllamaReasoningOptions.support(ChatReasoningPolicy.DISABLED))
                .isEqualTo(ChatReasoningSupport.APPLIED);
    }

    @Test
    void sendsNothingForProviderDefaultSoTheModelsOwnDefaultApplies() {
        OllamaChatOptions options = OllamaReasoningOptions
                .apply(OllamaChatOptions.builder().model("m:1"), ChatReasoningPolicy.PROVIDER_DEFAULT)
                .build();

        assertThat(options.getThinkOption()).isNull();
        assertThat(OllamaReasoningOptions.support(ChatReasoningPolicy.PROVIDER_DEFAULT))
                .isEqualTo(ChatReasoningSupport.NOT_REQUESTED);
    }

    @Test
    void appliesAPolicyToAnAlreadyBuiltOptionsObjectWithoutLosingOtherSettings() {
        OllamaChatOptions base = OllamaChatOptions.builder()
                .model("m:1")
                .temperature(0.0)
                .seed(42)
                .numPredict(64)
                .build();

        OllamaChatOptions withPolicy = OllamaReasoningOptions.withPolicy(base, ChatReasoningPolicy.DISABLED);

        assertThat(base.getThinkOption()).isNull();
        assertThat(withPolicy.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
        assertThat(withPolicy.getModel()).isEqualTo("m:1");
        assertThat(withPolicy.getTemperature()).isEqualTo(0.0);
        assertThat(withPolicy.getSeed()).isEqualTo(42);
        assertThat(withPolicy.getNumPredict()).isEqualTo(64);
    }
}
