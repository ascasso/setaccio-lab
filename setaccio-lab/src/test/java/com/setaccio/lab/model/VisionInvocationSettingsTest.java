package com.setaccio.lab.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VisionInvocationSettingsTest {

    @Test
    void acceptsExplicitGenerationSettingsAndTrimsTheModel() {
        VisionInvocationSettings settings = new VisionInvocationSettings(" model ", 0.0, 42, 1024);

        assertThat(settings.model()).isEqualTo("model");
        assertThat(settings.temperature()).isZero();
        assertThat(settings.seed()).isEqualTo(42);
        assertThat(settings.maxTokens()).isEqualTo(1024);
    }

    @Test
    void rejectsInvalidGenerationSettings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VisionInvocationSettings(" ", null, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VisionInvocationSettings("model", 2.1, null, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VisionInvocationSettings("model", null, -1, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VisionInvocationSettings("model", null, null, 0));
    }
}
