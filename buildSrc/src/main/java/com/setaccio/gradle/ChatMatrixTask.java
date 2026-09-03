package com.setaccio.gradle;

import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

public abstract class ChatMatrixTask extends DefaultTask {

    private final ExecOperations execOperations;
    private String ollamaBaseUrl;
    private String model;
    private String maxTokens;
    private String timeout;
    private String outputDir;

    @Inject
    public ChatMatrixTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
        setGroup("verification");
        setDescription("Runs the explicit local-only, no-pull six-row Ollama chat matrix; never part of the default lifecycle.");
    }

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getJavaExecutable();

    @Input
    @Optional
    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    @Option(option = "ollama-base-url", description = "Required explicit loopback Ollama HTTP(S) URL; never persisted.")
    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    @Input
    @Optional
    public String getModel() {
        return model;
    }

    @Option(option = "model", description = "Required already-installed Ollama model tag; no default or pull.")
    public void setModel(String model) {
        this.model = model;
    }

    @Input
    @Optional
    public String getMaxTokens() {
        return maxTokens;
    }

    @Option(option = "max-tokens", description = "Required positive output limit from 1 through 32768.")
    public void setMaxTokens(String maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Input
    @Optional
    public String getRequestTimeout() {
        return timeout;
    }

    @Option(option = "timeout", description = "Required positive ISO-8601 duration up to PT10M, for example PT30S.")
    public void setRequestTimeout(String timeout) {
        this.timeout = timeout;
    }

    @Input
    @Optional
    public String getOutputDir() {
        return outputDir;
    }

    @Option(option = "output-dir", description = "Required new dated directory directly under local/evidence/chat-matrix/.")
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    public void runMatrix() {
        requireOption(ollamaBaseUrl, "--ollama-base-url");
        requireOption(model, "--model");
        requireOption(maxTokens, "--max-tokens");
        requireOption(timeout, "--timeout");
        requireOption(outputDir, "--output-dir");
        execOperations.javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set(getMainClass());
            spec.setExecutable(getJavaExecutable().get());
            spec.setWorkingDir(getProject().getProjectDir());
            spec.args(List.of(
                    "--ollama-base-url", ollamaBaseUrl.trim(),
                    "--model", model.trim(),
                    "--max-tokens", maxTokens.trim(),
                    "--timeout", timeout.trim(),
                    "--output-dir", outputDir.trim()));
        });
    }

    private void requireOption(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new GradleException("chatMatrix requires " + option + "=<value>");
        }
    }
}
