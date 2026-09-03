package com.setaccio.lab.toolcompat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCompatibilityPreflightTest {

    private static final ToolCompatibilityModelIdentity MODEL_IDENTITY =
            new ToolCompatibilityModelIdentity(
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    ToolCompatibilityProtocol.INITIAL_MODEL,
                    "d".repeat(64));

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesOnlyTheExplicitLockedProtocolBeforeAllocation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        AtomicInteger sessions = new AtomicInteger();

        ToolCompatibilityPreflight.Prepared prepared = new ToolCompatibilityPreflight().prepare(
                input(project, "local/evidence/tool-compatibility/2026-08-18-provider-free"),
                (baseUrl, timeout) -> {
                    sessions.incrementAndGet();
                    assertThat(baseUrl).isEqualTo("http://localhost:11434");
                    assertThat(timeout).isEqualTo(Duration.ofMinutes(2));
                    return session();
                });

        assertThat(sessions).hasValue(1);
        assertThat(prepared.settings()).isEqualTo(ToolCompatibilityProtocol.runSettings());
        assertThat(prepared.modelIdentity()).isEqualTo(MODEL_IDENTITY);
        assertThat(prepared.callbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyElementsOf(ToolCompatibilityProtocol.caseSelection().toolNames());
        assertThat(prepared.outputDirectory()).doesNotExist();
    }

    @Test
    void rejectsOptionDriftBeforeCreatingAProviderSession() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("strict-project"));
        AtomicInteger sessions = new AtomicInteger();
        ToolCompatibilityPreflight.SessionFactory factory = (baseUrl, timeout) -> {
            sessions.incrementAndGet();
            return session();
        };

        assertThatThrownBy(() -> new ToolCompatibilityPreflight().prepare(
                new ToolCompatibilityPreflight.Input(
                        project,
                        "http://localhost:11434",
                        "another:model",
                        "512",
                        "PT2M",
                        "local/evidence/tool-compatibility/2026-08-18-wrong-model"),
                factory))
                .isInstanceOf(ToolCompatibilityProtocolIntegrityException.class)
                .hasMessageContaining("locked Phase 1 model");
        assertThatThrownBy(() -> ToolCompatibilityPreflight.parseMaxTokens("511"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512");
        assertThatThrownBy(() -> ToolCompatibilityPreflight.parseTimeout("PT1M"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT2M");
        assertThat(sessions).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com:11434",
            "http://192.168.1.12:11434"
    })
    void rejectsNonLoopbackEndpointsBeforeCreatingAProviderSession(String baseUrl) throws Exception {
        rejectsEndpoint(baseUrl);
    }

    @Test
    void rejectsEndpointUserInfoBeforeCreatingAProviderSession() throws Exception {
        rejectsEndpoint("http://user:password@localhost:11434");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:11434/api",
            "http://localhost:11434?mode=test",
            "http://localhost:11434#fragment"
    })
    void rejectsEndpointPathQueryAndFragmentBeforeCreatingAProviderSession(String baseUrl)
            throws Exception {
        rejectsEndpoint(baseUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "511", "513", "not-an-integer"})
    void rejectsInvalidTokenBounds(String maxTokens) {
        assertThatThrownBy(() -> ToolCompatibilityPreflight.parseMaxTokens(maxTokens))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-PT1S", "PT0S", "PT1M", "PT2M1S", "not-a-duration"})
    void rejectsInvalidTimeoutBounds(String timeout) {
        assertThatThrownBy(() -> ToolCompatibilityPreflight.parseTimeout(timeout))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allocatesOneFreshDatedDirectChildAndNeverReusesIt() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("allocation-project"));
        Path output = ToolCompatibilityPreflight.resolveNewOutputDirectory(
                project,
                "local/evidence/tool-compatibility/2026-08-18-fresh");

        Path allocated = ToolCompatibilityPreflight.allocate(output);

        assertThat(allocated).isDirectory();
        assertThatThrownBy(() -> ToolCompatibilityPreflight.allocate(output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> ToolCompatibilityPreflight.resolveNewOutputDirectory(
                project,
                "build/not-tool-compatibility/2026-08-18-outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directly under");
        assertThatThrownBy(() -> ToolCompatibilityPreflight.resolveNewOutputDirectory(
                project,
                "local/evidence/tool-compatibility/not-dated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void rejectsSymbolicLinksInTheOutputPath() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("symlink-project"));
        Path root = Files.createDirectories(project.resolve("local/evidence/tool-compatibility"));
        Path target = Files.createDirectory(temporaryDirectory.resolve("symlink-target"));
        Files.createSymbolicLink(root.resolve("2026-08-18-linked"), target);

        assertThatThrownBy(() -> ToolCompatibilityPreflight.resolveNewOutputDirectory(
                project,
                "local/evidence/tool-compatibility/2026-08-18-linked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic links");

        Path parentLinkedProject = Files.createDirectory(
                temporaryDirectory.resolve("parent-linked-project"));
        Path redirectedLocal = Files.createDirectory(
                temporaryDirectory.resolve("redirected-local"));
        Files.createSymbolicLink(parentLinkedProject.resolve("local"), redirectedLocal);
        assertThatThrownBy(() -> ToolCompatibilityPreflight.resolveNewOutputDirectory(
                parentLinkedProject,
                "local/evidence/tool-compatibility/2026-08-18-parent-linked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic links");
    }

    private static ToolCompatibilityPreflight.Input input(Path project, String output) {
        return new ToolCompatibilityPreflight.Input(
                project,
                "http://localhost:11434",
                ToolCompatibilityProtocol.INITIAL_MODEL,
                "512",
                "PT2M",
                output);
    }

    private void rejectsEndpoint(String baseUrl) throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve(
                "endpoint-project-" + Integer.toUnsignedString(baseUrl.hashCode())));
        assertThatThrownBy(() -> new ToolCompatibilityPreflight().prepare(
                new ToolCompatibilityPreflight.Input(
                        project,
                        baseUrl,
                        ToolCompatibilityProtocol.INITIAL_MODEL,
                        "512",
                        "PT2M",
                        "local/evidence/tool-compatibility/2026-08-18-invalid-endpoint"),
                (ignoredBaseUrl, ignoredTimeout) -> {
                    throw new AssertionError("Invalid endpoints must fail before session creation");
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    private static ToolCompatibilityPreflight.Session session() {
        return new ToolCompatibilityPreflight.Session() {
            @Override
            public ToolCompatibilityModelIdentity requireInstalled(String requestedModel) {
                assertThat(requestedModel).isEqualTo(ToolCompatibilityProtocol.INITIAL_MODEL);
                return MODEL_IDENTITY;
            }

            @Override
            public ToolCompatibilityControlledOllamaModel controlledModel(int seed) {
                throw new AssertionError("Preflight must not execute a model");
            }
        };
    }
}
