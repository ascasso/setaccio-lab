package com.setaccio.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Runs an explicit live local model matrix and writes non-overwriting evidence")
public abstract class ToolCompatibilityMatrixTask extends DefaultTask {

    private final ExecOperations execOperations;

    @Inject
    public ToolCompatibilityMatrixTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the explicit local-only, no-pull 16-row tool compatibility matrix; never part of the default lifecycle.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Input
    @Optional
    public abstract Property<String> getOllamaBaseUrl();

    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama HTTP(S) URL; never persisted.")
    public void setOllamaBaseUrlOption(String value) {
        getOllamaBaseUrl().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getModel();

    @Option(option = "model", description = "Required locked, already-installed LFM2.5 model tag; no default or pull.")
    public void setModelOption(String value) {
        getModel().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getMaxTokens();

    @Option(option = "max-tokens", description = "Required locked per-provider-turn output limit: 512.")
    public void setMaxTokensOption(String value) {
        getMaxTokens().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getRowTimeout();

    @Option(option = "timeout", description = "Required locked whole-row ISO-8601 deadline: PT2M.")
    public void setRowTimeoutOption(String value) {
        getRowTimeout().set(value);
    }

    @Input
    @Optional
    public abstract Property<String> getOutputDir();

    @Option(option = "output-dir", description = "Required new dated directory directly under local/evidence/tool-compatibility/.")
    public void setOutputDirOption(String value) {
        getOutputDir().set(value);
    }

    @TaskAction
    public void runMatrix() {
        requireOption(getOllamaBaseUrl(), "--ollama-base-url");
        requireOption(getModel(), "--model");
        requireOption(getMaxTokens(), "--max-tokens");
        requireOption(getRowTimeout(), "--timeout");
        requireOption(getOutputDir(), "--output-dir");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getWorkingDirectory().get().getAsFile());
            spec.args(List.of(
                    "--ollama-base-url", getOllamaBaseUrl().get(),
                    "--model", getModel().get(),
                    "--max-tokens", getMaxTokens().get(),
                    "--timeout", getRowTimeout().get(),
                    "--output-dir", getOutputDir().get()));
        });
    }

    private void requireOption(Property<String> property, String option) {
        String value = property.getOrNull();
        if (value == null || value.isBlank()) {
            throw new GradleException("toolCompatibilityMatrix requires " + option + "=<value>");
        }
    }
}
