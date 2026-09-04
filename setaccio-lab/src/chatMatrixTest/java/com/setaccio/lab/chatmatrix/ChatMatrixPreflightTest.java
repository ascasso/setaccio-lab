package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import com.setaccio.lab.chat.ChatModelUnavailableException;
import com.setaccio.lab.chat.OllamaChatModelIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.ollama.api.OllamaApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMatrixPreflightTest {

    @TempDir
    Path projectDirectory;

    @Test
    void validatesTheLockedContractAndInstalledDigestBeforeAllocatingOutput() {
        AtomicInteger sessions = new AtomicInteger();
        ChatMatrixPreflight.Prepared prepared = new ChatMatrixPreflight().prepare(
                validInput(),
                () -> ChatMatrixTestFixtures.CATALOG,
                (baseUrl, timeout) -> {
                    sessions.incrementAndGet();
                    assertThat(baseUrl).isEqualTo("http://127.0.0.1:11434");
                    assertThat(timeout).isEqualTo(Duration.ofSeconds(30));
                    return installedSession(ChatMatrixTestFixtures.MODEL_IDENTITY);
                });

        assertThat(sessions).hasValue(1);
        assertThat(prepared.settings()).isEqualTo(ChatMatrixTestFixtures.SETTINGS);
        assertThat(prepared.modelIdentity()).isEqualTo(ChatMatrixTestFixtures.MODEL_IDENTITY);
        assertThat(prepared.outputDirectory()).isEqualTo(outputDirectory());
        assertThat(Files.exists(outputDirectory())).isFalse();

        Path allocated = ChatMatrixPreflight.allocate(prepared);
        assertThat(allocated).isEqualTo(outputDirectory());
        assertThat(Files.isDirectory(allocated)).isTrue();
        assertThatThrownBy(() -> ChatMatrixPreflight.allocate(prepared))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsUnsafeOptionsBeforeOpeningASessionOrAllocatingOutput() {
        AtomicInteger sessions = new AtomicInteger();
        ChatMatrixPreflight.SessionFactory factory = (baseUrl, timeout) -> {
            sessions.incrementAndGet();
            return installedSession(ChatMatrixTestFixtures.MODEL_IDENTITY);
        };

        assertThatThrownBy(() -> new ChatMatrixPreflight().prepare(
                input("https://example.com", "128", "PT30S", outputArgument()),
                () -> ChatMatrixTestFixtures.CATALOG,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
        assertThatThrownBy(() -> new ChatMatrixPreflight().prepare(
                input("http://localhost:11434", "0", "PT30S", outputArgument()),
                () -> ChatMatrixTestFixtures.CATALOG,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 32768");
        assertThatThrownBy(() -> new ChatMatrixPreflight().prepare(
                input("http://localhost:11434", "128", "PT11M", outputArgument()),
                () -> ChatMatrixTestFixtures.CATALOG,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no greater than PT10M");
        assertThatThrownBy(() -> new ChatMatrixPreflight().prepare(
                input("http://localhost:11434", "128", "PT30S", "build/elsewhere/run"),
                () -> ChatMatrixTestFixtures.CATALOG,
                factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directly under local/evidence/chat-matrix");

        assertThat(sessions).hasValue(0);
        assertThat(Files.exists(projectDirectory.resolve("build"))).isFalse();
    }

    @Test
    void resolvesOnlyTheExplicitInstalledTagWithAFullDigest() {
        OllamaApi.Model installed = new OllamaApi.Model(
                "model-a:latest", null, null, 0L, "a".repeat(64), null);

        assertThat(ChatMatrixModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(installed)),
                "model-a"))
                .isEqualTo(ChatMatrixTestFixtures.MODEL_IDENTITY);
        assertThatThrownBy(() -> ChatMatrixModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of()),
                "model-a"))
                .isInstanceOf(ChatModelUnavailableException.class)
                .hasMessageContaining("not installed");
        assertThatThrownBy(() -> ChatMatrixModelInventory.requireInstalled(
                new OllamaApi.ListModelResponse(List.of(new OllamaApi.Model(
                        "model-a:latest", null, null, 0L, "short", null))),
                "model-a"))
                .isInstanceOf(ChatModelUnavailableException.class)
                .hasMessageContaining("complete immutable digest");
    }

    @Test
    void rejectsAResolvedModelMismatchBeforeAllocatingOutput() {
        OllamaChatModelIdentity mismatch = new OllamaChatModelIdentity(
                "ollama",
                "model-a",
                "different:latest",
                "b".repeat(64));

        assertThatThrownBy(() -> new ChatMatrixPreflight().prepare(
                validInput(),
                () -> ChatMatrixTestFixtures.CATALOG,
                (baseUrl, timeout) -> installedSession(mismatch)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match the requested tag");
        assertThat(Files.exists(outputDirectory())).isFalse();
    }

    private ChatMatrixPreflight.Input validInput() {
        return input("http://127.0.0.1:11434", "128", "PT30S", outputArgument());
    }

    private ChatMatrixPreflight.Input input(
            String baseUrl,
            String maxTokens,
            String timeout,
            String output
    ) {
        return new ChatMatrixPreflight.Input(
                projectDirectory,
                baseUrl,
                "model-a",
                maxTokens,
                timeout,
                output);
    }

    private String outputArgument() {
        return "local/evidence/chat-matrix/2026-08-04-test";
    }

    private Path outputDirectory() {
        return projectDirectory.resolve(outputArgument()).toAbsolutePath().normalize();
    }

    private static ChatMatrixPreflight.Session installedSession(OllamaChatModelIdentity identity) {
        return new ChatMatrixPreflight.Session() {
            @Override
            public OllamaChatModelIdentity requireInstalled(String requestedModel) {
                return identity;
            }

            @Override
            public ChatInvocationOutcome invoke(
                    ChatPromptCase prompt,
                    OllamaChatModelIdentity modelIdentity,
                    ChatGenerationSettings settings
            ) {
                throw new AssertionError("Preflight must not invoke a chat model");
            }
        };
    }
}
